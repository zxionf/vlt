#[derive(sqlx::FromRow, Clone, Debug)]
#[allow(dead_code)]
pub struct Vault {
    pub id: String,
    pub title: String,
    pub username: String,
    pub enc_password: String,
    pub enc_notes: Option<String>,
    pub url: Option<String>,
    pub version: i32,
    pub is_deleted: bool,
    pub created_at: i64,
    pub updated_at: i64,
}

#[derive(sqlx::FromRow, Debug)]
#[allow(dead_code)]
pub struct Config {
    pub device_id: String,
    pub kdf_salt: String,
    pub kdf_iter: i32,
    pub magic_text: String,
    pub device_name: String,
    pub enc_priv_key: String,
    pub pub_key: String,
    pub password_hint: String,
}

#[allow(dead_code)]
pub struct Tag {
    pub id: i32,
    pub name: String,
}

#[allow(dead_code)]
pub struct VaultTagJoin {
    pub vault_id: String,
    pub tag_id: i32,
}