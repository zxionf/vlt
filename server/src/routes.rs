use sqlx::Row;
use actix_web::{web, HttpResponse};
use crate::AppState;
use crate::models::*;

pub fn configure(cfg: &mut web::ServiceConfig) {
    cfg.service(
        web::scope("/api")
            .route("/health", web::get().to(health))
            .route("/devices/register", web::post().to(register_device))
            .route("/devices/{id}", web::get().to(get_device))
            .route("/devices/pending", web::get().to(list_pending_authorizations))
            .route("/devices/authorize", web::post().to(authorize_device))
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
    let result = sqlx::query(
        "INSERT OR REPLACE INTO devices (device_id, device_name, public_key, encrypted_data_key) VALUES (?, ?, ?, ?)"
    ).bind(&r.device_id).bind(&r.device_name).bind(&r.public_key).bind(&r.encrypted_data_key)
    .execute(&state.db).await;

    match result {
        Ok(_) => HttpResponse::Ok().json(serde_json::json!({"ok": true})),
        Err(e) => HttpResponse::InternalServerError().json(serde_json::json!({"error": e.to_string()})),
    }
}

async fn get_device(
    state: web::Data<AppState>,
    path: web::Path<String>,
) -> HttpResponse {
    let id = path.into_inner();
    let row = sqlx::query("SELECT device_id, device_name, public_key, encrypted_data_key, created_at FROM devices WHERE device_id = ?")
        .bind(&id).fetch_optional(&state.db).await;

    match row {
        Ok(Some(r)) => {
            let device = serde_json::json!({
                "device_id": r.get::<String,_>(0),
                "device_name": r.get::<String,_>(1),
                "public_key": r.get::<String,_>(2),
                "encrypted_data_key": r.get::<String,_>(3),
            });
            HttpResponse::Ok().json(device)
        }
        Ok(None) => HttpResponse::NotFound().json(serde_json::json!({"error": "not found"})),
        Err(e) => HttpResponse::InternalServerError().json(serde_json::json!({"error": e.to_string()})),
    }
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
