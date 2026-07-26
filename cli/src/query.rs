use crate::models::{Device, KeyPair, Password};
use sqlx::SqlitePool;

pub async fn is_initialized(pool: &SqlitePool) -> bool {
    sqlx::query_scalar::<_, i64>("SELECT COUNT(*) FROM key_pair")
        .fetch_one(pool)
        .await
        .map(|count| count > 0)
        .unwrap_or(false)
}

pub async fn get_key_pair(pool: &SqlitePool) -> Option<KeyPair> {
    match sqlx::query_as::<_, KeyPair>("SELECT * FROM key_pair")
        .fetch_optional(pool)
        .await
    {
        Ok(Some(kp)) => Some(kp),
        Ok(None) => None,
        Err(e) => {
            eprintln!("获取 key_pair 失败: {}", e);
            None
        }
    }
}

pub async fn get_device(pool: &SqlitePool) -> Option<Device> {
    match sqlx::query_as::<_, Device>("SELECT * FROM devices WHERE is_current_device = 1")
        .fetch_optional(pool)
        .await
    {
        Ok(Some(device)) => Some(device),
        Ok(None) => None,
        Err(e) => {
            eprintln!("获取设备信息失败: {}", e);
            None
        }
    }
}

pub async fn save_key_pair(
    pool: &SqlitePool,
    salt: &str,
    magic_iv: &str,
    magic_ct: &str,
    hint: &str,
    encrypted_priv_key: &str,
) -> Result<(), sqlx::Error> {
    sqlx::query(
        "INSERT OR REPLACE INTO key_pair (id, salt, magic_text_iv, magic_text_cipher, password_hint, encrypted_private_key)
         VALUES (1, ?1, ?2, ?3, ?4, ?5)"
    ).bind(salt).bind(magic_iv).bind(magic_ct).bind(hint).bind(encrypted_priv_key)
    .execute(pool)
    .await?;
    Ok(())
}

pub async fn save_device(
    pool: &SqlitePool,
    device_id: &str,
    device_name: &str,
    public_key: &str,
    encrypted_data_key: &str,
    is_current_device: bool,
    created_at: i64,
) -> Result<(), sqlx::Error> {
    sqlx::query(
        "INSERT OR REPLACE INTO devices (device_id, device_name, public_key, encrypted_data_key, is_current_device, created_at)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6)"
    ).bind(device_id).bind(device_name).bind(public_key).bind(encrypted_data_key).bind(is_current_device as i32).bind(created_at)
    .execute(pool)
    .await?;
    Ok(())
}

pub async fn insert_passwd(
    pool: &SqlitePool,
    id: &str,
    title: &str,
    username: &str,
    encrypted_password: &str,
    encrypted_notes: &str,
    url: &str,
    created_device_id: &str,
    last_modified_device_id: &str,
    created_at: i64,
    updated_at: i64,
    sync_version: i32,
    is_deleted: bool,
) -> Result<(), sqlx::Error> {
    sqlx::query(
        "INSERT OR REPLACE INTO passwords (id, title, username, encrypted_password, encrypted_notes, url, created_device_id, last_modified_device_id, created_at, updated_at, sync_version, is_deleted)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12)")
         .bind(id)
         .bind(title)
         .bind(username)
         .bind(encrypted_password)
         .bind(encrypted_notes)
         .bind(url)
         .bind(created_device_id)
         .bind(last_modified_device_id)
         .bind(created_at)
         .bind(updated_at)
         .bind(sync_version)
         .bind(is_deleted)
         .execute(pool)
         .await?;
    Ok(())
}


pub async fn get_all_passwords(pool: &SqlitePool) -> Vec<Password> {
    match sqlx::query_as::<_, Password>("SELECT * FROM passwords")
        .fetch_all(pool)
        .await 
    {
        Ok(rows) => rows,
        Err(e) => {
            eprintln!("Failed to fetch passwords: {}", e);
            Vec::new()   // 返回空向量，调用者无法知道错误
        }
    }
}

pub async fn find_by_prefix(pool: &SqlitePool, prefix: &str) -> Vec<Password> {
    let query = r#"
        SELECT *
        FROM passwords
        WHERE id LIKE ? || '%'
    "#;

    match sqlx::query_as::<_, Password>(query)
        .bind(prefix)
        .fetch_all(pool)
        .await
    {
        Ok(rows) => rows,
        Err(e) => {
            eprintln!("Failed to find passwords by prefix '{}': {}", prefix, e);
            Vec::new()
        }
    }
}
