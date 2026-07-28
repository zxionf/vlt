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
    AddDevice {
        #[arg(short = 'f', long)]
        file: Option<String>,
        #[arg(short = 'k', long)]
        key: Option<String>,
        #[arg(short, long)]
        sig: String,
        #[arg(short, long, default_value = "unknown")]
        name: String,
    },
    ListDevices,
    ListRecords { #[arg(short)] device_id: Option<String>, #[arg(short, long, default_value = "20")] limit: i64 },
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
        Commands::AddDevice { file, key, sig, name } => cmd_add_device(&pool, file, key, &sig, &name).await,
        Commands::ListDevices => cmd_list_devices(&pool).await,
        Commands::ListRecords { device_id, limit } => cmd_list_records(&pool, device_id, limit).await,
        Commands::Stats => cmd_stats(&pool).await,
    }
}

async fn cmd_add_device(pool: &SqlitePool, pubkey_file: Option<String>, pubkey_str: Option<String>, sig: &str, name: &str) {
    use base64::Engine;
    let pub_key = match (pubkey_file, pubkey_str) {
        (Some(f), _) => match std::fs::read_to_string(&f) {
            Ok(s) => s.trim().to_string(),
            Err(e) => { eprintln!("读取公钥文件失败: {e}"); return; }
        },
        (_, Some(s)) => s.trim().to_string(),
        (None, None) => { eprintln!("请用 -f 指定公钥文件或 -k 输入公钥"); return; }
    };
    if pub_key.is_empty() || sig.is_empty() { eprintln!("公钥或签名为空"); return; }

    let device_id = uuid::Uuid::new_v4().to_string();
    let dev_bytes = device_id.as_bytes();

    // 验证签名
    if let Err(e) = (|| -> Result<(), String> {
        let pk_bytes = base64::engine::general_purpose::STANDARD.decode(&pub_key).map_err(|e| format!("公钥解码: {e}"))?;
        let pk_arr: [u8; 32] = pk_bytes[..32].try_into().map_err(|_| "无效公钥长度".to_string())?;
        let vk = ed25519_dalek::VerifyingKey::from_bytes(&pk_arr).map_err(|e| format!("公钥: {e}"))?;
        let sig_bytes = base64::engine::general_purpose::STANDARD.decode(sig).map_err(|e| format!("签名解码: {e}"))?;
        let sig_arr: [u8; 64] = sig_bytes[..64].try_into().map_err(|_| "无效签名长度".to_string())?;
        vk.verify_strict(dev_bytes, &ed25519_dalek::Signature::from_bytes(&sig_arr)).map_err(|e| format!("签名: {e}"))?;
        Ok(())
    })() {
        eprintln!("签名验证失败: {e}"); return;
    }

    match sqlx::query(
        "INSERT INTO devices (device_id, device_name, public_key, signature) VALUES (?, ?, ?, ?)"
    ).bind(&device_id).bind(name).bind(&pub_key).bind(sig).execute(pool).await {
        Ok(_) => println!("已添加设备:\n  ID:   {device_id}\n  名称: {name}\n  公钥: {pub_key}"),
        Err(e) => eprintln!("添加失败: {e}"),
    }
}

async fn cmd_list_devices(pool: &SqlitePool) {
    let rows = sqlx::query("SELECT device_id, device_name FROM devices ORDER BY device_id")
        .fetch_all(pool).await.unwrap();
    if rows.is_empty() { println!("暂无设备"); return; }
    println!("{:<38} {:<20}", "device_id", "device_name");
    println!("{}", "-".repeat(60));
    for r in &rows {
        println!("{:<38} {:<20}", r.get::<String,_>(0), r.get::<String,_>(1));
    }
}

async fn cmd_list_records(pool: &SqlitePool, device_id: Option<String>, limit: i64) {
    let rows = if let Some(ref did) = device_id {
        sqlx::query("SELECT record_id, source_device_id, operation, sync_version, server_updated_at FROM sync_records WHERE source_device_id = ? ORDER BY server_updated_at DESC LIMIT ?")
            .bind(did).bind(limit).fetch_all(pool).await.unwrap()
    } else {
        sqlx::query("SELECT record_id, source_device_id, operation, sync_version, server_updated_at FROM sync_records ORDER BY server_updated_at DESC LIMIT ?")
            .bind(limit).fetch_all(pool).await.unwrap()
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
    let records: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM sync_records").fetch_one(pool).await.unwrap_or(0);
    println!("devices: {} | sync_records: {}", devices, records);
}