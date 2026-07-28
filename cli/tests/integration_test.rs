use std::process::{Command, Child};
use std::io::Write;
use std::thread;
use std::time::Duration;

static SERVER_URL: &str = "http://127.0.0.1:8080";

struct TestContext {
    db_path: String,
    _tempdir: tempfile::TempDir,
    _server: Option<Child>,
}

impl TestContext {
    fn new() -> Self {
        let dir = tempfile::tempdir().expect("create temp dir");
        let db_path = dir.path().join("vault.db").to_string_lossy().to_string();
        Self { _tempdir: dir, db_path, _server: None }
    }

    fn with_server() -> Self {
        let dir = tempfile::tempdir().expect("create temp dir");
        let db_path = dir.path().join("vault.db").to_string_lossy().to_string();
        let srv_db = dir.path().join("srv.db").to_string_lossy().to_string();
        let server = start_server(&srv_db);
        Self { _tempdir: dir, db_path, _server: Some(server) }
    }

    fn srv_db(&self) -> String {
        self._tempdir.path().join("srv.db").to_string_lossy().to_string()
    }

    fn run(&self, args: &[&str], password: &str) -> std::process::Output {
        Command::new(env!("CARGO_BIN_EXE_vlt"))
            .args(args)
            .arg("-D").arg(&self.db_path)
            .arg("--password").arg(password)
            .output()
            .expect("spawn failed")
    }

    fn run_piped(&self, args: &[&str], stdin: &str) -> std::process::Output {
        use std::process::Stdio;
        let mut child = Command::new(env!("CARGO_BIN_EXE_vlt"))
            .args(args)
            .arg("-D").arg(&self.db_path)
            .stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .stderr(Stdio::piped())
            .spawn()
            .expect("spawn failed");
        {
            let mut sin = child.stdin.take().unwrap();
            sin.write_all(stdin.as_bytes()).unwrap();
        }
        child.wait_with_output().unwrap()
    }

    fn run_with_server(&self, args: &[&str], password: &str) -> std::process::Output {
        Command::new(env!("CARGO_BIN_EXE_vlt"))
            .args(args)
            .arg("-D").arg(&self.db_path)
            .arg("-s").arg(SERVER_URL)
            .arg("--password").arg(password)
            .output()
            .expect("spawn failed")
    }
}

impl Drop for TestContext {
    fn drop(&mut self) {
        if let Some(ref mut srv) = self._server {
            srv.kill().ok();
        }
    }
}

fn start_server(db_path: &str) -> Child {
    let server_bin = std::path::Path::new(env!("CARGO_MANIFEST_DIR"))
        .parent().unwrap()
        .join("server/target/debug/vltsd");
    let mut child = Command::new(&server_bin)
        .env("DATABASE_URL", format!("sqlite:{}?mode=rwc", db_path))
        .stdout(std::process::Stdio::null())
        .stderr(std::process::Stdio::null())
        .spawn()
        .unwrap_or_else(|e| panic!("start vltsd failed: {e}"));
    for _ in 0..30 {
        thread::sleep(Duration::from_millis(500));
        if let Ok(o) = &mut Command::new("curl")
            .args(["-s", &format!("{SERVER_URL}/api/health")])
            .output()
        {
            if String::from_utf8_lossy(&o.stdout).contains("ok") {
                return child;
            }
        }
    }
    child.kill().ok();
    panic!("vltsd never became ready");
}

fn query_db(db_path: &str, sql: &str) -> String {
    let output = Command::new("sqlite3")
        .arg("-separator").arg("|")
        .arg(db_path)
        .arg(sql)
        .output()
        .unwrap();
    String::from_utf8_lossy(&output.stdout).trim().to_string()
}

fn exec_db(db_path: &str, sql: &str) {
    Command::new("sqlite3")
        .arg(db_path)
        .arg(sql)
        .output()
        .unwrap();
}

// ─── 基础功能测试 ───

#[test]
fn test_init_and_unlock() {
    let ctx = TestContext::new();
    let out = ctx.run_piped(&["init"], "test123\ntest123\nhint\n");
    let stdout = String::from_utf8_lossy(&out.stdout);
    assert!(!stdout.contains("失败"), "init failed: {}", stdout);
    assert!(stdout.contains("初始化"), "init: {}", stdout);
}

#[test]
fn test_init_add_list_get() {
    let ctx = TestContext::new();
    let out = ctx.run_piped(&["init"], "123456\n123456\nhint\n");
    assert!(out.status.success(), "init failed");

    let out = ctx.run(&["add", "-t", "Gmail", "-u", "user@mail.com", "-p", "secret123", "-U", "https://mail.google.com", "-n", "main"], "123456");
    let stdout = String::from_utf8_lossy(&out.stdout);
    assert!(stdout.contains("Gmail"), "add failed: {}", stdout);

    let out = ctx.run(&["list"], "123456");
    let stdout = String::from_utf8_lossy(&out.stdout);
    assert!(stdout.contains("Gmail"), "list failed: stdout={} stderr={}", stdout, String::from_utf8_lossy(&out.stderr));

    let first_line = stdout.trim().lines().next().unwrap();
    let id_prefix: String = first_line.chars().take(8).collect();
    assert!(!id_prefix.is_empty(), "empty id");

    let out = ctx.run(&["show", &id_prefix], "123456");
    let stdout = String::from_utf8_lossy(&out.stdout);
    assert!(stdout.contains("Gmail"), "show failed: {}", stdout);
}

#[test]
fn test_wrong_password_rejected() {
    let ctx = TestContext::new();
    ctx.run_piped(&["init"], "correct\ncorrect\nh\n");

    let out = ctx.run(&["list"], "wrong");
    let stderr = String::from_utf8_lossy(&out.stderr);
    assert!(stderr.contains("主密码错误"), "wrong pw: stderr={}", stderr);

    let out = ctx.run(&["list"], "correct");
    let stdout = String::from_utf8_lossy(&out.stdout);
    assert!(stdout.contains("暂无记录"), "correct pw: stdout={} stderr={}", stdout, String::from_utf8_lossy(&out.stderr));
}

#[test]
fn test_export() {
    let ctx = TestContext::new();
    ctx.run_piped(&["init"], "pass\npass\nh\n");
    ctx.run(&["add", "-t", "Foo", "-u", "bar", "-p", "pw123", "-U", "", "-n", ""], "pass");

    let outfile = ctx.db_path.replace("vault.db", "export.json");
    let out = ctx.run(&["export", "-o", &outfile], "pass");
    let stdout = String::from_utf8_lossy(&out.stdout);
    assert!(stdout.contains(&outfile), "export: stdout={} stderr={}", stdout, String::from_utf8_lossy(&out.stderr));

    let content = std::fs::read_to_string(&outfile).unwrap();
    assert!(content.contains("Foo"), "export file lackss Foo");
    assert!(content.contains("enc_password"), "export file lackss enc_password");
}

#[test]
fn test_edit() {
    let ctx = TestContext::new();
    ctx.run_piped(&["init"], "asdf\nasdf\nh\n");
    ctx.run(&["add", "-t", "Original", "-u", "user", "-p", "oldpw", "-U", "", "-n", ""], "asdf");

    let out = ctx.run(&["list"], "asdf");
    let stdout = String::from_utf8_lossy(&out.stdout);
    let first_line = stdout.trim().lines().next().unwrap();
    let id_prefix: String = first_line.chars().take(8).collect();

    let out = ctx.run(&["edit", &id_prefix, "-t", "Renamed"], "asdf");
    let stdout = String::from_utf8_lossy(&out.stdout);
    assert!(stdout.contains("已更新"), "edit: stdout={} stderr={}", stdout, String::from_utf8_lossy(&out.stderr));

    let out = ctx.run(&["show", &id_prefix], "asdf");
    let stdout = String::from_utf8_lossy(&out.stdout);
    assert!(stdout.contains("Renamed"), "get after edit: stdout={} stderr={}", stdout, String::from_utf8_lossy(&out.stderr));
}

// ─── 服务端联调测试 ───

#[test]
fn test_server_health() {
    let _ctx = TestContext::with_server();
    let out = Command::new("curl")
        .args(["-s", &format!("{SERVER_URL}/api/health")])
        .output()
        .unwrap();
    let stdout = String::from_utf8_lossy(&out.stdout);
    assert!(stdout.contains("ok"), "health: [{stdout}]");
}

#[test]
fn test_sync_push_and_pull() {
    let ctx = TestContext::with_server();

    // 设备 A init + add
    ctx.run_piped(&["init"], "pw1\npw1\nhint\n");
    ctx.run(&["add", "-t", "A-Google", "-u", "a@a.com", "-p", "a_pass", "-U", "https://a.com", "-n", ""], "pw1");
    ctx.run(&["add", "-t", "A-GitHub", "-u", "g@hub.com", "-p", "g_pass", "-U", "https://github.com", "-n", ""], "pw1");

    // 从cli db中读取设备ID、公钥，用 signup 获取签名后插入 server
    let devid = query_db(&ctx.db_path, "SELECT device_id FROM config;");
    let pubkey = query_db(&ctx.db_path, "SELECT pub_key FROM config;");
    let out = ctx.run(&["signup"], "pw1");
    let signup = String::from_utf8_lossy(&out.stdout);
    let lines: Vec<&str> = signup.lines().collect();
    let sig = lines.get(2).unwrap_or(&"");
    eprintln!("=> device_id={devid} pub_key={pubkey} sig={sig}");
    exec_db(&ctx.srv_db(), &format!(
        "INSERT INTO devices (device_id, device_name, public_key, signature) VALUES ('{devid}', 'test-cli', '{pubkey}', '{sig}');"
    ));

    // push
    let out = ctx.run_with_server(&["sync", "push"], "pw1");
    let s = String::from_utf8_lossy(&out.stdout);
    eprintln!("push: [{s}]");
    assert!(s.contains("2/2"), "push failed: [{s}]");

    // 删除本地
    exec_db(&ctx.db_path, "DELETE FROM vaults WHERE is_deleted = 0;");

    let out = ctx.run(&["list"], "pw1");
    let s = String::from_utf8_lossy(&out.stdout);
    assert!(s.contains("暂无"), "not empty: [{s}]");

    // pull 恢复
    let out = ctx.run_with_server(&["sync", "pull"], "pw1");
    let s = String::from_utf8_lossy(&out.stdout);
    eprintln!("pull: [{s}]");
    assert!(s.contains("拉取 2 条"), "pull failed: [{s}]");

    // 最终验证
    let out = ctx.run(&["list"], "pw1");
    let s = String::from_utf8_lossy(&out.stdout);
    assert!(s.contains("A-Google") && s.contains("A-GitHub"), "list: [{s}]");
}