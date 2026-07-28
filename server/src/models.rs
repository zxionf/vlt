use serde::{Deserialize, Serialize};

#[derive(sqlx::FromRow, Debug, Clone)]
#[allow(dead_code)]
pub struct DeviceRecord {
    pub device_id: String,
    pub device_name: String,
    pub public_key: String,
    pub signature: String,
    pub registered_at: i64,
    pub is_authorized: bool,
}

#[derive(sqlx::FromRow, Debug)]
#[allow(dead_code)]
pub struct DataKey {
    pub target_device_id: String,
    pub source_device_id: String,
    pub encrypted_data_key: String,
    pub created_at: i64,
}

#[derive(Debug, Deserialize)]
pub struct RegisterDeviceRequest {
    pub device_id: String,
    pub device_name: String,
    pub public_key: String,
    pub signature: String,
}

#[derive(Debug, Deserialize)]
pub struct AuthorizeDeviceRequest {
    pub source_device_id: String,
    pub target_device_id: String,
    pub encrypted_data_key: String,
}

#[derive(Debug, Deserialize)]
pub struct SyncPushRecord {
    pub record_id: String,
    pub source_device_id: String,
    pub encrypted_blob: String,
    pub sync_version: i32,
    #[serde(default)]
    pub client_updated_at: i64,
    #[serde(default)]
    pub operation: String,
}

#[derive(Debug, Deserialize)]
pub struct BatchSyncRequest {
    pub records: Vec<SyncPushRecord>,
}

#[derive(Debug, Serialize)]
pub struct PendingDevice {
    pub device_id: String,
    pub device_name: String,
    pub public_key: String,
}