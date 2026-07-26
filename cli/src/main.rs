mod crypto;
mod db;

use clap::{Parser, Subcommand};
use db::Database;
use std::path::PathBuf;

/// vlt — 跨平台密码管理器 CLI
#[derive(Parser)]
#[command(name = "vlt", version, about)]
struct Cli {
    /// 数据库文件路径（默认 ~/.vlt/vault.db）
    #[arg(short = 'D', long, default_value = ".")]
    db: PathBuf,

    /// 服务器地址
    #[arg(short = 's', long = "server", default_value = "")]
    server: String,

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

    /// 注册
    Regist,

    /// 同步到服务器
    Upload,

    /// 从服务器下载
    Download,
}

fn default_db_path() -> PathBuf {
    dirs_first().unwrap_or_else(|| PathBuf::from(".")).join(".vlt")
}

fn dirs_first() -> Option<PathBuf> {
    std::env::var("HOME").or_else(|_| std::env::var("USERPROFILE")).ok().map(PathBuf::from)
}

fn main() {
    let cli = Cli::parse();
    // 服务器地址优先级：命令行 > 环境变量 > 默认
    let server = if !cli.server.is_empty() { cli.server.clone() }
    else { std::env::var("VLT_SERVER").unwrap_or_else(|_| "http://127.0.0.1:8080".to_string()) };

    let db_path = if cli.db.to_string_lossy() == "." { default_db_path().join("vault.db") } else { cli.db.clone() };
    if let Some(parent) = db_path.parent() { std::fs::create_dir_all(parent).ok(); }
    let mut db = Database::open(&db_path).expect("无法打开数据库");

    let needs_unlock = matches!(cli.command, Commands::Add { .. } | Commands::List | Commands::Get { .. } | Commands::Edit { .. } | Commands::Upload | Commands::Delete { .. } | Commands::Download);
    if needs_unlock {
        if !db.is_initialized() { eprintln!("未初始化，请先运行: vlt init"); return; }
        let pass = rpassword::prompt_password("主密码: ").unwrap_or_default();
        // if !crypto::verify_and_load(&mut db, &pass) { eprintln!("主密码错误"); return; }
        match crypto::verify_and_load(&mut db, &pass) {
            Ok(b) => {},
            Err(e) => {eprint!("主密码错误"); return;},
        }
    }

    match cli.command {
        Commands::Init => cmd_init(&mut db),
        Commands::Add { title, username, passwd, url, notes } => cmd_add(&mut db, title, username, passwd, url, notes),
        Commands::List => cmd_list(&db),
        Commands::Get { id_prefix } => cmd_get(&db, &id_prefix),
        Commands::Delete { id_prefix } => cmd_delete(&mut db, &id_prefix),
        Commands::Edit { id_prefix, title, username, passwd, url, notes } => cmd_edit(&mut db, &id_prefix, title, username, passwd, url, notes),
        Commands::Test => cmd_test(&server),
        Commands::Regist => cmd_regist(&db, &server),
        Commands::Upload => cmd_upload(&db, &server),
        Commands::Download => cmd_download(&mut db, &server),
    }
}

fn cmd_init(db: &mut Database) {
    if db.is_initialized() { println!("已初始化"); return; }
    let pw1 = rpassword::prompt_password("设置主密码: ").unwrap_or_default();
    let pw2 = rpassword::prompt_password("确认主密码: ").unwrap_or_default();
    if pw1 != pw2 { eprintln!("两次输入不一致"); return; }
    let hint = dialoguer::Input::<String>::new().with_prompt("密码提示（可选）").allow_empty(true).interact_text().unwrap_or_default();
    println!("正在初始化...");
    match crypto::initialize_vault(db, &pw1, &hint) {
        Ok(_) => println!("初始化完成"),
        Err(e) => {
            println!("初始化失败");
            eprintln!("{}", e);
        }
    };
}

fn cmd_add(db: &mut Database, title: Option<String>, username: Option<String>, passwd: Option<String>, url: Option<String>, notes: Option<String>) {
    if !db.is_initialized() { eprintln!("未初始化"); return; }
    let prompt = |label: &str| dialoguer::Input::<String>::new().with_prompt(label).interact_text().unwrap_or_default();
    let title = title.unwrap_or_else(|| prompt("标题"));
    let username = username.unwrap_or_else(|| prompt("用户名"));
    let pwd = passwd.unwrap_or_else(|| rpassword::prompt_password("密码: ").unwrap_or_default());
    let url = url.unwrap_or_else(|| prompt("网址"));
    let notes = notes.unwrap_or_else(|| prompt("备注"));
    let id = uuid::Uuid::new_v4().to_string();
    let enc = crypto::encrypt_field(&pwd);
    let enc_notes = if notes.is_empty() { None } else { Some(crypto::encrypt_field(&notes)) };
    let created_at = chrono::Local::now().timestamp_millis();
    let device_id = db.get_device_info().unwrap().device_id;
    db.insert_passwd(&id, &title, &username, &enc.unwrap(), &enc_notes.unwrap().ok(), &url ,&device_id,&device_id,created_at,created_at);
    println!("已添加: {} [{}]", title, &id[..8]);
}

fn cmd_list(db: &Database) {
    let items = db.list_all();
    if items.is_empty() { println!("暂无记录"); return; }
    for (id, title, username, _, _, _, _) in &items { println!("{:<8}  {:<20}  {}", &id[..8], title, username); }
}

fn cmd_get(db: &Database, prefix: &str) {
    let Some(record) = db.find_by_prefix(prefix) else { eprintln!("未找到"); return; };
    let passwd = crypto::decrypt_field(&record.encrypted_password);
    // let notes = record.encrypted_notes.as_deref().map(|n| crypto::decrypt_field(n)).unwrap_or_default();
    println!("ID:      {}", record.id);
    println!("标题:    {}", record.title);
    println!("用户名:  {}", record.username);
    // println!("密码:    {}", passwd);
    println!("网址:    {}", record.url.as_deref().unwrap_or("-"));
    // println!("备注:    {}", notes);
    println!("创建:    {}", record.created_at);
    println!("更新:    {}", record.updated_at);
}

fn cmd_delete(db: &mut Database, prefix: &str) {
    let Some(record) = db.find_by_prefix(prefix) else { eprintln!("未找到"); return; };
    let confirm = dialoguer::Confirm::new().with_prompt(format!("删除 '{}'？", record.title)).interact().unwrap_or(false);
    if confirm { db.delete(&record.id); println!("已删除"); }
}

fn cmd_edit(db: &mut Database, prefix: &str, title: Option<String>, username: Option<String>, passwd: Option<String>, url: Option<String>, notes: Option<String>) {
    let Some(record) = db.find_by_prefix(prefix) else { eprintln!("未找到"); return; };
    let encrypt_if = |val: &Option<String>| val.as_ref().map(|v| crypto::encrypt_field(v));
    // let new_enc = encrypt_if(&passwd).unwrap_or(record.encrypted_password.clone());
    // let new_notes = if notes.is_some() { encrypt_if(&notes) } else { record.encrypted_notes.clone() };
    // db.update(&record.id, &title.unwrap_or(record.title), &username.unwrap_or(record.username), &new_enc, &new_notes, &url.unwrap_or(record.url.unwrap_or_default()));
    println!("✅ 已更新");
}

fn cmd_test(server: &str) {
    let client = reqwest::blocking::Client::new();
    match client.get(format!("{}/api/health", server)).send() {
        Ok(r) => { 
            match r.text() {
                Ok(body) => eprint!("{}",body),
                Err(e) => { eprintln!("{:#?}",e); }
            }
         }
        Err(e) => { eprintln!("{:#?}",e); }
    }
}

fn cmd_regist(db: &Database, server: &str) {
    let client = reqwest::blocking::Client::new();
    let payload = serde_json::json!({
        "device_id": "cli",
        "device_name": "cli",
        "public_key": "BEGIN",
        "signature": "abc",
    });
    match client.post(format!("{}/api/register", server)).json(&payload).send() {
        Ok(r) => {
            eprint!("{:#?}\n",r);
            eprint!("{}",r.text().unwrap())
        }
        Err(e) => { eprintln!("{:?}",e); }
    }
}

fn cmd_upload(db: &Database, server: &str) {
    let items = db.list_all();
    if items.is_empty() { println!("无记录可上传"); return; }
    let client = reqwest::blocking::Client::new();
    let mut uploaded = 0;
    let mut failed = 0;
    for (id, title, username, enc_pwd, enc_notes, url, _updated_at) in &items {
        let payload = serde_json::json!({
            "record_id": id,
            "device_id": "cli",
            "encrypted_blob": serde_json::json!({
                "title": title, "username": username,
                "encrypted_password": enc_pwd, "encrypted_notes": enc_notes, "url": url,
            }).to_string(),
            "sync_version": 1,
            "client_updated_at": 0_i64,
            "operation": "create",
        });
        match client.post(format!("{}/api/sync/push", server)).json(&serde_json::json!({"records": [payload]})).send() {
            Ok(r) if r.status().is_success() => uploaded += 1,
            Ok(r) => { eprintln!("上传 {} 失败: {}", title, r.status()); failed += 1; }
            Err(e) => { eprintln!("上传 {} 失败: {}", title, e); failed += 1; }
        }
    }
    println!("✅ 上传完成: {} 成功, {} 失败", uploaded, failed);
}

fn cmd_download(db: &mut Database, server: &str) {
    // let client = reqwest::blocking::Client::new();
    // match client.get(format!("{}/api/sync/pull/0", server)).send() {
    //     Ok(resp) => {
    //         let records: serde_json::Value = resp.json().unwrap_or_default();
    //         let empty: Vec<serde_json::Value> = vec![];
    //         let arr = records.as_array().unwrap_or(&empty);
    //         let mut inserted = 0;
    //         for rec in arr {
    //             let blob_str = rec["encrypted_blob"].as_str().unwrap_or("{}");
    //             let blob: serde_json::Value = serde_json::from_str(blob_str).unwrap_or_default();
    //             let id = rec["record_id"].as_str().unwrap_or("");
    //             if !id.is_empty() && db.find_by_prefix(&id[..8]).is_none() {
    //                 db.insert_passwd(id,
    //                     blob["title"].as_str().unwrap_or(""),
    //                     blob["username"].as_str().unwrap_or(""),
    //                     blob["encrypted_password"].as_str().unwrap_or(""),
    //                     &blob["encrypted_notes"].as_str().map(|s| s.to_string()),
    //                     blob["url"].as_str().unwrap_or("")
    //                 );
    //                 inserted += 1;
    //             }
    //         }
    //         println!("✅ 下载完成: {} 条新记录", inserted);
    //     }
    //     Err(e) => eprintln!("下载失败: {}", e),
    // }
}
