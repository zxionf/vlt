use serde::Deserialize;

#[derive(Debug, Deserialize)]
pub struct SyncPushRecord {
    pub record_id: String,
    pub source_device_id: String,
    pub encrypted_blob: String,
    #[serde(default)]
    pub blob_nonce: String,
    pub sync_version: i32,
    #[serde(default)]
    pub client_updated_at: i64,
    #[serde(default)]
    pub operation: String,
    #[serde(default)]
    pub signature: String,
}

#[derive(Debug, Deserialize)]
pub struct BatchSyncRequest {
    pub records: Vec<SyncPushRecord>,
}