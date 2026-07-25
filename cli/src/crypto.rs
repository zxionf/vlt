use aes_gcm::{Aes256Gcm, KeyInit, Nonce, aead::Aead};
use base64::{Engine, engine::general_purpose::STANDARD as B64};
use rand::RngCore;
use sha2::Sha512;
use hmac::Hmac;
use pbkdf2::pbkdf2;
use crate::db::Database;

const MAGIC_TEXT: &str = "PWD_MASTER_VERIFY_OK";
const SALT_LEN: usize = 32;
const NONCE_LEN: usize = 12;
const PBKDF2_ITERATIONS: u32 = 600_000;

static mut AES_KEY: Option<[u8; 32]> = None;

fn set_key(key: [u8; 32]) { unsafe { AES_KEY = Some(key); } }

pub fn encrypt_field(value: &str) -> String {
    let key = unsafe { AES_KEY.as_ref().expect("未解锁") };
    let cipher = Aes256Gcm::new_from_slice(key).unwrap();
    let mut nonce = [0u8; NONCE_LEN]; rand::thread_rng().fill_bytes(&mut nonce);
    let ct = cipher.encrypt(Nonce::from_slice(&nonce), value.as_bytes()).unwrap();
    format!("{}:{}", B64.encode(nonce), B64.encode(ct))
}

pub fn decrypt_field(encoded: &str) -> String {
    let key = unsafe { AES_KEY.as_ref().expect("未解锁") };
    let (iv_s, ct_s) = encoded.split_once(':').unwrap();
    let iv = B64.decode(iv_s).unwrap();
    let ct = B64.decode(ct_s).unwrap();
    let cipher = Aes256Gcm::new_from_slice(key).unwrap();
    let plain = cipher.decrypt(Nonce::from_slice(&iv), ct.as_ref()).unwrap();
    String::from_utf8(plain).unwrap()
}

pub fn verify_and_load(db: &mut Database, password: &str) -> bool {
    let info = db.get_key_info().unwrap();
    let salt = B64.decode(&info.salt).unwrap();
    let mut key = [0u8; 32];
    pbkdf2::<Hmac<Sha512>>(password.as_bytes(), &salt, PBKDF2_ITERATIONS, &mut key).unwrap();
    // 验证 magic text
    let cipher = Aes256Gcm::new_from_slice(&key).unwrap();
    let magic_iv = B64.decode(&info.magic_iv).unwrap();
    let magic_ct = B64.decode(&info.magic_ct).unwrap();
    match cipher.decrypt(Nonce::from_slice(&magic_iv), magic_ct.as_ref()) {
        Ok(plain) if plain == MAGIC_TEXT.as_bytes() => { set_key(key); true },
        _ => false,
    }
}

pub fn initialize_vault(db: &mut Database, password: &str, hint: &str) {
    let mut salt = [0u8; SALT_LEN]; rand::thread_rng().fill_bytes(&mut salt);
    let mut key = [0u8; 32];
    pbkdf2::<Hmac<Sha512>>(password.as_bytes(), &salt, PBKDF2_ITERATIONS, &mut key).unwrap();
    // 加密 magic text
    let cipher = Aes256Gcm::new_from_slice(&key).unwrap();
    let mut nonce = [0u8; NONCE_LEN]; rand::thread_rng().fill_bytes(&mut nonce);
    let ct = cipher.encrypt(Nonce::from_slice(&nonce), MAGIC_TEXT.as_bytes()).unwrap();
    db.save_key_info(&B64.encode(salt), &B64.encode(nonce), &B64.encode(ct), hint);
    set_key(key);
}
