use std::process::Command;
use std::io::Write;

struct TestContext {
    db_path: String,
    _tempdir: tempfile::TempDir,
}

impl TestContext {
    fn new() -> Self {
        let dir = tempfile::tempdir().expect("create temp dir");
        let db_path = dir.path().join("vault.db").to_string_lossy().to_string();
        Self { _tempdir: dir, db_path }
    }

    /// 使用 --password 参数的非交互模式
    fn run(&self, args: &[&str], password: &str) -> std::process::Output {
        Command::new(env!("CARGO_BIN_EXE_vlt"))
            .args(args)
            .arg("-D").arg(&self.db_path)
            .arg("--password").arg(password)
            .output()
            .expect("spawn failed")
    }

    /// 使用管道给 stdin（用于 init 等需要 rpassword 输入的情况）
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
}

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

    let out = ctx.run(&["get", &id_prefix], "123456");
    let stdout = String::from_utf8_lossy(&out.stdout);
    assert!(stdout.contains("Gmail"), "get failed: {}", stdout);
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
    assert!(content.contains("Foo"), "export file lacks Foo");
    assert!(content.contains("encrypted_password"), "export file lacks encrypted_password");
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

    let out = ctx.run(&["get", &id_prefix], "asdf");
    let stdout = String::from_utf8_lossy(&out.stdout);
    assert!(stdout.contains("Renamed"), "get after edit: stdout={} stderr={}", stdout, String::from_utf8_lossy(&out.stderr));
}