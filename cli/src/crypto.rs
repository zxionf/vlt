use aes_gcm::{Aes256Gcm, KeyInit, Nonce, aead::Aead};
use base64::{Engine, engine::general_purpose::STANDARD as B64};
use ed25519_dalek::{SigningKey, Signer};
use hmac::Hmac;
use pbkdf2::pbkdf2;
use rand::RngCore;
use sha2::Sha512;
use sqlx::SqlitePool;
use std::sync::{Mutex, OnceLock};
use thiserror::Error;

use crate::query;

const MAGIC_TEXT: &[u8; 12] = b"VLT_OK______";
const SALT_LEN: usize = 32;
const NONCE_LEN: usize = 12;
const DEFAULT_KDF_ITER: u32 = 200_000;

static SESSION_MASTER: OnceLock<Mutex<Option<[u8; 32]>>> = OnceLock::new();
static SESSION_SIGN: OnceLock<Mutex<Option<SigningKey>>> = OnceLock::new();

#[derive(Error, Debug)]
pub enum CryptoError {
    #[error("密钥未加载")]
    KeyNotLoaded,
    #[error("Base64 解码失败: {0}")]
    Base64Decode(#[from] base64::DecodeError),
    #[error("AES加解密失败: {0}")]
    AesGcm(#[from] aes_gcm::Error),
    #[error("UTF-8 转换失败: {0}")]
    Utf8(#[from] std::string::FromUtf8Error),
    #[error("密文格式错误")]
    InvalidCipherFormat,
    #[error("密钥长度无效")]
    InvalidKeyLength,
    #[error("ed25519 错误: {0}")]
    Ed25519(#[from] ed25519_dalek::SignatureError),
    #[error("数据库未初始化")]
    NotInitialized,
    #[error("数据库错误: {0}")]
    Database(#[from] sqlx::Error),
}

fn set_master(key: [u8; 32]) {
    let mut g = SESSION_MASTER.get_or_init(|| Mutex::new(None)).lock().unwrap();
    *g = Some(key);
}

fn get_master() -> Result<[u8; 32], CryptoError> {
    SESSION_MASTER.get()
        .and_then(|m| *m.lock().unwrap())
        .ok_or(CryptoError::KeyNotLoaded)
}

fn set_sign(key: SigningKey) {
    let mut g = SESSION_SIGN.get_or_init(|| Mutex::new(None)).lock().unwrap();
    *g = Some(key);
}

fn get_sign() -> Result<SigningKey, CryptoError> {
    SESSION_SIGN.get()
        .and_then(|m| m.lock().unwrap().clone())
        .ok_or(CryptoError::KeyNotLoaded)
}

pub fn encrypt_field(value: &str) -> Result<String, CryptoError> {
    let key = get_master()?;
    let cipher = Aes256Gcm::new_from_slice(&key).map_err(|_| CryptoError::InvalidKeyLength)?;
    let mut nonce = [0u8; NONCE_LEN];
    rand::thread_rng().fill_bytes(&mut nonce);
    let ct = cipher.encrypt(Nonce::from_slice(&nonce), value.as_bytes())?;
    Ok(format!("{}:{}", B64.encode(nonce), B64.encode(ct)))
}

pub fn decrypt_field(encoded: &str) -> Result<String, CryptoError> {
    let key = get_master()?;
    let (iv_s, ct_s) = encoded.split_once(':').ok_or(CryptoError::InvalidCipherFormat)?;
    let iv = B64.decode(iv_s)?;
    let ct = B64.decode(ct_s)?;
    let cipher = Aes256Gcm::new_from_slice(&key).map_err(|_| CryptoError::InvalidKeyLength)?;
    let plain = cipher.decrypt(Nonce::from_slice(&iv), ct.as_ref())?;
    Ok(String::from_utf8(plain)?)
}

fn enc(key: &[u8; 32], data: &[u8]) -> Result<String, CryptoError> {
    let cipher = Aes256Gcm::new_from_slice(key).map_err(|_| CryptoError::InvalidKeyLength)?;
    let mut nonce = [0u8; NONCE_LEN];
    rand::thread_rng().fill_bytes(&mut nonce);
    let ct = cipher.encrypt(Nonce::from_slice(&nonce), data)?;
    Ok(format!("{}:{}", B64.encode(nonce), B64.encode(ct)))
}

fn dec(key: &[u8; 32], encoded: &str) -> Result<Vec<u8>, CryptoError> {
    let (iv_s, ct_s) = encoded.split_once(':').ok_or(CryptoError::InvalidCipherFormat)?;
    let iv = B64.decode(iv_s)?;
    let ct = B64.decode(ct_s)?;
    let cipher = Aes256Gcm::new_from_slice(key).map_err(|_| CryptoError::InvalidKeyLength)?;
    let plain = cipher.decrypt(Nonce::from_slice(&iv), ct.as_ref())?;
    Ok(plain)
}

pub async fn init_vault(pool: &SqlitePool, password: &str, hint: &str) -> Result<(), CryptoError> {
    let mut salt = [0u8; SALT_LEN];
    rand::thread_rng().fill_bytes(&mut salt);
    let salt_b64 = B64.encode(&salt);

    let mut master = [0u8; 32];
    pbkdf2::<Hmac<Sha512>>(password.as_bytes(), &salt, DEFAULT_KDF_ITER, &mut master)
        .map_err(|_| CryptoError::AesGcm(aes_gcm::Error))?;

    let magic = enc(&master, MAGIC_TEXT)?;

    let mut csprng = rand::thread_rng();
    let sign_key = SigningKey::generate(&mut csprng);
    let verif_key = sign_key.verifying_key();
    let pub_key_b64 = B64.encode(&verif_key.to_bytes());
    let enc_priv = enc(&master, &sign_key.to_bytes())?;

    let device_id = uuid::Uuid::new_v4().to_string();
    let devname = hostname::get()
        .map(|h| h.to_string_lossy().into_owned())
        .unwrap_or_else(|_| "Unknown Device".to_string());

    query::save_config(
        pool,
        &device_id,
        &salt_b64,
        DEFAULT_KDF_ITER as i32,
        &magic,
        &devname,
        &enc_priv,
        &pub_key_b64,
        hint,
    ).await?;
    Ok(())
}

pub async fn verify_and_load(pool: &SqlitePool, password: &str) -> Result<bool, CryptoError> {
    let cfg = query::get_config(pool).await.ok_or(CryptoError::NotInitialized)?;
    let master = {
        let salt = B64.decode(&cfg.kdf_salt)?;
        let mut key = [0u8; 32];
        pbkdf2::<Hmac<Sha512>>(password.as_bytes(), &salt, cfg.kdf_iter as u32, &mut key)
            .map_err(|_| CryptoError::AesGcm(aes_gcm::Error))?;
        key
    };

    let magic = dec(&master, &cfg.magic_text)?;
    if magic != MAGIC_TEXT { return Ok(false); }

    let priv_bytes = dec(&master, &cfg.enc_priv_key)?;
    let arr: [u8; 32] = priv_bytes[..32].try_into()
        .map_err(|_| CryptoError::InvalidCipherFormat)?;
    let sign_key = SigningKey::from_bytes(&arr);

    set_master(master);
    set_sign(sign_key);
    Ok(true)
}

pub fn sign_msg(data: &[u8]) -> Result<String, CryptoError> {
    let sk = get_sign()?;
    let sig = sk.sign(data);
    Ok(B64.encode(sig.to_bytes()))
}