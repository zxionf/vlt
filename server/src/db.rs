use sqlx::SqlitePool;

pub async fn migrate(pool: &SqlitePool) {
    sqlx::query(
        "CREATE TABLE IF NOT EXISTS devices (
            device_id TEXT PRIMARY KEY,
            device_name TEXT NOT NULL,
            public_key TEXT NOT NULL,
            encrypted_data_key TEXT NOT NULL,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP
        )"
    ).execute(pool).await.ok();

    sqlx::query(
        "CREATE TABLE IF NOT EXISTS pending_authorizations (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            from_device_id TEXT NOT NULL,
            to_device_id TEXT NOT NULL,
            public_key TEXT NOT NULL,
            status TEXT DEFAULT 'pending',
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP
        )"
    ).execute(pool).await.ok();

    sqlx::query(
        "CREATE TABLE IF NOT EXISTS password_records (
            id TEXT PRIMARY KEY,
            encrypted_password TEXT NOT NULL,
            encrypted_notes TEXT,
            url TEXT,
            created_device_id TEXT NOT NULL,
            last_modified_device_id TEXT NOT NULL,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL,
            sync_version INTEGER DEFAULT 1,
            is_deleted INTEGER DEFAULT 0
        )"
    ).execute(pool).await.ok();

    log::info!("Database migration completed");
}
