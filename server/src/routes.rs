use actix_web::{web, HttpResponse};
use crate::AppState;
use crate::models::BatchSyncRequest;

pub fn configure(cfg: &mut web::ServiceConfig) {
    cfg.service(
        web::scope("/api")
            .route("/health", web::get().to(health))
            .route("/sync", web::post().to(sync_push))
            .route("/sync", web::get().to(sync_pull))
    );
}

async fn health() -> HttpResponse {
    HttpResponse::Ok().json(serde_json::json!({"status": "ok"}))
}

#[derive(sqlx::FromRow)]
struct SyncPullRow {
    record_id: String,
    source_device_id: String,
    encrypted_blob: String,
    blob_nonce: String,
    sync_version: i32,
    operation: String,
    client_updated_at: i64,
    server_updated_at: i64,
}

async fn sync_push(
    state: web::Data<AppState>,
    body: web::Json<BatchSyncRequest>,
) -> HttpResponse {
    let now = chrono::Utc::now().timestamp_millis();
    let mut verified = 0;

    for rec in &body.records {
        let sig_ok = match verify_sig(&state, &rec.source_device_id, rec).await {
            Ok(true) => true,
            Ok(false) => { eprintln!("[sync_push] 签名验证失败: {}", rec.source_device_id); false }
            Err(e) => { eprintln!("[sync_push] 验证异常: {} => {e}", rec.source_device_id); false }
        };
        if !sig_ok { continue; }

        let op = if rec.operation == "delete" { "delete" }
            else if rec.sync_version <= 1 { "create" }
            else { "update" };
        let r = sqlx::query(
            "INSERT OR REPLACE INTO sync_records (record_id, source_device_id, encrypted_blob, blob_nonce, sync_version, operation, client_updated_at, server_updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
        ).bind(&rec.record_id).bind(&rec.source_device_id).bind(&rec.encrypted_blob)
         .bind(&rec.blob_nonce).bind(rec.sync_version).bind(op).bind(rec.client_updated_at).bind(now)
          .execute(&state.db).await;
        if r.is_ok() { verified += 1; }
    }
    HttpResponse::Ok().json(serde_json::json!({"verified": verified}))
}

async fn verify_sig(app: &web::Data<AppState>, device_id: &str, rec: &crate::models::SyncPushRecord) -> Result<bool, String> {
    use sqlx::Row;
    use base64::Engine;
    use ed25519_dalek::{VerifyingKey, Signature};

    let sig_base64 = &rec.signature;
    if sig_base64.is_empty() { return Err("no record signature".to_string()); }

    let row = sqlx::query("SELECT public_key, signature FROM devices WHERE device_id = ?")
        .bind(device_id).fetch_optional(&app.db).await.map_err(|e| e.to_string())?;
    let (pub_key_b64, dev_sig_b64) = match row {
        Some(r) => (r.get::<String,_>(0), r.get::<String,_>(1)),
        None => return Err("unknown device".to_string()),
    };
    if dev_sig_b64.is_empty() { return Err("device has no signature".to_string()); }

    let pub_key_bytes = base64::engine::general_purpose::STANDARD.decode(&pub_key_b64).map_err(|e| format!("pub_key decode: {e}"))?;
    let pub_arr: [u8; 32] = pub_key_bytes[..32].try_into().map_err(|_| "invalid pub_len".to_string())?;
    let verifying_key = VerifyingKey::from_bytes(&pub_arr).map_err(|e| format!("pub key: {e}"))?;

    // 验证设备签名（公钥对 device_id 的签名）
    let dev_sig_bytes = base64::engine::general_purpose::STANDARD.decode(&dev_sig_b64).map_err(|e| format!("dev sig decode: {e}"))?;
    let dev_sig_arr: [u8; 64] = dev_sig_bytes[..64].try_into().map_err(|_| "invalid dev_sig len".to_string())?;
    verifying_key.verify_strict(device_id.as_bytes(), &Signature::from_bytes(&dev_sig_arr)).map_err(|e| format!("dev sig: {e}"))?;

    // ---
    // 验证 record 签名
    let sig_bytes = base64::engine::general_purpose::STANDARD.decode(sig_base64).map_err(|e| format!("sig decode: {e}"))?;
    let sig_arr: [u8; 64] = sig_bytes[..64].try_into().map_err(|_| "invalid sig len".to_string())?;
    let signature = Signature::from_bytes(&sig_arr);

    let msg_str = format!(
        "{}|{}|{}|{}|{}|{}|{}",
        rec.record_id, rec.source_device_id, rec.encrypted_blob,
        rec.blob_nonce, rec.sync_version, rec.client_updated_at, rec.operation,
    );
    verifying_key.verify_strict(msg_str.as_bytes(), &signature).map_err(|e| format!("sig: {e}"))?;
    Ok(true)
}

async fn sync_pull(
    state: web::Data<AppState>,
    query: web::Query<std::collections::HashMap<String, String>>,
) -> HttpResponse {
    let since: i64 = query.get("since").and_then(|s| s.parse().ok()).unwrap_or(0);
    let rows = sqlx::query_as::<_, SyncPullRow>(
        "SELECT * FROM sync_records WHERE server_updated_at > ? ORDER BY server_updated_at"
    ).bind(since).fetch_all(&state.db).await;

    match rows {
        Ok(list) => {
            let out: Vec<serde_json::Value> = list.iter().map(|r| serde_json::json!({
                "record_id": r.record_id,
                "source_device_id": r.source_device_id,
                "encrypted_blob": r.encrypted_blob,
                "blob_nonce": r.blob_nonce,
                "sync_version": r.sync_version,
                "operation": r.operation,
                "client_updated_at": r.client_updated_at,
                "server_updated_at": r.server_updated_at,
            })).collect();
            HttpResponse::Ok().json(out)
        }
        Err(e) => HttpResponse::InternalServerError().json(serde_json::json!({"error": e.to_string()})),
    }
}