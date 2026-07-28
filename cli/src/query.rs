use crate::models::{Device, MasterAuth, Password};
use sqlx::SqlitePool;

pub async fn is_initialized(pool: &SqlitePool) -> bool {
    sqlx::query_scalar::<_, i64>("SELECT COUNT(*) FROM master_auth")
        .fetch_one(pool)
        .await
        .map(|count| count > 0)
        .unwrap_or(false)
}

pub async fn get_master_auth(pool: &SqlitePool) -> Option<MasterAuth> {
    match sqlx::query_as::<_, MasterAuth>("SELECT * FROM master_auth")
        .fetch_optional(pool)
        .await
    {
        Ok(Some(ma)) => Some(ma),
        Ok(None) => None,
        Err(e) => {
            eprintln!("获取 master_auth 失败: {}", e);
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

#[allow(dead_code)]
pub async fn get_device_by_id(pool: &SqlitePool, device_id: &str) -> Option<Device> {
    match sqlx::query_as::<_, Device>("SELECT * FROM devices WHERE device_id = ?")
        .bind(device_id)
        .fetch_optional(pool)
        .await
    {
        Ok(Some(device)) => Some(device),
        Ok(None) => None,
        Err(e) => {
            eprintln!("获取设备 {} 信息失败: {}", device_id, e);
            None
        }
    }
}

pub async fn save_master_auth(
    pool: &SqlitePool,
    salt: &str,
    auth_iv: &str,
    auth_cipher: &str,
    hint: &str,
    created_at: i64,
) -> Result<(), sqlx::Error> {
    sqlx::query(
        "INSERT OR REPLACE INTO master_auth (id, salt, auth_iv, auth_cipher, password_hint, created_at)
         VALUES (1, ?1, ?2, ?3, ?4, ?5)"
    ).bind(salt).bind(auth_iv).bind(auth_cipher).bind(hint).bind(created_at)
    .execute(pool)
    .await?;
    Ok(())
}

pub async fn save_device(
    pool: &SqlitePool,
    device_id: &str,
    device_name: &str,
    public_key: &str,
    encrypted_private_key: &str,
    encrypted_data_key: &str,
    is_current_device: bool,
    created_at: i64,
) -> Result<(), sqlx::Error> {
    sqlx::query(
        "INSERT OR REPLACE INTO devices (device_id, device_name, public_key, encrypted_private_key, encrypted_data_key, is_current_device, created_at)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)"
    ).bind(device_id).bind(device_name).bind(public_key).bind(encrypted_private_key).bind(encrypted_data_key).bind(is_current_device as i32).bind(created_at)
    .execute(pool)
    .await?;
    Ok(())
}

pub async fn update_device_data_key(pool: &SqlitePool, device_id: &str, encrypted_data_key: &str) -> Result<(), sqlx::Error> {
    sqlx::query(
        "UPDATE devices SET encrypted_data_key = ?1 WHERE device_id = ?2"
    ).bind(encrypted_data_key).bind(device_id).execute(pool).await?;
    Ok(())
}

pub async fn insert_passwd(
    pool: &SqlitePool,
    id: &str,
    title: &str,
    username: &str,
    encrypted_password: &str,
    encrypted_notes: Option<&str>,
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

pub async fn update_passwd(
    pool: &SqlitePool,
    id: &str,
    title: &str,
    username: &str,
    encrypted_password: &str,
    encrypted_notes: Option<&str>,
    url: &str,
    last_modified_device_id: &str,
) -> Result<bool, sqlx::Error> {
    let updated_at = chrono::Utc::now().timestamp_millis();
    let result = sqlx::query(
        "UPDATE passwords SET title=?1, username=?2, encrypted_password=?3, encrypted_notes=?4, url=?5, last_modified_device_id=?6, updated_at=?7, sync_version=sync_version+1 WHERE id=?8"
    )
        .bind(title)
        .bind(username)
        .bind(encrypted_password)
        .bind(encrypted_notes)
        .bind(url)
        .bind(last_modified_device_id)
        .bind(updated_at)
        .bind(id)
        .execute(pool)
        .await?;
    Ok(result.rows_affected() > 0)
}

pub async fn delete_passwd(pool: &SqlitePool, id: &str) -> bool {
    match sqlx::query("DELETE FROM passwords WHERE id=?1")
        .bind(id)
        .execute(pool)
        .await
    {
        Ok(result) => result.rows_affected() > 0,
        Err(e) => {
            eprintln!("删除失败: {}", e);
            false
        }
    }
}
