use sqlx::SqlitePool;

pub async fn migrate(pool: &SqlitePool) {
    sqlx::query("PRAGMA foreign_keys = ON;").execute(pool).await.ok();

    sqlx::query(
        "CREATE TABLE IF NOT EXISTS devices (
            device_id TEXT PRIMARY KEY,
            device_name TEXT NOT NULL,
            public_key TEXT NOT NULL,
            signature TEXT NOT NULL DEFAULT ''
        )"
    ).execute(pool).await.ok();

    sqlx::query(
        "CREATE TABLE IF NOT EXISTS sync_records (
            record_id TEXT PRIMARY KEY,
            source_device_id TEXT NOT NULL,
            encrypted_blob TEXT NOT NULL,
            blob_nonce TEXT NOT NULL DEFAULT '',
            sync_version INTEGER NOT NULL DEFAULT 1,
            operation TEXT NOT NULL DEFAULT 'create' CHECK(operation IN ('create','update','delete')),
            client_updated_at INTEGER NOT NULL,
            server_updated_at INTEGER NOT NULL
        )"
    ).execute(pool).await.ok();

    log::info!("Database migration completed");
}