use serde::{Deserialize, Serialize};

#[derive(Debug, Deserialize)]
pub struct RegisterDeviceRequest {
    pub device_id: String,      // uuid
    pub device_name: String,    
    pub public_key: String,     // base64 RSA 公钥
    pub signature: String,      // 设备签名
}

#[derive(Debug, Deserialize)]
pub struct AuthorizeDeviceRequest {
    pub from_device_id: String,
    pub to_device_id: String,
    pub encrypted_data_key: String,
}

#[derive(Debug, Deserialize, Serialize)]
pub struct SyncPasswordRequest {
    pub id: String,
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

#[derive(Debug, Deserialize)]
pub struct BatchSyncRequest {
    pub records: Vec<SyncPasswordRequest>,
}
