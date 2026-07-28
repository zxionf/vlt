use aes_gcm::{Aes256Gcm, KeyInit, Nonce, aead::Aead};
use base64::{Engine, engine::general_purpose::STANDARD as B64};
use rand::RngCore;
use rsa::{Oaep, pkcs1::{EncodeRsaPublicKey, DecodeRsaPublicKey}, pkcs8::{EncodePrivateKey, DecodePrivateKey}};
use sha2::{Sha512, Digest};
use hmac::Hmac;
use pbkdf2::pbkdf2;
use sqlx::SqlitePool;
use thiserror::Error;
use std::sync::{Mutex, OnceLock};
use rsa::{RsaPrivateKey, RsaPublicKey, pkcs1v15::SigningKey, signature::{RandomizedSigner, SignatureEncoding}};

use crate::query;

const MAGIC_TEXT: &str = "PWD_MASTER_VERIFY_OK";
const SALT_LEN: usize = 32;
const NONCE_LEN: usize = 12;
const PBKDF2_ITERATIONS: u32 = 600_000;

static SESSION_KEY: OnceLock<Mutex<Option<[u8; 32]>>> = OnceLock::new();
static SESSION_KEK: OnceLock<Mutex<Option<[u8; 32]>>> = OnceLock::new();
static SESSION_PRIV_KEY: OnceLock<Mutex<Option<RsaPrivateKey>>> = OnceLock::new();

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
    #[error("签名错误: {0}")]
    Signature(#[from] rsa::signature::Error),
    #[error("数据库未初始化，请先运行 init")]
    NotInitialized,
    #[error("数据库错误: {0}")]
    Database(#[from] sqlx::Error),
}

fn set_data_key(key: [u8; 32]) {
    let lock = SESSION_KEY.get_or_init(|| Mutex::new(None));
    let mut guard = lock.lock().unwrap();
    *guard = Some(key);
}

fn get_data_key() -> Result<[u8; 32], CryptoError> {
    let lock = SESSION_KEY.get().ok_or(CryptoError::KeyNotLoaded)?;
    let guard = lock.lock().unwrap();
    guard.ok_or(CryptoError::KeyNotLoaded)
}

fn set_kek(key: [u8; 32]) {
    let lock = SESSION_KEK.get_or_init(|| Mutex::new(None));
    let mut guard = lock.lock().unwrap();
    *guard = Some(key);
}

fn get_kek() -> Result<[u8; 32], CryptoError> {
    let lock = SESSION_KEK.get().ok_or(CryptoError::KeyNotLoaded)?;
    let guard = lock.lock().unwrap();
    guard.ok_or(CryptoError::KeyNotLoaded)
}

fn set_priv_key(key: RsaPrivateKey) {
    let lock = SESSION_PRIV_KEY.get_or_init(|| Mutex::new(None));
    let mut guard = lock.lock().unwrap();
    *guard = Some(key);
}

fn get_priv_key() -> Result<RsaPrivateKey, CryptoError> {
    let lock = SESSION_PRIV_KEY.get().ok_or(CryptoError::KeyNotLoaded)?;
    let guard = lock.lock().unwrap();
    guard.clone().ok_or(CryptoError::KeyNotLoaded)
}

pub fn encrypt_field(value: &str) -> Result<String, CryptoError> {
    let key = get_data_key()?;
    let cipher = Aes256Gcm::new_from_slice(&key)?;
    let mut nonce = [0u8; NONCE_LEN];
    rand::thread_rng().fill_bytes(&mut nonce);
    let ct = cipher.encrypt(Nonce::from_slice(&nonce), value.as_bytes())?;
    Ok(format!("{}:{}", B64.encode(nonce), B64.encode(ct)))
}

pub fn decrypt_field(encoded: &str) -> Result<String, CryptoError> {
    let key = get_data_key()?;
    let (iv_s, ct_s) = encoded.split_once(':').ok_or(CryptoError::InvalidCipherFormat)?;
    let iv = B64.decode(iv_s)?;
    let ct = B64.decode(ct_s)?;
    let cipher = Aes256Gcm::new_from_slice(&key)?;
    let plain = cipher.decrypt(Nonce::from_slice(&iv), ct.as_ref())?;
    Ok(String::from_utf8(plain)?)
}

pub fn get_current_data_key() -> Result<[u8; 32], CryptoError> {
    get_data_key()
}

pub fn decrypt_and_store_data_key(encrypted_base64: &str) -> Result<String, CryptoError> {
    let enc = B64.decode(encrypted_base64)?;
    let priv_key = get_priv_key()?;
    let padding = Oaep::new::<Sha512>();
    let dk = priv_key.decrypt(padding, &enc)?;
    if dk.len() != 32 { return Err(CryptoError::InvalidCipherFormat); }
    let kek = get_kek()?;
    let mut nonce = [0u8; NONCE_LEN];
    rand::thread_rng().fill_bytes(&mut nonce);
    let cipher = Aes256Gcm::new_from_slice(&kek)?;
    let ct = cipher.encrypt(Nonce::from_slice(&nonce), dk.as_ref())?;
    Ok(format!("{}:{}", B64.encode(nonce), B64.encode(ct)))
}

pub fn encrypt_with_public_key(pub_key_b64: &str, data: &[u8]) -> Result<String, CryptoError> {
    let der = B64.decode(pub_key_b64)?;
    let pub_key = RsaPublicKey::from_pkcs1_der(&der)?;
    let mut rng = rand::thread_rng();
    let padding = Oaep::new::<Sha512>();
    let encrypted = pub_key.encrypt(&mut rng, padding, data)?;
    Ok(B64.encode(encrypted))
}

pub fn sign_device(device_id: &str, public_key: &str) -> Result<String, CryptoError> {
    let priv_key = get_priv_key()?;
    let message = format!("{},{}", device_id, public_key);
    let mut hasher = Sha512::new();
    Digest::update(&mut hasher, message.as_bytes());
    let digest = hasher.finalize();
    let signing_key = SigningKey::<Sha512>::new_unprefixed(priv_key);
    let mut rng = rand::thread_rng();
    let signature = signing_key.sign_with_rng(&mut rng, &digest);
    Ok(B64.encode(signature.to_bytes()))
}

pub async fn verify_and_load(pool: &SqlitePool, password: &str) -> Result<bool, CryptoError> {
    let info = query::get_master_auth(pool).await.ok_or_else(|| CryptoError::NotInitialized)?;
    let salt = B64.decode(&info.salt)?;
    let mut kmaster = [0u8; 32];
    pbkdf2::<Hmac<Sha512>>(password.as_bytes(), &salt, PBKDF2_ITERATIONS, &mut kmaster)
        .map_err(|_| CryptoError::AesGcm(aes_gcm::Error))?;

    let cipher = Aes256Gcm::new_from_slice(&kmaster)?;
    let auth_iv = B64.decode(&info.auth_iv)?;
    let auth_ct = B64.decode(&info.auth_cipher)?;
    let auth_ok = match cipher.decrypt(Nonce::from_slice(&auth_iv), auth_ct.as_ref()) {
        Ok(plain) => plain == MAGIC_TEXT.as_bytes(),
        _ => false,
    };
    if !auth_ok { return Ok(false); }

    let device = query::get_device(pool).await.ok_or(CryptoError::NotInitialized)?;

    if let Some(enc_priv_key) = &device.encrypted_private_key {
        let (iv_s, ct_s) = enc_priv_key.split_once(':')
            .ok_or(CryptoError::InvalidCipherFormat)?;
        let priv_iv = B64.decode(iv_s)?;
        let priv_ct = B64.decode(ct_s)?;
        let priv_key_der = cipher.decrypt(Nonce::from_slice(&priv_iv), priv_ct.as_ref())?;
        let priv_key = RsaPrivateKey::from_pkcs8_der(&priv_key_der)?;
        set_priv_key(priv_key);
    }

    let (iv_s, ct_s) = device.encrypted_data_key.split_once(':')
        .ok_or(CryptoError::InvalidCipherFormat)?;
    let dk_iv = B64.decode(iv_s)?;
    let dk_ct = B64.decode(ct_s)?;
    let dk = cipher.decrypt(Nonce::from_slice(&dk_iv), dk_ct.as_ref())?;
    if dk.len() != 32 { return Err(CryptoError::InvalidCipherFormat); }
    let mut data_key = [0u8; 32];
    data_key.copy_from_slice(&dk);

    set_data_key(data_key);
    set_kek(kmaster);
    Ok(true)
}

pub async fn initialize_vault(
    pool: &SqlitePool,
    password: &str,
    hint: &str,
) -> Result<(), CryptoError> {
    let mut salt = [0u8; SALT_LEN];
    rand::thread_rng().fill_bytes(&mut salt);
    let mut kmaster = [0u8; 32];
    pbkdf2::<Hmac<Sha512>>(password.as_bytes(), &salt, PBKDF2_ITERATIONS, &mut kmaster)
        .map_err(|_| CryptoError::AesGcm(aes_gcm::Error))?;

    let cipher = Aes256Gcm::new_from_slice(&kmaster)?;
    let mut nonce = [0u8; NONCE_LEN];
    rand::thread_rng().fill_bytes(&mut nonce);
    let ct = cipher.encrypt(Nonce::from_slice(&nonce), MAGIC_TEXT.as_bytes())?;

    let mut rng = rand::thread_rng();
    let priv_key = RsaPrivateKey::new(&mut rng, 3072)?;
    let pub_key = RsaPublicKey::from(&priv_key);
    let pub_key_b64 = B64.encode(pub_key.to_pkcs1_der()?.as_ref());

    let der = priv_key.to_pkcs8_der()?;
    let encrypted_priv_key = encrypt_with_kmaster(&kmaster, der.as_bytes())?;

    let mut data_key = [0u8; 32];
    rand::thread_rng().fill_bytes(&mut data_key);
    let encrypted_data_key = encrypt_with_kmaster(&kmaster, &data_key)?;

    let device_id = uuid::Uuid::new_v4().to_string();
    let now = chrono::Utc::now().timestamp_millis();

    query::save_master_auth(
        pool,
        &B64.encode(salt),
        &B64.encode(nonce),
        &B64.encode(ct),
        hint,
        now,
    ).await?;
    let devname = hostname::get()
        .map(|h| h.to_string_lossy().into_owned())
        .unwrap_or_else(|_| "Unknown Device".to_string());
    query::save_device(
        pool,
        &device_id,
        &devname,
        &pub_key_b64,
        &encrypted_priv_key,
        &encrypted_data_key,
        true,
        now,
    ).await?;
    Ok(())
}

fn encrypt_with_kmaster(kmaster: &[u8; 32], plaintext: &[u8]) -> Result<String, CryptoError> {
    let cipher = Aes256Gcm::new_from_slice(kmaster)?;
    let mut nonce = [0u8; NONCE_LEN];
    rand::thread_rng().fill_bytes(&mut nonce);
    let ct = cipher.encrypt(Nonce::from_slice(&nonce), plaintext)?;
    Ok(format!("{}:{}", B64.encode(nonce), B64.encode(ct)))
}
