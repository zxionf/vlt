use crate::models::{Config, Vault};
use sqlx::SqlitePool;

pub async fn is_initialized(pool: &SqlitePool) -> bool {
    sqlx::query_scalar::<_, i64>("SELECT COUNT(*) FROM config")
        .fetch_one(pool)
        .await
        .map(|c| c > 0)
        .unwrap_or(false)
}

pub async fn get_config(pool: &SqlitePool) -> Option<Config> {
    sqlx::query_as::<_, Config>("SELECT * FROM config")
        .fetch_optional(pool)
        .await
        .ok()
        .flatten()
}

pub async fn save_config(
    pool: &SqlitePool,
    device_id: &str,
    kdf_salt: &str,
    kdf_iter: i32,
    magic_text: &str,
    device_name: &str,
    enc_priv_key: &str,
    pub_key: &str,
    password_hint: &str,
) -> Result<(), sqlx::Error> {
    sqlx::query(
        "INSERT OR REPLACE INTO config (device_id, kdf_salt, kdf_iter, magic_text, device_name, enc_priv_key, pub_key, password_hint)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)"
    ).bind(device_id).bind(kdf_salt).bind(kdf_iter).bind(magic_text)
     .bind(device_name).bind(enc_priv_key).bind(pub_key).bind(password_hint)
     .execute(pool).await?;
    Ok(())
}

pub async fn insert_vault(
    pool: &SqlitePool,
    id: &str,
    title: &str,
    username: &str,
    enc_password: &str,
    enc_notes: Option<&str>,
    url: &str,
    created_at: i64,
    updated_at: i64,
) -> Result<(), sqlx::Error> {
    sqlx::query(
        "INSERT OR REPLACE INTO vaults (id, title, username, enc_password, enc_notes, url, version, is_deleted, created_at, updated_at)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, 1, 0, ?7, ?8)"
    ).bind(id).bind(title).bind(username).bind(enc_password).bind(enc_notes)
     .bind(url).bind(created_at).bind(updated_at)
     .execute(pool).await?;
    Ok(())
}

pub async fn update_vault(
    pool: &SqlitePool,
    id: &str,
    title: &str,
    username: &str,
    enc_password: &str,
    enc_notes: Option<&str>,
    url: &str,
) -> Result<bool, sqlx::Error> {
    let updated_at = chrono::Utc::now().timestamp_millis();
    let result = sqlx::query(
        "UPDATE vaults SET title=?1, username=?2, enc_password=?3, enc_notes=?4, url=?5, updated_at=?6, version=version+1 WHERE id=?7"
    ).bind(title).bind(username).bind(enc_password).bind(enc_notes)
     .bind(url).bind(updated_at).bind(id)
     .execute(pool).await?;
    Ok(result.rows_affected() > 0)
}

pub async fn list_vaults(pool: &SqlitePool) -> Vec<Vault> {
    sqlx::query_as::<_, Vault>("SELECT * FROM vaults WHERE is_deleted = 0 ORDER BY updated_at DESC")
        .fetch_all(pool)
        .await
        .unwrap_or_default()
}

pub async fn find_vault_by_prefix(pool: &SqlitePool, prefix: &str) -> Vec<Vault> {
    sqlx::query_as::<_, Vault>("SELECT * FROM vaults WHERE id LIKE ? || '%' AND is_deleted = 0")
        .bind(prefix)
        .fetch_all(pool)
        .await
        .unwrap_or_default()
}

pub async fn delete_vault(pool: &SqlitePool, id: &str) -> bool {
    sqlx::query("UPDATE vaults SET is_deleted = 1 WHERE id = ?1")
        .bind(id)
        .execute(pool)
        .await
        .map(|r| r.rows_affected() > 0)
        .unwrap_or(false)
}