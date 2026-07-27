mod crypto;
mod models;
mod db;
mod query;

use sqlx::{sqlite::{SqlitePool, SqlitePoolOptions}};
use clap::{Parser, Subcommand};
use std::path::PathBuf;
use std::io::{self, BufRead, Write};

fn read_line(prompt: &str) -> String {
    match dialoguer::Input::<String>::new().with_prompt(prompt).interact_text() {
        Ok(s) => s,
        Err(_) => {
            print!("{}: ", prompt);
            let _ = io::stdout().flush();
            io::stdin().lock().lines().next()
                .and_then(|r| r.ok())
                .unwrap_or_default()
        }
    }
}

fn read_pass(prompt: &str) -> String {
    // try TTY first, fall back to stdin (for test pipes)
    match rpassword::prompt_password(prompt) {
        Ok(s) => return s,
        Err(_) => {
            print!("{}", prompt);
            let _ = io::stdout().flush();
            io::stdin().lock().lines().next()
                .and_then(|r| r.ok())
                .unwrap_or_default()
        }
    }
}

/// vlt — 跨平台密码管理器 CLI
#[derive(Parser)]
#[command(name = "vlt", version, about)]
struct Cli {
    /// 数据库文件路径（默认 ~/.vlt/vault.db）
    #[arg(short = 'D', long, global = true, default_value = ".")]
    db: PathBuf,

    /// 服务器地址
    #[arg(short = 's', long = "server", global = true, default_value = "")]
    server: String,

    /// 主密码（非交互模式，仅用于测试）
    #[arg(long = "password", global = true)]
    password: Option<String>,

    #[command(subcommand)]
    command: Commands,
}

#[derive(Subcommand)]
enum Commands {
    /// 初始化：设置主密码（首次使用）
    Init,

    /// 添加密码记录
    Add {
        #[arg(short = 't', long = "title")] title: Option<String>,
        #[arg(short = 'u', long = "uname")] username: Option<String>,
        #[arg(short = 'p', long = "passwd")] passwd: Option<String>,
        #[arg(short = 'U', long = "url")] url: Option<String>,
        #[arg(short = 'n', long = "notes")] notes: Option<String>,
    },

    /// 列出所有记录
    List,

    /// 查看单条记录详情
    Get { id_prefix: String },

    /// 删除记录
    Delete { id_prefix: String },

    /// 编辑记录
    Edit {
        id_prefix: String,
        #[arg(short = 't', long = "title")] title: Option<String>,
        #[arg(short = 'u', long = "uname")] username: Option<String>,
        #[arg(short = 'p', long = "passwd")] passwd: Option<String>,
        #[arg(short = 'U', long = "url")] url: Option<String>,
        #[arg(short = 'n', long = "notes")] notes: Option<String>,
    },

    /// test
    Test,

    /// 显示本机设备信息
    Info,

    /// 注册
    Regist,

    /// 同步到服务器
    Upload,

    /// 从服务器下载
    Download,

    /// 已授权设备：授权新设备（拉取公钥，加密 Data Key，上传）
    Authorize {
        /// 目标设备 ID
        target_device_id: String,
    },

    /// 新设备：拉取本机加密的 Data Key 并导入
    SyncKey,

    Export {
        #[arg(short = 'o', long = "output")]
        output: Option<PathBuf>,
    },

    Import {
        #[arg(short = 'f', long = "file")]
        file: Option<PathBuf>,
    },
}

fn default_db_path() -> PathBuf {
    dirs_first().unwrap_or_else(|| PathBuf::from(".")).join(".vlt")
}

fn dirs_first() -> Option<PathBuf> {
    std::env::var("HOME").or_else(|_| std::env::var("USERPROFILE")).ok().map(PathBuf::from)
}

#[tokio::main]
async fn main() {
    let cli = Cli::parse();
    let server = if !cli.server.is_empty() { cli.server.clone() }
    else { std::env::var("VLT_SERVER").unwrap_or_else(|_| "http://127.0.0.1:8080".to_string()) };

    let db_path = if cli.db.to_string_lossy() == "." { default_db_path().join("vault.db") } else { cli.db.clone() };
    if let Some(parent) = db_path.parent() { std::fs::create_dir_all(parent).ok(); }
    let url = format!("sqlite:{}?mode=rwc", db_path.to_string_lossy());
    let pool = SqlitePoolOptions::new().max_connections(1).connect(&url).await
        .expect("无法连接数据库");

    db::migrate(&pool).await;

    let needs_unlock = !matches!(cli.command, Commands::Init | Commands::Test | Commands::Info);
    if needs_unlock {
        if !query::is_initialized(&pool).await { eprintln!("未初始化，请先运行: vlt init"); return; }
        let pass = if let Some(p) = cli.password {
            p
        } else {
            read_pass("主密码: ")
        };
        match crypto::verify_and_load(&pool, &pass).await {
            Ok(true) => {}
            _ => { eprintln!("主密码错误"); return; }
        }
    }

    match cli.command {
        Commands::Init => cmd_init(&pool).await,
        Commands::Add { title, username, passwd, url, notes } => cmd_add(&pool, title, username, passwd, url, notes).await,
        Commands::List => cmd_list(&pool).await,
        Commands::Get { id_prefix } => cmd_get(&pool, &id_prefix).await,
        Commands::Delete { id_prefix } => cmd_delete(&pool, &id_prefix).await,
        Commands::Edit { id_prefix, title, username, passwd, url, notes } => cmd_edit(&pool, &id_prefix, title, username, passwd, url, notes).await,
        Commands::Test => cmd_test(&server),
        Commands::Info => cmd_info(&pool).await,
        Commands::Regist => cmd_regist(&pool, &server).await,
        Commands::Upload => cmd_upload(&pool, &server).await,
        Commands::Download => cmd_download(&pool, &server).await,
        Commands::Authorize { target_device_id } => cmd_authorize(&pool, &server, &target_device_id).await,
        Commands::SyncKey => cmd_sync_key(&pool, &server).await,
        Commands::Export { output } => cmd_export(&pool, output).await,
        Commands::Import { file } => cmd_import(&pool, file).await,
    }
}

async fn cmd_init(pool: &SqlitePool) {
    if query::is_initialized(pool).await { println!("已初始化"); return; }
    let pw1 = read_pass("设置主密码: ");
    let pw2 = read_pass("确认主密码: ");
    if pw1 != pw2 { eprintln!("两次输入不一致"); return; }
    let hint = read_line("密码提示（可选）");
    println!("正在初始化...");
    match crypto::initialize_vault(pool, &pw1, &hint).await {
        Ok(_) => println!("初始化完成"),
        Err(e) => eprintln!("初始化失败: {}", e),
    };
}

async fn cmd_add(pool: &SqlitePool, title: Option<String>, username: Option<String>, passwd: Option<String>, url: Option<String>, notes: Option<String>) {
    let title = title.unwrap_or_else(|| read_line("标题"));
    let username = username.unwrap_or_else(|| read_line("用户名"));
    let pwd = passwd.unwrap_or_else(|| read_pass("密码: "));
    let url = url.unwrap_or_else(|| read_line("网址"));
    let notes = notes.unwrap_or_else(|| read_line("备注"));
    let id = uuid::Uuid::new_v4().to_string();
    let enc = crypto::encrypt_field(&pwd);
    let enc_notes = if notes.is_empty() { None } else {
        match crypto::encrypt_field(&notes) {
            Ok(e) => Some(e),
            Err(e) => { eprintln!("加密备注失败: {}", e); return; }
        }
    };
    let created_at = chrono::Local::now().timestamp_millis();
    let device_id = query::get_device(pool).await.unwrap().device_id;
    query::insert_passwd(pool, &id, &title, &username, &enc.unwrap(), enc_notes.as_deref(), &url, &device_id, &device_id, created_at, created_at, 1, false).await.ok();
    println!("已添加: {} [{}]", title, &id[..8]);
}

async fn cmd_list(pool: &SqlitePool) {
    let items: Vec<models::Password> = query::get_all_passwords(pool).await;
    if items.is_empty() { println!("暂无记录"); return; }
    for item in &items { println!("{:<8}  {:<20}  {}", &item.id[..8], item.title, item.username); }
}

async fn cmd_get(pool: &SqlitePool, prefix: &str) {
    let vec = query::find_by_prefix(pool, prefix).await;
    if vec.is_empty() { println!("未找到匹配的记录"); return; }
    let item = &vec[0];
    let passwd = crypto::decrypt_field(&item.encrypted_password);
    let notes = item.encrypted_notes.as_deref().map(|n| crypto::decrypt_field(n));
    println!("ID:      {}", item.id);
    println!("标题:    {}", item.title);
    println!("用户名:  {}", item.username);
    println!("密码:    {}", passwd.as_deref().unwrap_or("解密失败"));
    println!("网址:    {}", item.url.as_deref().unwrap_or("-"));
    println!("备注:    {}", notes.and_then(|r| r.ok()).unwrap_or_default());
    println!("创建:    {}", item.created_at);
    println!("更新:    {}", item.updated_at);
}

async fn cmd_delete(pool: &SqlitePool, prefix: &str) {
    let records = query::find_by_prefix(pool, prefix).await;
    if records.is_empty() { eprintln!("未找到匹配的记录"); return; }
    if records.len() > 1 {
        println!("找到 {} 条匹配记录:", records.len());
        for r in &records { println!("  {:<8}  {}", &r.id[..8], r.title); }
        return;
    }
    let record = &records[0];
    let confirm = {
        let ans = read_line(&format!("确认删除 '{}'？[y/N] ", record.title));
        ans.trim().eq_ignore_ascii_case("y")
    };
    if confirm {
        query::delete_passwd(pool, &record.id).await;
        println!("已删除: {}", record.title);
    }
}

async fn cmd_edit(pool: &SqlitePool, prefix: &str, title: Option<String>, username: Option<String>, passwd: Option<String>, url: Option<String>, notes: Option<String>) {
    let records = query::find_by_prefix(pool, prefix).await;
    if records.is_empty() { eprintln!("未找到匹配的记录"); return; }
    if records.len() > 1 {
        println!("找到 {} 条匹配记录:", records.len());
        for r in &records { println!("  {:<8}  {}", &r.id[..8], r.title); }
        return;
    }
    let record = &records[0];
    let device_id = query::get_device(pool).await.unwrap().device_id;
    let new_title = title.unwrap_or_else(|| record.title.clone());
    let new_username = username.unwrap_or_else(|| record.username.clone());

    let new_pwd = match passwd {
        Some(p) => match crypto::encrypt_field(&p) {
            Ok(e) => e,
            Err(e) => { eprintln!("加密失败: {}", e); return; }
        },
        None => record.encrypted_password.clone(),
    };

    let new_url = url.unwrap_or_else(|| record.url.clone().unwrap_or_default());

    let new_notes: Option<String> = match notes {
        Some(n) if !n.is_empty() => match crypto::encrypt_field(&n) {
            Ok(e) => Some(e),
            Err(e) => { eprintln!("加密失败: {}", e); return; }
        },
        Some(_) => None,
        None => record.encrypted_notes.clone(),
    };

    query::update_passwd(
        pool, &record.id, &new_title, &new_username, &new_pwd,
        new_notes.as_deref(), &new_url, &device_id,
    ).await.ok();
    println!("已更新: {}", new_title);
}

async fn cmd_export(pool: &SqlitePool, output: Option<PathBuf>) {
    let items: Vec<models::Password> = query::get_all_passwords(pool).await;
    let json = serde_json::to_string_pretty(&items.iter().map(|p| {
        serde_json::json!({
            "id": p.id,
            "title": p.title,
            "username": p.username,
            "encrypted_password": p.encrypted_password,
            "encrypted_notes": p.encrypted_notes,
            "url": p.url,
            "created_device_id": p.created_device_id,
            "last_modified_device_id": p.last_modified_device_id,
            "created_at": p.created_at,
            "updated_at": p.updated_at,
            "sync_version": p.sync_version,
            "is_deleted": p.is_deleted,
        })
    }).collect::<Vec<_>>()).unwrap_or_else(|e| {
        eprintln!("序列化失败: {}", e);
        "[]".to_string()
    });

    match output {
        Some(path) => {
            if let Err(e) = std::fs::write(&path, &json) {
                eprintln!("写入文件失败: {}", e);
            } else {
                println!("已导出到: {}", path.display());
            }
        }
        None => println!("{}", json),
    }
}

async fn cmd_import(pool: &SqlitePool, file: Option<PathBuf>) {
    let json = match file {
        Some(path) => match std::fs::read_to_string(&path) {
            Ok(s) => s,
            Err(e) => { eprintln!("读取文件失败: {}", e); return; }
        }
        None => {
            use std::io::Read;
            let mut s = String::new();
            match std::io::stdin().read_to_string(&mut s) {
                Ok(_) => s,
                Err(e) => { eprintln!("读取 stdin 失败: {}", e); return; }
            }
        }
    };

    let items: Vec<serde_json::Value> = match serde_json::from_str(&json) {
        Ok(v) => v,
        Err(e) => { eprintln!("JSON 解析失败: {}", e); return; }
    };

    let device_id = query::get_device(pool).await.unwrap().device_id;
    let now = chrono::Utc::now().timestamp_millis();
    let mut imported = 0;
    for item in &items {
        let id = item["id"].as_str().unwrap_or("");
        if id.is_empty() { continue; }
        let title = item["title"].as_str().unwrap_or("");
        if title.is_empty() { continue; }
        let existing = query::find_by_prefix(pool, id).await;
        if !existing.is_empty() { continue; }

        let enc_notes = item["encrypted_notes"].as_str().and_then(|s| if s.is_empty() { None } else { Some(s) });

        query::insert_passwd(
            pool,
            id,
            title,
            item["username"].as_str().unwrap_or(""),
            item["encrypted_password"].as_str().unwrap_or(""),
            enc_notes,
            item["url"].as_str().unwrap_or(""),
            item["created_device_id"].as_str().unwrap_or(&device_id),
            &device_id,
            item["created_at"].as_i64().unwrap_or(now),
            item["updated_at"].as_i64().unwrap_or(now),
            item["sync_version"].as_i64().unwrap_or(1) as i32,
            item["is_deleted"].as_bool().unwrap_or(false),
        ).await.ok();
        imported += 1;
    }
    println!("导入完成: {} 条记录", imported);
}

fn cmd_test(server: &str) {
    let client = reqwest::blocking::Client::new();
    match client.get(format!("{}/api/health", server)).send() {
        Ok(r) => {
            match r.text() {
                Ok(body) => print!("{}", body),
                Err(e) => eprintln!("{:#?}", e),
            }
        }
        Err(e) => eprintln!("{:#?}", e),
    }
}

async fn cmd_info(pool: &SqlitePool) {
    let device = query::get_device(pool).await;
    match device {
        Some(d) => {
            println!("设备 ID:   {}", d.device_id);
            println!("设备名称:  {}", d.device_name);
            println!("公钥:      {}", d.public_key);
        }
        None => eprintln!("未初始化，请先运行: vlt init"),
    }
}

async fn cmd_regist(pool: &SqlitePool, server: &str) {
    if !query::is_initialized(pool).await { eprintln!("未初始化，请先运行: vlt init"); return; }
    let client = reqwest::Client::new();
    let device = query::get_device(pool).await.unwrap();

    let signature = match crypto::sign_device(&device.device_id, &device.public_key) {
        Ok(sig) => sig,
        Err(e) => { eprintln!("签名失败: {}", e); return; }
    };

    let payload = serde_json::json!({
        "device_id": &device.device_id,
        "device_name": &device.device_name,
        "public_key": &device.public_key,
        "signature": signature,
    });
    match client.post(format!("{}/api/register", server)).json(&payload).send().await {
        Ok(r) => {
            println!("状态: {}", r.status());
            println!("响应: {}", r.text().await.unwrap_or_default());
        }
        Err(e) => eprintln!("请求失败: {:?}", e),
    }
}

async fn cmd_upload(pool: &SqlitePool, server: &str) {
    let items: Vec<models::Password> = query::get_all_passwords(pool).await;
    if items.is_empty() { println!("无记录可上传"); return; }
    let client = reqwest::blocking::Client::new();
    let device_id = query::get_device(pool).await.unwrap().device_id;
    let mut uploaded = 0;
    let mut failed = 0;
    for item in &items {
        let blob = serde_json::json!({
            "title": item.title,
            "username": item.username,
            "encrypted_password": item.encrypted_password,
            "encrypted_notes": item.encrypted_notes,
            "url": item.url,
            "created_device_id": item.created_device_id,
        });
        let payload = serde_json::json!({
            "record_id": item.id,
            "device_id": device_id,
            "encrypted_blob": blob.to_string(),
            "sync_version": item.sync_version,
            "client_updated_at": item.updated_at,
        });
        match client.post(format!("{}/api/sync/push", server))
            .json(&serde_json::json!({"records": [payload]}))
            .send()
        {
            Ok(r) if r.status().is_success() => uploaded += 1,
            Ok(r) => { eprintln!("上传 {} 失败: {}", item.title, r.status()); failed += 1; }
            Err(e) => { eprintln!("上传 {} 失败: {}", item.title, e); failed += 1; }
        }
    }
    println!("上传完成 {} 成功, {} 失败", uploaded, failed);
}

async fn cmd_download(pool: &SqlitePool, server: &str) {
    let client = reqwest::blocking::Client::new();
    match client.get(format!("{}/api/sync/pull/0", server)).send() {
        Ok(resp) => {
            let records: serde_json::Value = match resp.json() {
                Ok(v) => v,
                Err(e) => { eprintln!("解析响应失败: {}", e); return; }
            };
            let arr = match records.as_array() {
                Some(a) => a,
                None => { eprintln!("响应格式错误"); return; }
            };
            if arr.is_empty() { println!("无新记录"); return; }
            let device_id = query::get_device(pool).await.unwrap().device_id;
            let mut inserted = 0;
            for rec in arr {
                let record_id = rec["record_id"].as_str().unwrap_or("");
                if record_id.is_empty() { continue; }
                let existing = query::find_by_prefix(pool, record_id).await;
                if !existing.is_empty() { continue; }
                let blob_str = rec["encrypted_blob"].as_str().unwrap_or("{}");
                let blob: serde_json::Value = match serde_json::from_str(blob_str) {
                    Ok(v) => v,
                    Err(_) => continue,
                };
                let created_device_id = blob["created_device_id"].as_str().unwrap_or("");
                let now = chrono::Utc::now().timestamp_millis();
                let enc_notes = blob["encrypted_notes"].as_str()
                    .and_then(|s| if s.is_empty() { None } else { Some(s) });
                query::insert_passwd(
                    pool,
                    record_id,
                    blob["title"].as_str().unwrap_or(""),
                    blob["username"].as_str().unwrap_or(""),
                    blob["encrypted_password"].as_str().unwrap_or(""),
                    enc_notes,
                    blob["url"].as_str().unwrap_or(""),
                    if created_device_id.is_empty() { &device_id } else { created_device_id },
                    &device_id,
                    now,
                    now,
                    1,
                    false,
                ).await.ok();
                inserted += 1;
            }
            println!("下载完成: {} 条新记录", inserted);
        }
        Err(e) => eprintln!("下载失败: {}", e),
    }
}

async fn cmd_authorize(pool: &SqlitePool, server: &str, target_device_id: &str) {
    let client = reqwest::Client::new();

    let resp = match client.get(format!("{}/api/devices/pending", server)).send().await {
        Ok(r) => r,
        Err(e) => { eprintln!("获取待授权列表失败: {}", e); return; }
    };

    let body_text = resp.text().await.unwrap_or_default();
    let pending: Vec<serde_json::Value> = match serde_json::from_str(&body_text) {
        Ok(v) => v,
        Err(e) => {
            let snippet = &body_text[..body_text.len().min(500)];
            eprintln!("解析 pending 列表失败: {} — body: {}", e, snippet);
            return;
        }
    };

    let target = pending.iter().find(|d| d["device_id"].as_str() == Some(target_device_id));
    let target_pubkey = match target {
        Some(d) => d["public_key"].as_str().unwrap_or("").to_string(),
        None => {
            eprintln!("目标设备不在待授权队列中");
            eprintln!("请先在设备 B 上执行: vlt init && vlt regist -s {}", server);
            return;
        }
    };

    if target_pubkey.is_empty() {
        eprintln!("无法获取目标设备的公钥");
        return;
    }

    let data_key = match crypto::get_current_data_key() {
        Ok(k) => k,
        Err(e) => { eprintln!("获取 Data Key 失败: {}", e); return; }
    };

    let encrypted = match crypto::encrypt_with_public_key(&target_pubkey, &data_key) {
        Ok(e) => e,
        Err(e) => { eprintln!("加密 Data Key 失败: {}", e); return; }
    };

    let my_device = query::get_device(pool).await.unwrap();
    let payload = serde_json::json!({
        "from_device_id": my_device.device_id,
        "to_device_id": target_device_id,
        "encrypted_data_key": encrypted,
    });

    match client.post(format!("{}/api/authorize", server)).json(&payload).send().await {
        Ok(r) if r.status().is_success() => {
            println!("已授权设备: {}", target_device_id);
        }
        Ok(r) => eprintln!("授权失败: {}", r.status()),
        Err(e) => eprintln!("请求失败: {:?}", e),
    }
}

async fn cmd_sync_key(pool: &SqlitePool, server: &str) {
    let device = query::get_device(pool).await.unwrap();
    let client = reqwest::Client::new();

    let resp = match client.get(format!("{}/api/data-key/{}", server, device.device_id)).send().await {
        Ok(r) => r,
        Err(e) => { eprintln!("请求失败: {}", e); return; }
    };

    let keys: Vec<serde_json::Value> = match resp.json().await {
        Ok(v) => v,
        Err(e) => { eprintln!("解析失败: {}", e); return; }
    };

    if keys.is_empty() { eprintln!("暂无 Data Key 可下载"); return; }

    let enc_dk = keys[0]["encrypted_data_key"].as_str().unwrap_or("");
    if enc_dk.is_empty() { eprintln!("Data Key 为空"); return; }

    query::update_device_data_key(pool, &device.device_id, enc_dk).await.ok();
    println!("Data Key 同步完成");
}

pub async fn create_pool(database_url: &str) -> Result<SqlitePool, sqlx::Error> {
    SqlitePoolOptions::new()
        .max_connections(5)
        .connect(database_url)
        .await
}