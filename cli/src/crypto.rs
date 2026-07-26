use aes_gcm::{Aes256Gcm, KeyInit, Nonce, aead::Aead};
use base64::{Engine, engine::general_purpose::STANDARD as B64};
use rand::RngCore;
use rsa::{Oaep, pkcs1::EncodeRsaPublicKey, pkcs8::EncodePrivateKey};
use sha2::Sha512;
use hmac::Hmac;
use pbkdf2::pbkdf2;
use crate::db::Database;
use thiserror::Error;
use std::sync::{Mutex, OnceLock};
use rsa::{RsaPrivateKey, RsaPublicKey};

const MAGIC_TEXT: &str = "PWD_MASTER_VERIFY_OK";
const SALT_LEN: usize = 32;
const NONCE_LEN: usize = 12;
const PBKDF2_ITERATIONS: u32 = 600_000;

static mut SESSION_KEY: OnceLock<Mutex<Option<[u8; 32]>>> = OnceLock::new();

#[derive(Error, Debug)]
pub enum CryptoError {
    #[error("密钥未加载，请先解锁")]
    KeyNotLoaded,
    #[error("Base64 解码失败: {0}")]
    Base64Decode(#[from] base64::DecodeError),
    #[error("加密/解密失败: {0}")]
    AesGcm(#[from] aes_gcm::Error),
    #[error("UTF-8 转换失败: {0}")]
    Utf8(#[from] std::string::FromUtf8Error),
    #[error("密文格式错误（缺少分隔符）")]
    InvalidCipherFormat,
    #[error("密钥长度无效")]
    InvalidKeyLength(#[from] sha2::digest::InvalidLength),
    #[error("RSA 错误: {0}")]
    Rsa(#[from] rsa::Error),
    #[error("PKCS8 错误: {0}")]
    Pkcs8(#[from] rsa::pkcs8::Error),
    #[error("PKCS1 错误: {0}")]
    Pkcs1(#[from] rsa::pkcs1::Error),
}

fn set_key(key: [u8; 32]) {
    unsafe {
        let lock = SESSION_KEY.get_or_init(|| Mutex::new(None));
        let mut guard = lock.lock().unwrap();
        *guard = Some(key);
    }
}

fn get_key() -> Result<[u8; 32], CryptoError> {
    unsafe {
        let lock = SESSION_KEY.get().ok_or(CryptoError::KeyNotLoaded)?;
        let guard = lock.lock().unwrap();
        guard.ok_or(CryptoError::KeyNotLoaded)
    }
}

pub fn is_unlocked() -> bool {
    unsafe {
        return SESSION_KEY.get().and_then(|lock| lock.lock().ok())
        .map(|g|g.is_some()).unwrap_or(false);
    }
}

pub fn encrypt_field(value: &str) -> Result<String, CryptoError> {
    let key = get_key()?;
    let cipher = Aes256Gcm::new_from_slice(&key)?;
    let mut nonce = [0u8; NONCE_LEN];
    rand::thread_rng().fill_bytes(&mut nonce);
    let ct = cipher.encrypt(Nonce::from_slice(&nonce), value.as_bytes())?;
    Ok(format!("{}:{}", B64.encode(nonce), B64.encode(ct)))
}

pub fn decrypt_field(encoded: &str) -> Result<String, CryptoError> {
    let key = get_key()?;
    let (iv_s, ct_s) = encoded.split_once(':').ok_or(CryptoError::InvalidCipherFormat)?;
    let iv = B64.decode(iv_s)?;
    let ct = B64.decode(ct_s)?;
    let cipher = Aes256Gcm::new_from_slice(&key)?;
    let plain = cipher.decrypt(Nonce::from_slice(&iv), ct.as_ref())?;
    Ok(String::from_utf8(plain)?)
}

pub fn verify_and_load(db: &mut Database, password: &str) -> Result<bool, CryptoError> {
    let info = db.get_key_info().ok_or_else(|| CryptoError::KeyNotLoaded)?;
    let salt = B64.decode(&info.salt)?;
    let mut key = [0u8; 32];
    pbkdf2::<Hmac<Sha512>>(password.as_bytes(), &salt, PBKDF2_ITERATIONS, &mut key)
        .map_err(|_| CryptoError::AesGcm(aes_gcm::Error))?;  // 简化处理

    let cipher = Aes256Gcm::new_from_slice(&key)?;
    let magic_iv = B64.decode(&info.magic_text_iv)?;
    let magic_ct = B64.decode(&info.magic_text_cipher)?;
    match cipher.decrypt(Nonce::from_slice(&magic_iv), magic_ct.as_ref()) {
        Ok(plain) if plain == MAGIC_TEXT.as_bytes() => {
            set_key(key);
            Ok(true)
        }
        _ => Ok(false),
    }
}

pub fn initialize_vault(
    db: &mut Database,
    password: &str,
    hint: &str,
) -> Result<(), CryptoError> {
    // 生成随机盐 防彩虹表
    let mut salt = [0u8; SALT_LEN];
    rand::thread_rng().fill_bytes(&mut salt);
    // 派生 AES 密钥
    let mut kmaster = [0u8; 32];
    pbkdf2::<Hmac<Sha512>>(password.as_bytes(), &salt, PBKDF2_ITERATIONS, &mut kmaster)
        .map_err(|_| CryptoError::AesGcm(aes_gcm::Error))?;

    // 加密验证文本 Magic Text
    let cipher = Aes256Gcm::new_from_slice(&kmaster)?;
    let mut nonce = [0u8; NONCE_LEN];
    rand::thread_rng().fill_bytes(&mut nonce);
    let ct = cipher.encrypt(Nonce::from_slice(&nonce), MAGIC_TEXT.as_bytes())?;

    // 生成设备密钥对
    let mut rng = rand::thread_rng();
    let priv_key = RsaPrivateKey::new(&mut rng, 3072)?;
    let pub_key = RsaPublicKey::from(&priv_key);
    let pub_key_b64 = B64.encode(pub_key.to_pkcs1_der()?.as_ref());

    // 使用 K_master 加密 RSA 私钥
    let der = priv_key.to_pkcs8_der()?;
    let encrypted_priv_key = encrypt_with_kmaster(&kmaster, der.as_bytes())?;

    // 生成 Data Key
    let mut data_key = [0u8; 32];
    rand::thread_rng().fill_bytes(&mut data_key);

    // 本机公钥加密 Data Key
    let mut rng = rand::thread_rng();
    let padding = Oaep::new::<Sha512>(); // 使用 SHA‑512 作为 OAEP 哈希
    let encrypted_data_key = pub_key.encrypt(&mut rng, padding, &data_key)?;

    // 生成设备 ID
    let device_id = uuid::Uuid::new_v4().to_string();

    db.save_key_info(
        &B64.encode(salt),
        &B64.encode(nonce),
        &B64.encode(ct),
        hint,
        &B64.encode(encrypted_priv_key)
    );
    let devname = hostname::get()
        .map(|h| h.to_string_lossy().into_owned())
        .unwrap_or_else(|_| "Unknown Device".to_string());
    db.save_device_info(
        &device_id,
        &devname,   // 可让用户输入设备名
        &pub_key_b64,
        &B64.encode(encrypted_data_key),
        true,                   // is_current_device
        chrono::Utc::now().timestamp_millis() as i64,
    );
    // 将派生密钥加载到内存
    set_key(data_key);
    Ok(())
}

fn encrypt_with_kmaster(kmaster: &[u8; 32], plaintext: &[u8]) -> Result<String, CryptoError> {
    let cipher = Aes256Gcm::new_from_slice(kmaster)?;
    let mut nonce = [0u8; NONCE_LEN];
    rand::thread_rng().fill_bytes(&mut nonce);
    let ct = cipher.encrypt(Nonce::from_slice(&nonce), plaintext)?;
    Ok(format!("{}:{}", B64.encode(nonce), B64.encode(ct)))
}
