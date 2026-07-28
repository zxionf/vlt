mod crypto;
mod models;
mod db;
mod query;

use sqlx::sqlite::{SqlitePool, SqlitePoolOptions};
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
    match rpassword::prompt_password(prompt) {
        Ok(s) => s,
        Err(_) => {
            print!("{}", prompt);
            let _ = io::stdout().flush();
            io::stdin().lock().lines().next()
                .and_then(|r| r.ok())
                .unwrap_or_default()
        }
    }
}

#[derive(Parser)]
#[command(name = "vlt", version, about)]
struct Cli {
    #[arg(short = 'D', long, global = true, default_value = ".")]
    db: PathBuf,
    #[arg(short = 's', long = "server", global = true, default_value = "")]
    server: String,
    #[arg(long = "password", global = true)]
    password: Option<String>,
    #[command(subcommand)]
    command: Commands,
}

#[derive(Subcommand)]
enum Commands {
    Init,
    Add {
        #[arg(short = 't', long = "title")] title: Option<String>,
        #[arg(short = 'u', long = "uname")] username: Option<String>,
        #[arg(short = 'p', long = "passwd")] passwd: Option<String>,
        #[arg(short = 'U', long = "url")] url: Option<String>,
        #[arg(short = 'n', long = "notes")] notes: Option<String>,
    },
    List,
    Show { id_prefix: String },
    Rm { id_prefix: String },
    Edit {
        id_prefix: String,
        #[arg(short = 't', long = "title")] title: Option<String>,
        #[arg(short = 'u', long = "uname")] username: Option<String>,
        #[arg(short = 'p', long = "passwd")] passwd: Option<String>,
        #[arg(short = 'U', long = "url")] url: Option<String>,
        #[arg(short = 'n', long = "notes")] notes: Option<String>,
    },
    Info,
    Signup,
    Sync {
        #[command(subcommand)]
        action: SyncAction,
    },
    Export { #[arg(short = 'o', long = "output")] output: Option<PathBuf> },
    Import { #[arg(short = 'f', long = "file")] file: Option<PathBuf> },
}

#[derive(Subcommand)]
enum SyncAction {
    Push,
    Pull,
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
    let pool = SqlitePoolOptions::new().max_connections(1).connect(&format!("sqlite:{}?mode=rwc", db_path.to_string_lossy())).await
        .expect("无法连接数据库");

    db::migrate(&pool).await;

    let needs_unlock = !matches!(cli.command, Commands::Init | Commands::Info);
    if needs_unlock {
        if !query::is_initialized(&pool).await { eprintln!("未初始化，请先运行: vlt init"); return; }
        let pass = cli.password.unwrap_or_else(|| read_pass("主密码: "));
        match crypto::verify_and_load(&pool, &pass).await {
            Ok(true) => {}
            _ => { eprintln!("主密码错误"); return; }
        }
    }

    match cli.command {
        Commands::Init => cmd_init(&pool).await,
        Commands::Add { title, username, passwd, url, notes } => cmd_add(&pool, title, username, passwd, url, notes).await,
        Commands::List => cmd_list(&pool).await,
        Commands::Show { id_prefix } => cmd_show(&pool, &id_prefix).await,
        Commands::Rm { id_prefix } => cmd_rm(&pool, &id_prefix).await,
        Commands::Edit { id_prefix, title, username, passwd, url, notes } => cmd_edit(&pool, &id_prefix, title, username, passwd, url, notes).await,
        Commands::Info => cmd_info(&pool).await,
        Commands::Signup => cmd_signup(&pool).await,
        Commands::Sync { action } => match action {
            SyncAction::Push => cmd_push(&pool, &server).await,
            SyncAction::Pull => cmd_pull(&pool, &server).await,
        },
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
    match crypto::init_vault(pool, &pw1, &hint).await {
        Ok(_) => println!("初始化完成"),
        Err(e) => eprintln!("初始化失败: {e}"),
    }
}

async fn cmd_add(pool: &SqlitePool, title: Option<String>, username: Option<String>, passwd: Option<String>, url: Option<String>, notes: Option<String>) {
    let title = title.unwrap_or_else(|| read_line("标题"));
    let username = username.unwrap_or_else(|| read_line("用户名"));
    let pwd = passwd.unwrap_or_else(|| read_pass("密码: "));
    let url = url.unwrap_or_else(|| read_line("网址"));
    let notes = notes.unwrap_or_else(|| read_line("备注"));
    let id = uuid::Uuid::new_v4().to_string();
    let enc = crypto::encrypt_field(&pwd).unwrap();
    let enc_notes = if notes.is_empty() { None } else { crypto::encrypt_field(&notes).ok() };
    let now = chrono::Utc::now().timestamp_millis();
    query::insert_vault(pool, &id, &title, &username, &enc, enc_notes.as_deref(), &url, now, now).await.ok();
    println!("已添加: {title} [{:.8}]", &id[..8.min(id.len())]);
}

async fn cmd_list(pool: &SqlitePool) {
    let items = query::list_vaults(pool).await;
    if items.is_empty() { println!("暂无记录"); return; }
    for item in &items {
        println!("{:.8}  {:<20}  {}", &item.id[..8.min(item.id.len())], item.title, item.username);
    }
}

async fn cmd_show(pool: &SqlitePool, prefix: &str) {
    let vec = query::find_vault_by_prefix(pool, prefix).await;
    if vec.is_empty() { eprintln!("未找到"); return; }
    let item = &vec[0];
    let passwd = crypto::decrypt_field(&item.enc_password).ok();
    let notes = item.enc_notes.as_deref().map(|n| crypto::decrypt_field(n)).and_then(|r| r.ok());
    println!("ID:        {}", item.id);
    println!("标题:      {}", item.title);
    println!("用户名:    {}", item.username);
    println!("密码:      {}", passwd.unwrap_or_else(|| "解密失败".into()));
    println!("网址:      {}", item.url.as_deref().unwrap_or("-"));
    println!("备注:      {}", notes.unwrap_or_default());
    println!("版本:      v{}", item.version);
    println!("创建:      {}", item.created_at);
    println!("更新:      {}", item.updated_at);
}

async fn cmd_rm(pool: &SqlitePool, prefix: &str) {
    let records = query::find_vault_by_prefix(pool, prefix).await;
    if records.is_empty() { eprintln!("未找到"); return; }
    if records.len() > 1 {
        println!("找到 {} 条匹配:", records.len());
        for r in &records { println!("  {:.8}  {}", &r.id[..8.min(r.id.len())], r.title); }
        return;
    }
    let r = &records[0];
    let confirm = read_line(&format!("确认删除 '{}'？[y/N] ", r.title));
    if confirm.trim().eq_ignore_ascii_case("y") {
        query::delete_vault(pool, &r.id).await;
        println!("已删除");
    }
}

async fn cmd_edit(pool: &SqlitePool, prefix: &str, title: Option<String>, username: Option<String>, passwd: Option<String>, url: Option<String>, notes: Option<String>) {
    let records = query::find_vault_by_prefix(pool, prefix).await;
    if records.is_empty() { eprintln!("未找到"); return; }
    if records.len() > 1 {
        println!("找到 {} 条匹配:", records.len());
        for r in &records { println!("  {:.8}  {}", &r.id[..8.min(r.id.len())], r.title); }
        return;
    }
    let r = &records[0];
    let new_title = title.unwrap_or_else(|| r.title.clone());
    let new_username = username.unwrap_or_else(|| r.username.clone());
    let new_pwd = match passwd {
        Some(p) => crypto::encrypt_field(&p).unwrap(),
        None => r.enc_password.clone(),
    };
    let new_url = url.unwrap_or_else(|| r.url.clone().unwrap_or_default());
    let new_notes: Option<String> = match notes {
        Some(n) if !n.is_empty() => Some(crypto::encrypt_field(&n).unwrap()),
        Some(_) => None,
        None => r.enc_notes.clone(),
    };
    query::update_vault(pool, &r.id, &new_title, &new_username, &new_pwd, new_notes.as_deref(), &new_url).await.ok();
    println!("已更新");
}

async fn cmd_export(pool: &SqlitePool, output: Option<PathBuf>) {
    let items = query::list_vaults(pool).await;
    let json = serde_json::to_string_pretty(&items.iter().map(|v| serde_json::json!({
        "id": v.id, "title": v.title, "username": v.username,
        "enc_password": v.enc_password, "enc_notes": v.enc_notes,
        "url": v.url, "version": v.version, "is_deleted": v.is_deleted,
        "created_at": v.created_at, "updated_at": v.updated_at,
    })).collect::<Vec<_>>()).unwrap_or_default();
    match output {
        Some(p) => { std::fs::write(&p, &json).ok(); println!("已导出到 {}", p.display()); }
        None => println!("{json}"),
    }
}

async fn cmd_import(pool: &SqlitePool, file: Option<PathBuf>) {
    use std::io::Read;
    let json = match file {
        Some(path) => std::fs::read_to_string(&path).unwrap_or_default(),
        None => { let mut s = String::new(); std::io::stdin().read_to_string(&mut s).ok(); s }
    };
    if json.trim().is_empty() { eprintln!("内容为空"); return; }
    let items: Vec<serde_json::Value> = match serde_json::from_str(&json) {
        Ok(v) => v, Err(e) => { eprintln!("JSON 错误: {e}"); return; }
    };
    let now = chrono::Utc::now().timestamp_millis();
    let mut n = 0;
    for item in &items {
        let id = item["id"].as_str().unwrap_or("");
        if id.is_empty() { continue; }
        if !query::find_vault_by_prefix(pool, id).await.is_empty() { continue; }
        let enc_notes = item["enc_notes"].as_str().and_then(|s| if s.is_empty() { None } else { Some(s) });
        query::insert_vault(pool,
            id, item["title"].as_str().unwrap_or(""), item["username"].as_str().unwrap_or(""),
            item["enc_password"].as_str().unwrap_or(""), enc_notes, item["url"].as_str().unwrap_or(""),
            item["created_at"].as_i64().unwrap_or(now), item["updated_at"].as_i64().unwrap_or(now),
        ).await.ok();
        n += 1;
    }
    println!("导入 {n} 条");
}

async fn cmd_signup(pool: &SqlitePool) {
    let cfg = query::get_config(pool).await.unwrap();
    let dev_sig = crypto::sign_msg(cfg.device_id.as_bytes()).unwrap();
    println!("{}", cfg.device_id);
    println!("{}", cfg.pub_key);
    println!("{dev_sig}");
}

async fn cmd_info(pool: &SqlitePool) {
    let cfg = query::get_config(pool).await;
    match cfg {
        Some(c) => {
            println!("Device ID:  {}", c.device_id);
            println!("Name:       {}", c.device_name);
            println!("Public Key: {}", c.pub_key);
        }
        None => eprintln!("未初始化"),
    }
}

async fn cmd_push(pool: &SqlitePool, server: &str) {
    let items = query::list_vaults(pool).await;
    if items.is_empty() { println!("无记录"); return; }
    let cfg = query::get_config(pool).await.unwrap();
    let client = reqwest::Client::new();
    let mut ok = 0;
    for item in &items {
        let blob = serde_json::json!({
            "id": item.id, "title": item.title, "username": item.username,
            "enc_password": item.enc_password, "enc_notes": item.enc_notes,
            "url": item.url,
        });
        let enc_blob = crypto::encrypt_field(&blob.to_string()).unwrap();
        let payload_without_sig = format!(
            "{}|{}|{}|{}|{}|{}|{}",
            item.id, cfg.device_id, enc_blob, "", item.version, item.updated_at, ""
        );
        let signature = crypto::sign_msg(payload_without_sig.as_bytes()).unwrap();
        let payload = serde_json::json!({
            "record_id": item.id,
            "source_device_id": cfg.device_id,
            "encrypted_blob": enc_blob,
            "sync_version": item.version,
            "client_updated_at": item.updated_at,
            "signature": signature,
        });
        match client.post(&format!("{server}/api/sync")).json(&serde_json::json!({"records": [payload]})).send().await {
            Ok(r) if r.status().is_success() => ok += 1,
            Ok(r) => eprintln!("{} 失败: {}", item.title, r.status()),
            Err(e) => eprintln!("{} 失败: {}", item.title, e),
        }
    }
    println!("推送 {ok}/{} 条", items.len());
}

async fn cmd_pull(pool: &SqlitePool, server: &str) {
    let client = reqwest::Client::new();
    match client.get(&format!("{server}/api/sync")).send().await {
        Ok(resp) => {
            let records: Vec<serde_json::Value> = match resp.json().await {
                Ok(v) => v, Err(e) => { eprintln!("解析: {e}"); return; }
            };
            if records.is_empty() { println!("无新记录"); return; }
            let mut n = 0;
            let now = chrono::Utc::now().timestamp_millis();
            for rec in &records {
                let blob_str = rec["encrypted_blob"].as_str().unwrap_or("");
                if blob_str.is_empty() { continue; }
                let blob_json: serde_json::Value = match serde_json::from_str(
                    &crypto::decrypt_field(blob_str).unwrap_or_default()
                ) { Ok(v) => v, Err(_) => continue };
                let id = blob_json["id"].as_str().unwrap_or("");
                if id.is_empty() { continue; }
                if !query::find_vault_by_prefix(pool, id).await.is_empty() { continue; }
                let enc_notes = blob_json["enc_notes"].as_str().and_then(|s| if s.is_empty() { None } else { Some(s) });
                query::insert_vault(pool,
                    id, blob_json["title"].as_str().unwrap_or(""),
                    blob_json["username"].as_str().unwrap_or(""),
                    blob_json["enc_password"].as_str().unwrap_or(""),
                    enc_notes, blob_json["url"].as_str().unwrap_or(""),
                    now, now,
                ).await.ok();
                n += 1;
            }
            println!("拉取 {n} 条");
        }
        Err(e) => eprintln!("拉取失败: {e}"),
    }
}

