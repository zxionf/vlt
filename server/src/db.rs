use sqlx::SqlitePool;

pub async fn migrate(pool: &SqlitePool) {
    sqlx::query("PRAGMA foreign_keys = ON;").execute(pool).await.ok();

    sqlx::query(
        "CREATE TABLE IF NOT EXISTS registered_devices (
            device_id TEXT PRIMARY KEY,
            public_key TEXT NOT NULL,
            device_name TEXT NOT NULL,
            signature TEXT,
            registered_at INTEGER NOT NULL,
            is_authorized INTEGER NOT NULL DEFAULT 0
        )"
    ).execute(pool).await.ok();

    sqlx::query(
        "CREATE TABLE IF NOT EXISTS encrypted_data_keys (
            device_id TEXT PRIMARY KEY,
            encrypted_data_key TEXT NOT NULL,
            created_at TEXT DEFAULT (datetime('now')),
            updated_at TEXT DEFAULT (datetime('now')),
            FOREIGN KEY (device_id) REFERENCES registered_devices(device_id)
        )"
    ).execute(pool).await.ok();

    sqlx::query(
        "CREATE TABLE IF NOT EXISTS sync_records (
            record_id TEXT PRIMARY KEY,
            device_id TEXT NOT NULL,
            encrypted_blob TEXT NOT NULL,
            sync_version INTEGER NOT NULL DEFAULT 1,
            operation TEXT NOT NULL CHECK(operation IN ('create','update','delete')),
            client_modified_at INTEGER NOT NULL,
            server_modified_at TEXT DEFAULT (datetime('now')),
            FOREIGN KEY (device_id) REFERENCES registered_devices(device_id)
        )"
    ).execute(pool).await.ok();

    log::info!("Database migration completed");
}
