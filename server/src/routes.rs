use sqlx::Row;
use actix_web::{web, HttpResponse};
use crate::AppState;
use crate::models::*;

pub fn configure(cfg: &mut web::ServiceConfig) {
    cfg.service(
        web::scope("/api")
            .route("/health", web::get().to(health))
            .route("/register", web::post().to(register_device))
            // 设备相关
            .route("/devices/{id}", web::get().to(get_device))               // 查看设备信息（已授权设备可用）
            .route("/devices/pending", web::get().to(list_pending_authorizations)) // 待授权设备列表
            .route("/data-key/{device_id}", web::get().to(get_encrypted_data_key)) // 获取本机加密DataKey
            .route("/authorize", web::post().to(authorize_device))           // 授权设备（上传加密DataKey）
            // 同步相关
            .route("/sync/push", web::post().to(sync_push))
            .route("/sync/pull/{device_id}/{since}", web::get().to(sync_pull)) // 增量拉取，排除自身
    );
}

async fn health() -> HttpResponse {
    log::info!("Health check");
    HttpResponse::Ok().json(serde_json::json!({"status": "ok"}))
}

async fn register_device(
    state: web::Data<AppState>,
    body: web::Json<RegisterDeviceRequest>,
) -> HttpResponse {
    log::info!("Registering device: {:#?}", body);
    let r = body.into_inner();
    
    let count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM registered_devices")
        .fetch_one(&state.db)
        .await
        .unwrap_or(0);
    if count == 0 {
        // 第一个设备，直接注册并授权
        let result = sqlx::query(
            "INSERT INTO registered_devices (device_id, device_name, public_key, signature, registered_at, is_authorized) VALUES (?, ?, ?, ?, ?, ?)"
        ).bind(&r.device_id).bind(&r.device_name).bind(&r.public_key).bind(&r.signature)
        .bind(chrono::Local::now().timestamp_millis()).bind(1)
        .execute(&state.db)
        .await;

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
        // 后续设备，加入待授权队列
        let mut map = state.pending_devices.lock().unwrap(); // 注意 await
        // 如果队列中已有该 device_id，可以覆盖或忽略，这里覆盖
        map.insert(r.device_id.clone(), r);
        log::info!("Device added to pending queue");
        HttpResponse::Ok().json(serde_json::json!({"status": "pending"}))
    }
}

async fn get_device(
    state: web::Data<AppState>,
    path: web::Path<String>,
) -> HttpResponse {
    let id = path.into_inner();
    let row = sqlx::query_as::<_,RegisteredDevice>("SELECT * FROM registered_devices WHERE device_id = ?")
        .bind(&id).fetch_optional(&state.db).await;

    match row {
        Ok(Some(r)) => {
            let device = serde_json::json!({
                "device_id": &r.device_id,
                "device_name": &r.device_name,
                "public_key": &r.public_key,
                // "encrypted_data_key": r.get::<String,_>(3),
            });
            HttpResponse::Ok().json(device)
        }
        Ok(None) => HttpResponse::NotFound().json(serde_json::json!({"error": "not found"})),
        Err(e) => HttpResponse::InternalServerError().json(serde_json::json!({"error": e.to_string()})),
    }
}

async fn get_encrypted_data_key() -> HttpResponse {
    HttpResponse::InternalServerError().json(serde_json::json!({"error": "e"}))
}

async fn list_pending_authorizations(
    state: web::Data<AppState>,
) -> HttpResponse {
    let rows = sqlx::query("SELECT id, from_device_id, to_device_id, public_key, status FROM pending_authorizations WHERE status = 'pending'")
        .fetch_all(&state.db).await;

    match rows {
        Ok(list) => {
            let out: Vec<serde_json::Value> = list.iter().map(|r| serde_json::json!({
                "id": r.get::<i64,_>(6),
                "from_device_id": r.get::<String,_>(1),
                "to_device_id": r.get::<String,_>(2),
                "public_key": r.get::<String,_>(3),
                "status": r.get::<String,_>(4),
            })).collect();
            HttpResponse::Ok().json(out)
        }
        Err(e) => HttpResponse::InternalServerError().json(serde_json::json!({"error": e.to_string()})),
    }
}

async fn authorize_device(
    state: web::Data<AppState>,
    body: web::Json<AuthorizeDeviceRequest>,
) -> HttpResponse {
    let r = body.into_inner();
    sqlx::query("INSERT OR REPLACE INTO devices (device_id, device_name, public_key, encrypted_data_key) VALUES (?, '', '', ?)")
        .bind(&r.to_device_id).bind(&r.encrypted_data_key)
        .execute(&state.db).await.ok();

    sqlx::query("UPDATE pending_authorizations SET status = 'approved' WHERE to_device_id = ?")
        .bind(&r.to_device_id).execute(&state.db).await.ok();

    HttpResponse::Ok().json(serde_json::json!({"ok": true}))
}

async fn sync_push(
    state: web::Data<AppState>,
    body: web::Json<BatchSyncRequest>,
) -> HttpResponse {
    let mut updated = 0;
    for rec in &body.records {
        let r = sqlx::query(
            "INSERT OR REPLACE INTO password_records (id, encrypted_password, encrypted_notes, url, created_device_id, last_modified_device_id, created_at, updated_at, sync_version, is_deleted) VALUES (?,?,?,?,?,?,?,?,?,?)"
        ).bind(&rec.id).bind(&rec.encrypted_password).bind(&rec.encrypted_notes).bind(&rec.url)
        .bind(&rec.created_device_id).bind(&rec.last_modified_device_id)
        .bind(rec.created_at).bind(rec.updated_at).bind(rec.sync_version).bind(rec.is_deleted as i32)
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
    let rows = sqlx::query("SELECT id, encrypted_password, encrypted_notes, url, created_device_id, last_modified_device_id, created_at, updated_at, sync_version, is_deleted FROM password_records WHERE updated_at > ? ORDER BY updated_at")
        .bind(since).fetch_all(&state.db).await;

    match rows {
        Ok(list) => {
            let out: Vec<serde_json::Value> = list.iter().map(|r| serde_json::json!({
                "id": r.get::<String,_>(0),
                "encrypted_password": r.get::<String,_>(1),
                "encrypted_notes": r.get::<String,_>(2),
                "url": r.get::<String,_>(3),
                "created_device_id": r.get::<String,_>(4),
                "last_modified_device_id": r.get::<String,_>(5),
                "created_at": r.get::<i64,_>(6),
                "updated_at": r.get::<i64,_>(6),
                "sync_version": r.get::<i32,_>(8),
                "is_deleted": r.get::<i32,_>(9),
            })).collect();
            HttpResponse::Ok().json(out)
        }
        Err(e) => HttpResponse::InternalServerError().json(serde_json::json!({"error": e.to_string()})),
    }
}
