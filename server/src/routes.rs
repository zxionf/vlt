use sqlx::Row;
use actix_web::{web, HttpResponse};
use crate::AppState;
use crate::models::*;

const PENDING_CAPACITY: usize = 3;

pub fn configure(cfg: &mut web::ServiceConfig) {
    cfg.service(
        web::scope("/api")
            .route("/health", web::get().to(health))
            .route("/register", web::post().to(register_device))
            .route("/devices/pending", web::get().to(list_pending))
            .route("/devices/{id}", web::get().to(get_device))
            .route("/data-key/{device_id}", web::get().to(get_data_key))
            .route("/authorize", web::post().to(authorize_device))
            .route("/sync/push", web::post().to(sync_push))
            .route("/sync/pull/{since}", web::get().to(sync_pull))
    );
}

async fn health() -> HttpResponse {
    HttpResponse::Ok().json(serde_json::json!({"status": "ok"}))
}

async fn register_device(
    state: web::Data<AppState>,
    body: web::Json<RegisterDeviceRequest>,
) -> HttpResponse {
    let r = body.into_inner();

    let count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM devices")
        .fetch_one(&state.db).await.unwrap_or(0);

    if count == 0 {
        let now = chrono::Utc::now().timestamp_millis();
        let result = sqlx::query(
            "INSERT INTO devices (device_id, device_name, public_key, signature, registered_at, is_authorized) VALUES (?, ?, ?, ?, ?, 1)"
        ).bind(&r.device_id).bind(&r.device_name).bind(&r.public_key).bind(&r.signature)
         .bind(now).execute(&state.db).await;

        match result {
            Ok(_) => {
                log::info!("First device registered and authorized: {}", r.device_id);
                HttpResponse::Ok().json(serde_json::json!({"status": "authorized"}))
            }
            Err(e) => {
                log::error!("Failed to register first device: {}", e);
                HttpResponse::InternalServerError().json(serde_json::json!({"error": e.to_string()}))
            }
        }
    } else {
        let mut queue = state.pending_devices.lock().unwrap();
        if queue.len() >= PENDING_CAPACITY {
            HttpResponse::TooManyRequests().json(serde_json::json!({
                "status": "queue_full",
                "message": "待授权队列已满，请稍后再试"
            }))
        } else {
            queue.push_back(r);
            log::info!("Device added to pending queue ({} / {})", queue.len(), PENDING_CAPACITY);
            HttpResponse::Ok().json(serde_json::json!({"status": "pending"}))
        }
    }
}

async fn get_device(
    state: web::Data<AppState>,
    path: web::Path<String>,
) -> HttpResponse {
    let id = path.into_inner();
    let row = sqlx::query_as::<_, DeviceRecord>(
        "SELECT * FROM devices WHERE device_id = ?"
    ).bind(&id).fetch_optional(&state.db).await;

    match row {
        Ok(Some(r)) => HttpResponse::Ok().json(serde_json::json!({
            "device_id": r.device_id,
            "device_name": r.device_name,
            "public_key": r.public_key,
        })),
        Ok(None) => HttpResponse::NotFound().json(serde_json::json!({"error": "not found"})),
        Err(e) => HttpResponse::InternalServerError().json(serde_json::json!({"error": e.to_string()})),
    }
}

async fn list_pending(
    state: web::Data<AppState>,
) -> HttpResponse {
    let queue = state.pending_devices.lock().unwrap();
    let out: Vec<PendingDevice> = queue.iter().map(|r| PendingDevice {
        device_id: r.device_id.clone(),
        device_name: r.device_name.clone(),
        public_key: r.public_key.clone(),
    }).collect();
    HttpResponse::Ok().json(out)
}

async fn get_data_key(
    state: web::Data<AppState>,
    path: web::Path<String>,
) -> HttpResponse {
    let device_id = path.into_inner();
    let row = sqlx::query(
        "SELECT source_device_id, encrypted_data_key, created_at FROM data_keys WHERE target_device_id = ?"
    ).bind(&device_id).fetch_all(&state.db).await;

    match row {
        Ok(list) if list.is_empty() => {
            HttpResponse::NotFound().json(serde_json::json!({"error": "no data key for this device"}))
        }
        Ok(list) => {
            let keys: Vec<serde_json::Value> = list.iter().map(|r| serde_json::json!({
                "source_device_id": r.get::<String,_>(0),
                "encrypted_data_key": r.get::<String,_>(1),
                "created_at": r.get::<i64,_>(2),
            })).collect();
            HttpResponse::Ok().json(keys)
        }
        Err(e) => HttpResponse::InternalServerError().json(serde_json::json!({"error": e.to_string()})),
    }
}
async fn authorize_device(
    state: web::Data<AppState>,
    body: web::Json<AuthorizeDeviceRequest>,
) -> HttpResponse {
    let r = body.into_inner();
    let now = chrono::Utc::now().timestamp_millis();

    let reg = sqlx::query(
        "INSERT OR REPLACE INTO devices (device_id, device_name, public_key, signature, registered_at, is_authorized) VALUES (?, '', '', '', ?, 1)"
    ).bind(&r.target_device_id).bind(now).execute(&state.db).await;

    if reg.is_err() {
        return HttpResponse::InternalServerError().json(serde_json::json!({"error": "db error"}));
    }

    sqlx::query(
        "INSERT OR REPLACE INTO data_keys (target_device_id, source_device_id, encrypted_data_key, created_at) VALUES (?, ?, ?, ?)"
    ).bind(&r.target_device_id).bind(&r.source_device_id).bind(&r.encrypted_data_key).bind(now)
     .execute(&state.db).await.ok();

    let mut queue = state.pending_devices.lock().unwrap();
    queue.retain(|d| d.device_id != r.target_device_id);

    HttpResponse::Ok().json(serde_json::json!({"ok": true}))
}

#[derive(sqlx::FromRow)]
struct SyncPullRow {
    record_id: String,
    source_device_id: String,
    encrypted_blob: String,
    sync_version: i32,
    operation: String,
    client_updated_at: i64,
    server_updated_at: i64,
}

async fn sync_push(
    state: web::Data<AppState>,
    body: web::Json<BatchSyncRequest>,
) -> HttpResponse {
    let mut updated = 0;
    let now = chrono::Utc::now().timestamp_millis();
    for rec in &body.records {
        let op = if rec.operation == "delete" { "delete" }
            else if rec.sync_version <= 1 { "create" }
            else { "update" };
        let r = sqlx::query(
            "INSERT OR REPLACE INTO sync_records (record_id, source_device_id, encrypted_blob, sync_version, operation, client_updated_at, server_updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)"
        ).bind(&rec.record_id).bind(&rec.source_device_id).bind(&rec.encrypted_blob)
         .bind(rec.sync_version).bind(op).bind(rec.client_updated_at).bind(now)
         .execute(&state.db).await;
        if r.is_ok() { updated += 1; }
    }
    HttpResponse::Ok().json(serde_json::json!({"updated": updated}))
}

async fn sync_pull(
    state: web::Data<AppState>,
    path: web::Path<i64>,
) -> HttpResponse {
    let since = path.into_inner();
    let rows = sqlx::query_as::<_, SyncPullRow>(
        "SELECT record_id, source_device_id, encrypted_blob, sync_version, operation, client_updated_at, server_updated_at FROM sync_records WHERE server_updated_at > ? ORDER BY server_updated_at"
    ).bind(since).fetch_all(&state.db).await;

    match rows {
        Ok(list) => {
            let out: Vec<serde_json::Value> = list.iter().map(|r| serde_json::json!({
                "record_id": r.record_id,
                "source_device_id": r.source_device_id,
                "encrypted_blob": r.encrypted_blob,
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