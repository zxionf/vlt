use rusqlite::{Connection, params};
use std::{path::Path, result};

pub struct Password {
    pub id: String,                       // UUID
    pub title: String,
    pub username: String,
    pub encrypted_password: String,       // base64(iv):base64(cipher)
    pub encrypted_notes: Option<String>,
    pub url: Option<String>,
    pub created_device_id: String,
    pub last_modified_device_id: String,
    pub created_at: i64,                  // 毫秒时间戳
    pub updated_at: i64,
    pub sync_version: i32,
    pub is_deleted: bool,
}

pub struct KeyPair {
    pub salt: String,                     // Base64
    pub magic_text_iv: String,
    pub magic_text_cipher: String,        // 验证主密码的密文
    pub encrypted_private_key: String,    // 用 K_master 加密的 RSA 私钥
    pub password_hint: String,
}

pub struct Device {
    pub device_id: String,                // UUID
    pub device_name: String,
    pub public_key: String,               // RSA 公钥 Base64
    pub encrypted_data_key: Option<String>, // 用本机公钥加密的 Data Key
    pub is_current_device: bool,
    pub created_at: i64,
}

pub struct Tag {
    pub id: i32,          // 自增主键
    pub name: String,     // 标签名（唯一）
}

pub struct PasswordTagJoin {
    pub password_id: String,  // 对应 passwords.id
    pub tag_id: i32,          // 对应 tags.id
}

pub struct Database {
    conn: Connection,
}

impl Database {
    pub fn open(path: &Path) -> Result<Self, rusqlite::Error> {
        let conn = Connection::open(path)?;
        conn.execute_batch(
            "CREATE TABLE IF NOT EXISTS key_pair (
            id INTEGER PRIMARY KEY DEFAULT 1,
            salt TEXT NOT NULL,
            magic_text_iv TEXT NOT NULL,
            magic_text_cipher TEXT NOT NULL,
            encrypted_private_key TEXT NOT NULL,
            password_hint TEXT NOT NULL DEFAULT ''
        );

        CREATE TABLE IF NOT EXISTS devices (
            device_id TEXT PRIMARY KEY,
            device_name TEXT NOT NULL,
            public_key TEXT NOT NULL,
            encrypted_data_key TEXT,
            is_current_device INTEGER NOT NULL DEFAULT 0,
            created_at INTEGER NOT NULL
        );

        CREATE TABLE IF NOT EXISTS passwords (
            id TEXT PRIMARY KEY,
            title TEXT NOT NULL,
            username TEXT NOT NULL DEFAULT '',
            encrypted_password TEXT NOT NULL,
            encrypted_notes TEXT,
            url TEXT,
            created_device_id TEXT NOT NULL,
            last_modified_device_id TEXT NOT NULL,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL,
            sync_version INTEGER NOT NULL DEFAULT 1,
            is_deleted INTEGER NOT NULL DEFAULT 0
        );

        CREATE TABLE IF NOT EXISTS tags (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL UNIQUE
        );

        CREATE TABLE IF NOT EXISTS password_tag_join (
            password_id TEXT NOT NULL,
            tag_id INTEGER NOT NULL,
            PRIMARY KEY (password_id, tag_id),
            FOREIGN KEY (password_id) REFERENCES passwords(id) ON DELETE CASCADE,
            FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE CASCADE
        );

        -- 可选：开启外键约束（需在每次连接后执行，但建表后可单独设置）
        PRAGMA foreign_keys = ON;"
        )?;
        Ok(Self { conn })
    }

    pub fn is_initialized(&self) -> bool {
        self.conn.query_row("SELECT COUNT(*) FROM key_pair", [], |r| r.get::<_, i32>(0)).unwrap_or(0) > 0
    }

    pub fn get_key_info(&self) -> Option<KeyPair> {
        self.conn.query_row("SELECT salt, magic_text_iv, magic_text_cipher, password_hint FROM key_pair WHERE id = 1", [], |r| {
            Ok(KeyPair { salt: r.get(0)?, magic_text_iv: r.get(1)?, magic_text_cipher: r.get(2)?, password_hint: r.get(3)?, encrypted_private_key:String::from("value") })
        }).ok()
    }

    pub fn save_device_info(&mut self, device_id: &str, device_name: &str, public_key: &str, encrypted_data_key: &str, is_current_device: bool, created_at: i64){
        let result = self.conn.execute("INSERT OR REPLACE INTO devices (device_id, device_name, public_key, encrypted_data_key, is_current_device, created_at) VALUES (?, ?, ?, ?, ?, ?)",
            params![device_id, device_name, public_key, encrypted_data_key, is_current_device as i32, created_at]);
        match result {
            Ok(_) => {},
            Err(e) => {println!("err save_key_info: {}",e)}
        }
    }

    pub fn get_device_info(&self) -> Option<Device> {
        self.conn.query_row("SELECT device_id, device_name, public_key, encrypted_data_key created_at FROM devices WHERE is_current_device = 1", [], |r| {
            Ok(Device { 
                device_id : r.get(0)?,
                device_name: r.get(1)?,
                public_key: r.get(2)?,
                encrypted_data_key: r.get(3)?,
                is_current_device: true,
                created_at: r.get(4)?
             })
        }).ok()
    }

    pub fn save_key_info(&mut self, salt: &str, magic_text_iv: &str, magic_text_cipher: &str, password_hint: &str, encrypted_private_key: &str) {
        let result = self.conn.execute("INSERT OR REPLACE INTO key_pair (id, salt, magic_text_iv, magic_text_cipher, password_hint, encrypted_private_key) VALUES (1, ?, ?, ?, ?, ?)",
            params![salt, magic_text_iv, magic_text_cipher, password_hint, encrypted_private_key]);
        match result {
            Ok(_) => {},
            Err(e) => {println!("err save_key_info: {}",e)}
        }
    }

    pub fn insert_passwd(&mut self, id: &str, title: &str, username: &str, enc_pwd: &str, enc_notes: &Option<String>, url: &str, created_device_id:&str,last_modified_device_id:&str, created_at: i64, updated_at: i64) {
        self.conn.execute(
            "INSERT INTO passwords (id, title, username, encrypted_password, encrypted_notes, url, created_device_id, last_modified_device_id,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
            params![id, title, username, enc_pwd, enc_notes, url, created_device_id, last_modified_device_id, created_at, updated_at]
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

    pub fn find_by_prefix(&self, prefix: &str) -> Option<Password> {
        let mut stmt = self.conn.prepare(
            "SELECT id, title, username, encrypted_password, encrypted_notes, url, created_at, updated_at FROM passwords WHERE id LIKE ?1"
        ).unwrap();
        stmt.query_row(params![format!("{}%", prefix)], |r| Ok(Password {
            id: r.get(0)?, title: r.get(1)?, username: r.get(2)?, encrypted_password: r.get(3)?,
            encrypted_notes: r.get(4)?, url: r.get(5)?, created_at: r.get(6)?, updated_at: r.get(7)?,
            created_device_id:String::from("null"),last_modified_device_id:String::from("null"),sync_version:1,is_deleted:false
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
