#[derive(sqlx::FromRow, Clone, Debug)]
#[allow(dead_code)]
pub struct Password {
    pub id: String,
    pub title: String,
    pub username: String,
    pub encrypted_password: String,
    pub encrypted_notes: Option<String>,
    pub url: Option<String>,
    pub created_device_id: String,
    pub last_modified_device_id: String,
    pub created_at: i64,
    pub updated_at: i64,
    pub sync_version: i32,
    pub is_deleted: bool,
}

#[derive(sqlx::FromRow, Debug)]
#[allow(dead_code)]
pub struct MasterAuth {
    pub salt: String,
    pub auth_iv: String,
    pub auth_cipher: String,
    pub password_hint: String,
    pub created_at: i64,
}

#[derive(sqlx::FromRow, Debug)]
#[allow(dead_code)]
pub struct Device {
    pub device_id: String,
    pub device_name: String,
    pub public_key: String,
    pub encrypted_private_key: Option<String>,
    pub encrypted_data_key: String,
    pub is_current_device: bool,
    pub created_at: i64,
}

#[allow(dead_code)]
pub struct Tag {
    pub id: i32,          // 自增主键
    pub name: String,     // 标签名（唯一）
}

#[allow(dead_code)]
pub struct PasswordTagJoin {
    pub password_id: String,  // 对应 passwords.id
    pub tag_id: i32,          // 对应 tags.id
}