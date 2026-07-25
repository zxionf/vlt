use rusqlite::{Connection, params};
use std::path::Path;

pub struct Record {
    pub id: String,
    pub title: String,
    pub username: String,
    pub encrypted_password: String,
    pub encrypted_notes: Option<String>,
    pub url: Option<String>,
    pub created_at: String,
    pub updated_at: String,
}

pub struct KeyInfo {
    pub salt: String,
    pub magic_iv: String,
    pub magic_ct: String,
    pub password_hint: String,
}

pub struct Database {
    conn: Connection,
}

impl Database {
    pub fn open(path: &Path) -> Result<Self, rusqlite::Error> {
        let conn = Connection::open(path)?;
        conn.execute_batch(
            "CREATE TABLE IF NOT EXISTS passwords (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                username TEXT NOT NULL DEFAULT '',
                encrypted_password TEXT NOT NULL,
                encrypted_notes TEXT,
                url TEXT,
                created_at TEXT NOT NULL DEFAULT (datetime('now')),
                updated_at TEXT NOT NULL DEFAULT (datetime('now'))
            );
            CREATE TABLE IF NOT EXISTS key_pair (
                id INTEGER PRIMARY KEY DEFAULT 1,
                salt TEXT NOT NULL,
                magic_iv TEXT NOT NULL,
                magic_ct TEXT NOT NULL,
                password_hint TEXT NOT NULL DEFAULT ''
            );"
        )?;
        Ok(Self { conn })
    }

    pub fn is_initialized(&self) -> bool {
        self.conn.query_row("SELECT COUNT(*) FROM key_pair", [], |r| r.get::<_, i32>(0)).unwrap_or(0) > 0
    }

    pub fn get_key_info(&self) -> Option<KeyInfo> {
        self.conn.query_row("SELECT salt, magic_iv, magic_ct, password_hint FROM key_pair WHERE id = 1", [], |r| {
            Ok(KeyInfo { salt: r.get(0)?, magic_iv: r.get(1)?, magic_ct: r.get(2)?, password_hint: r.get(3)? })
        }).ok()
    }

    pub fn save_key_info(&mut self, salt: &str, magic_iv: &str, magic_ct: &str, hint: &str) {
        self.conn.execute("INSERT OR REPLACE INTO key_pair (id, salt, magic_iv, magic_ct, password_hint) VALUES (1, ?, ?, ?, ?)",
            params![salt, magic_iv, magic_ct, hint]).ok();
    }

    pub fn insert(&mut self, id: &str, title: &str, username: &str, enc_pwd: &str, enc_notes: &Option<String>, url: &str) {
        self.conn.execute(
            "INSERT INTO passwords (id, title, username, encrypted_password, encrypted_notes, url) VALUES (?,?,?,?,?,?)",
            params![id, title, username, enc_pwd, enc_notes, url]
        ).ok();
    }

    pub fn list_all(&self) -> Vec<(String, String, String, String, Option<String>, Option<String>, String)> {
        let mut stmt = self.conn.prepare(
            "SELECT id, title, username, encrypted_password, encrypted_notes, url, updated_at FROM passwords ORDER BY updated_at DESC"
        ).unwrap();
        stmt.query_map([], |r| Ok((
            r.get(0)?, r.get(1)?, r.get(2)?, r.get(3)?, r.get(4)?, r.get(5)?, r.get(6)?,
        ))).unwrap().filter_map(|r| r.ok()).collect()
    }

    pub fn find_by_prefix(&self, prefix: &str) -> Option<Record> {
        let mut stmt = self.conn.prepare(
            "SELECT id, title, username, encrypted_password, encrypted_notes, url, created_at, updated_at FROM passwords WHERE id LIKE ?1"
        ).unwrap();
        stmt.query_row(params![format!("{}%", prefix)], |r| Ok(Record {
            id: r.get(0)?, title: r.get(1)?, username: r.get(2)?, encrypted_password: r.get(3)?,
            encrypted_notes: r.get(4)?, url: r.get(5)?, created_at: r.get(6)?, updated_at: r.get(7)?,
        })).ok()
    }

    pub fn update(&mut self, id: &str, title: &str, username: &str, enc_pwd: &str, enc_notes: &Option<String>, url: &str) {
        self.conn.execute(
            "UPDATE passwords SET title=?, username=?, encrypted_password=?, encrypted_notes=?, url=?, updated_at=datetime('now') WHERE id=?",
            params![title, username, enc_pwd, enc_notes, url, id]
        ).ok();
    }

    pub fn delete(&mut self, id: &str) {
        self.conn.execute("DELETE FROM passwords WHERE id = ?", params![id]).ok();
    }
}
