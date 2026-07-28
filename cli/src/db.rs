use sqlx::SqlitePool;

pub async fn migrate(pool: &SqlitePool) {
    sqlx::query("PRAGMA foreign_keys = ON;").execute(pool).await.ok();
    sqlx::query("CREATE TABLE IF NOT EXISTS config (
            device_id TEXT PRIMARY KEY,
            kdf_salt TEXT NOT NULL,
            kdf_iter INTEGER NOT NULL DEFAULT 600000,
            magic_text TEXT NOT NULL,
            device_name TEXT NOT NULL,
            enc_priv_key TEXT NOT NULL,
            pub_key TEXT NOT NULL,
            password_hint TEXT NOT NULL DEFAULT ''
        );").execute(pool).await.ok();
    sqlx::query("CREATE TABLE IF NOT EXISTS vaults (
            id TEXT PRIMARY KEY,
            title TEXT NOT NULL,
            username TEXT NOT NULL DEFAULT '',
            enc_password TEXT NOT NULL,
            enc_notes TEXT,
            url TEXT,
            version INTEGER NOT NULL DEFAULT 1,
            is_deleted INTEGER NOT NULL DEFAULT 0,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL
        );").execute(pool).await.ok();
    sqlx::query("CREATE TABLE IF NOT EXISTS tags (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL UNIQUE
        );").execute(pool).await.ok();
    sqlx::query("CREATE TABLE IF NOT EXISTS vault_tag_join (
            vault_id TEXT NOT NULL,
            tag_id INTEGER NOT NULL,
            PRIMARY KEY (vault_id, tag_id),
            FOREIGN KEY (vault_id) REFERENCES vaults(id) ON DELETE CASCADE,
            FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE CASCADE
        );").execute(pool).await.ok();
}