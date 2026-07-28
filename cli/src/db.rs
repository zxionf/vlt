use sqlx::SqlitePool;

pub async fn migrate(pool: &SqlitePool) {
    sqlx::query("PRAGMA foreign_keys = ON;").execute(pool).await.ok();
    sqlx::query("CREATE TABLE IF NOT EXISTS master_auth (
            id INTEGER PRIMARY KEY DEFAULT 1,
            salt TEXT NOT NULL,
            auth_iv TEXT NOT NULL,
            auth_cipher TEXT NOT NULL,
            password_hint TEXT NOT NULL DEFAULT '',
            created_at INTEGER NOT NULL DEFAULT 0
        );").execute(pool).await.ok();
    sqlx::query("CREATE TABLE IF NOT EXISTS devices (
            device_id TEXT PRIMARY KEY,
            device_name TEXT NOT NULL,
            public_key TEXT NOT NULL,
            encrypted_private_key TEXT,
            encrypted_data_key TEXT NOT NULL,
            is_current_device INTEGER NOT NULL DEFAULT 0,
            created_at INTEGER NOT NULL
        );").execute(pool).await.ok();
    sqlx::query("CREATE TABLE IF NOT EXISTS passwords (
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
        );").execute(pool).await.ok();
    sqlx::query("CREATE TABLE IF NOT EXISTS tags (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL UNIQUE
        );").execute(pool).await.ok();
    sqlx::query("CREATE TABLE IF NOT EXISTS password_tag_join (
            password_id TEXT NOT NULL,
            tag_id INTEGER NOT NULL,
            PRIMARY KEY (password_id, tag_id),
            FOREIGN KEY (password_id) REFERENCES passwords(id) ON DELETE CASCADE,
            FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE CASCADE
        );").execute(pool).await.ok();
    sqlx::query("CREATE TABLE IF NOT EXISTS sync_cursor (
            device_id TEXT PRIMARY KEY,
            last_pulled_at INTEGER NOT NULL DEFAULT 0,
            last_server_timestamp INTEGER NOT NULL DEFAULT 0
        );").execute(pool).await.ok();
}



// impl Database {

//     pub fn get_key_info(&self) -> Option<KeyPair> {
//         self.conn.query_row("SELECT salt, magic_text_iv, magic_text_cipher, password_hint FROM key_pair WHERE id = 1", [], |r| {
//             Ok(KeyPair { salt: r.get(0)?, magic_text_iv: r.get(1)?, magic_text_cipher: r.get(2)?, password_hint: r.get(3)?, encrypted_private_key:String::from("value") })
//         }).ok()
//     }

//     pub fn get_device_info(&self) -> Option<Device> {
//         self.conn.query_row("SELECT device_id, device_name, public_key, encrypted_data_key created_at FROM devices WHERE is_current_device = 1", [], |r| {
//             Ok(Device { 
//                 device_id : r.get(0)?,
//                 device_name: r.get(1)?,
//                 public_key: r.get(2)?,
//                 encrypted_data_key: r.get(3)?,
//                 is_current_device: true,
//                 created_at: r.get(4)?
//              })
//         }).ok()
//     }

//     pub fn insert_passwd(&mut self, id: &str, title: &str, username: &str, enc_pwd: &str, enc_notes: &Option<String>, url: &str, created_device_id:&str,last_modified_device_id:&str, created_at: i64, updated_at: i64) {
//         self.conn.execute(
//             "INSERT INTO passwords (id, title, username, encrypted_password, encrypted_notes, url, created_device_id, last_modified_device_id,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
//             params![id, title, username, enc_pwd, enc_notes, url, created_device_id, last_modified_device_id, created_at, updated_at]
//         ).ok();
//     }

//     pub fn list_all(&self) -> Vec<(String, String, String, String, Option<String>, Option<String>, String)> {
//         let mut stmt = self.conn.prepare(
//             "SELECT id, title, username, encrypted_password, encrypted_notes, url, updated_at FROM passwords ORDER BY updated_at DESC"
//         ).unwrap();
//         stmt.query_map([], |r| Ok((
//             r.get(0)?, r.get(1)?, r.get(2)?, r.get(3)?, r.get(4)?, r.get(5)?, r.get(6)?,
//         ))).unwrap().filter_map(|r| r.ok()).collect()
//     }

//     pub fn find_by_prefix(&self, prefix: &str) -> Option<Password> {
//         let mut stmt = self.conn.prepare(
//             "SELECT id, title, username, encrypted_password, encrypted_notes, url, created_at, updated_at FROM passwords WHERE id LIKE ?1"
//         ).unwrap();
//         stmt.query_row(params![format!("{}%", prefix)], |r| Ok(Password {
//             id: r.get(0)?, title: r.get(1)?, username: r.get(2)?, encrypted_password: r.get(3)?,
//             encrypted_notes: r.get(4)?, url: r.get(5)?, created_at: r.get(6)?, updated_at: r.get(7)?,
//             created_device_id:String::from("null"),last_modified_device_id:String::from("null"),sync_version:1,is_deleted:false
//         })).ok()
//     }

//     pub fn update(&mut self, id: &str, title: &str, username: &str, enc_pwd: &str, enc_notes: &Option<String>, url: &str) {
//         self.conn.execute(
//             "UPDATE passwords SET title=?, username=?, encrypted_password=?, encrypted_notes=?, url=?, updated_at=datetime('now') WHERE id=?",
//             params![title, username, enc_pwd, enc_notes, url, id]
//         ).ok();
//     }

//     pub fn delete(&mut self, id: &str) {
//         self.conn.execute("DELETE FROM passwords WHERE id = ?", params![id]).ok();
//     }
// }
