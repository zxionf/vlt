use clap::{Parser, Subcommand};
use sqlx::sqlite::{SqlitePool, SqlitePoolOptions};
use sqlx::Row;

mod db;

/// vlts — 服务器管理工具
#[derive(Parser)]
#[command(name = "vlts", version, about)]
struct Cli {
    #[arg(short, long, default_value = "pwd_server.db")]
    db: String,

    #[command(subcommand)]
    command: Commands,
}

#[derive(Subcommand)]
enum Commands {
    ListDevices,
    Authorize {
        device_id: String,
        encrypted_data_key: String,
    },
    ListRecords {
        #[arg(short)]
        device_id: Option<String>,
        #[arg(short, long, default_value = "20")]
        limit: i64,
    },
    Stats,
}

#[tokio::main]
async fn main() {
    let cli = Cli::parse();
    let db_url = format!("sqlite:{}?mode=rwc", cli.db);
    let pool = SqlitePoolOptions::new().max_connections(1).connect(&db_url).await
        .expect("无法连接数据库");

    db::migrate(&pool).await;

    match cli.command {
        Commands::ListDevices => cmd_list_devices(&pool).await,
        Commands::Authorize { device_id, encrypted_data_key } => cmd_authorize(&pool, &device_id, &encrypted_data_key).await,
        Commands::ListRecords { device_id, limit } => cmd_list_records(&pool, device_id, limit).await,
        Commands::Stats => cmd_stats(&pool).await,
    }
}

async fn cmd_list_devices(pool: &SqlitePool) {
    let rows = sqlx::query(
        "SELECT device_id, device_name, is_authorized, registered_at FROM devices ORDER BY registered_at DESC"
    ).fetch_all(pool).await.unwrap();

    if rows.is_empty() { println!("暂无设备"); return; }
    println!("{:<38} {:<20} {:>8}  {}", "device_id", "device_name", "authed", "registered_at");
    println!("{}", "-".repeat(85));
    for r in &rows {
        let auth: i64 = r.get(2);
        println!("{:<38} {:<20} {:>8}  {}",
            r.get::<String,_>(0), r.get::<String,_>(1), auth, r.get::<i64,_>(3));
    }
}

async fn cmd_authorize(pool: &SqlitePool, device_id: &str, encrypted_data_key: &str) {
    let now = chrono::Utc::now().timestamp_millis();
    match sqlx::query(
        "INSERT OR REPLACE INTO devices (device_id, device_name, public_key, signature, registered_at, is_authorized) VALUES (?, '', '', '', ?, 1)"
    ).bind(device_id).bind(now).execute(pool).await {
        Ok(r) if r.rows_affected() > 0 => {
            sqlx::query(
                "INSERT OR REPLACE INTO data_keys (target_device_id, source_device_id, encrypted_data_key, created_at) VALUES (?, 'admin', ?, ?)"
            ).bind(device_id).bind(encrypted_data_key).bind(now).execute(pool).await.ok();
            println!("已授权 {}", device_id);
        }
        _ => eprintln!("设备未找到"),
    }
}

async fn cmd_list_records(pool: &SqlitePool, device_id: Option<String>, limit: i64) {
    let rows = if let Some(ref did) = device_id {
        sqlx::query(
            "SELECT record_id, source_device_id, operation, sync_version, server_updated_at FROM sync_records WHERE source_device_id = ? ORDER BY server_updated_at DESC LIMIT ?"
        ).bind(did).bind(limit).fetch_all(pool).await.unwrap()
    } else {
        sqlx::query(
            "SELECT record_id, source_device_id, operation, sync_version, server_updated_at FROM sync_records ORDER BY server_updated_at DESC LIMIT ?"
        ).bind(limit).fetch_all(pool).await.unwrap()
    };
    if rows.is_empty() { println!("No records"); return; }
    for r in &rows {
        println!("{:38} {:38} {:>8}  v{}  {}",
            r.get::<String,_>(0), r.get::<String,_>(1), r.get::<String,_>(2),
            r.get::<i32,_>(3), r.get::<i64,_>(4));
    }
}

async fn cmd_stats(pool: &SqlitePool) {
    let devices: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM devices").fetch_one(pool).await.unwrap_or(0);
    let authed: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM devices WHERE is_authorized = 1").fetch_one(pool).await.unwrap_or(0);
    let records: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM sync_records").fetch_one(pool).await.unwrap_or(0);
    let keys: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM data_keys").fetch_one(pool).await.unwrap_or(0);
    println!("devices: {} | authed: {} | sync_records: {} | data_keys: {}", devices, authed, records, keys);
}