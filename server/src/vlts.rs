use clap::{Parser, Subcommand};
use sqlx::sqlite::{SqlitePool, SqlitePoolOptions};
use sqlx::Row;

/// vlts — 服务器管理工具
#[derive(Parser)]
#[command(name = "vlts", version, about)]
struct Cli {
    /// 数据库路径
    #[arg(short, long, default_value = "pwd_server.db")]
    db: String,

    #[command(subcommand)]
    command: Commands,
}

#[derive(Subcommand)]
enum Commands {
    /// 列出已注册设备
    ListDevices,
    /// 列出待授权设备
    ListPending,
    /// 授权设备
    Authorize {
        /// 设备 ID
        device_id: String,
    },
    /// 列出同步记录
    ListRecords {
        /// 设备 ID（可选筛选）
        #[arg(short)]
        device_id: Option<String>,
        /// 最大返回条数
        #[arg(short, long, default_value = "20")]
        limit: i64,
    },
    /// 数据库统计
    Stats,
}

#[tokio::main]
async fn main() {
    let cli = Cli::parse();
    let db_url = format!("sqlite:{}?mode=rwc", cli.db);
    let pool = SqlitePoolOptions::new().max_connections(1).connect(&db_url).await
        .expect("无法连接数据库，请确认数据库路径正确且 vltsd 已启动过");

    match cli.command {
        Commands::ListDevices => cmd_list_devices(&pool).await,
        Commands::ListPending => cmd_list_pending(&pool).await,
        Commands::Authorize { device_id } => cmd_authorize(&pool, &device_id).await,
        Commands::ListRecords { device_id, limit } => cmd_list_records(&pool, device_id, limit).await,
        Commands::Stats => cmd_stats(&pool).await,
    }
}

async fn cmd_list_devices(pool: &SqlitePool) {
    let rows = sqlx::query("SELECT device_id, device_name, is_authed, registered_at FROM registered_devices ORDER BY registered_at DESC")
        .fetch_all(pool).await.unwrap();
    if rows.is_empty() { println!("暂无设备"); return; }
    println!("{:<38} {:<20} {:>8}  {}", "device_id", "device_name", "authed", "registered_at");
    println!("{}", "-".repeat(85));
    for r in &rows {
        println!("{:<38} {:<20} {:>8}  {}",
            r.get::<String,_>(0), r.get::<String,_>(1), r.get::<bool,_>(2), r.get::<String,_>(3));
    }
}

async fn cmd_list_pending(pool: &SqlitePool) {
    let rows = sqlx::query("SELECT from_device_id, from_device_name, status FROM pending_authorizations WHERE status = 'pending'")
        .fetch_all(pool).await.unwrap();
    if rows.is_empty() { println!("无待授权设备"); return; }
    for r in &rows {
        println!("{}  {}  {}", r.get::<String,_>(0), r.get::<String,_>(1), r.get::<String,_>(2));
    }
}

async fn cmd_authorize(pool: &SqlitePool, device_id: &str) {
    match sqlx::query("UPDATE registered_devices SET is_authed = 1 WHERE device_id = ?")
        .bind(device_id).execute(pool).await {
        Ok(r) if r.rows_affected() > 0 => {
            sqlx::query("UPDATE pending_authorizations SET status = 'approved' WHERE to_device_id = ?")
                .bind(device_id).execute(pool).await.ok();
            println!("✅ 已授权 {}", device_id);
        }
        _ => eprintln!("❌ 设备未找到"),
    }
}

async fn cmd_list_records(pool: &SqlitePool, device_id: Option<String>, limit: i64) {
    let rows = if let Some(ref did) = device_id {
        sqlx::query("SELECT record_id, device_id, operation, sync_version, server_updated_at FROM sync_records WHERE device_id = ? ORDER BY server_updated_at DESC LIMIT ?")
            .bind(did).bind(limit).fetch_all(pool).await.unwrap()
    } else {
        sqlx::query("SELECT record_id, device_id, operation, sync_version, server_updated_at FROM sync_records ORDER BY server_updated_at DESC LIMIT ?")
            .bind(limit).fetch_all(pool).await.unwrap()
    };
    if rows.is_empty() { println!("暂无记录"); return; }
    for r in &rows {
        println!("{:38} {:38} {:>8}  v{}  {}",
            r.get::<String,_>(0), r.get::<String,_>(1), r.get::<String,_>(2),
            r.get::<i32,_>(3), r.get::<String,_>(4));
    }
}

async fn cmd_stats(pool: &SqlitePool) {
    let devices: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM registered_devices").fetch_one(pool).await.unwrap_or(0);
    let authed: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM registered_devices WHERE is_authed = 1").fetch_one(pool).await.unwrap_or(0);
    let records: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM sync_records").fetch_one(pool).await.unwrap_or(0);
    let pending: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM pending_authorizations WHERE status = 'pending'").fetch_one(pool).await.unwrap_or(0);
    println!("总设备: {} | 已授权: {} | 同步记录: {} | 待授权: {}", devices, authed, records, pending);
}
