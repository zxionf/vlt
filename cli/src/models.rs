#[derive(sqlx::FromRow, Clone, Debug)]
#[allow(dead_code)]
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

#[derive(sqlx::FromRow, Debug)]
#[allow(dead_code)]
pub struct KeyPair {
    pub salt: String,                     // Base64
    pub magic_text_iv: String,
    pub magic_text_cipher: String,        // 验证主密码的密文
    pub encrypted_private_key: String,    // 用 K_master 加密的 RSA 私钥
    pub password_hint: String,
}

#[derive(sqlx::FromRow, Debug)]
#[allow(dead_code)]
pub struct Device {
    pub device_id: String,                // UUID
    pub device_name: String,
    pub public_key: String,               // RSA 公钥 Base64
    pub encrypted_data_key: Option<String>, // 用本机公钥加密的 Data Key
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