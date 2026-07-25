# 18 — 完善 Phase 3（Rust 服务器）+ Phase 4（BackupHelper）— 2026-07-25

## Phase 3 — Rust 服务器完善

### Rust 编译问题修复

| 问题 | 修复 |
|------|------|
| `main.rs` 未使用的 imports (`HttpResponse`, `middleware`, `serde`, `Mutex`) | 删除 |
| `models.rs` 字段未使用警告 (`from_device_id`) | 加 `#[allow(dead_code)]` |
| `models.rs` `impl std::fmt::Debug` 冲突（宏展开） | 移除手动 Debug impl，依赖 derive |
| `db.rs` `sqlx::query_as` 需要 derive `sqlx::FromRow` | 添加 derive |
| `routes.rs` `sqlx::query!` 编译时报错（SQL 字符串无法静态检查） | 改用 `sqlx::query` + `Row::get()` 动态取值 |
| `routes.rs` 缺少 `use sqlx::Row` | 添加 |
| `routes.rs` `get::<i32,_>().unwrap_or_default()` / `get::<i64,_>().unwrap_or_default()` 类型推断失败 | 改为直接 `get::<i32,_>()` / `get::<i64,_>()`（不用 unwrap） |

### 服务器 API 确认

```
POST   /api/devices/register      — 注册设备
GET    /api/devices/{id}          — 查询设备
GET    /api/devices/pending       — 待授权设备
POST   /api/devices/authorize     — 授权设备
POST   /api/sync/push            — 批量上传
GET    /api/sync/pull/{since}    — 增量拉取
GET    /api/health               — 健康检查
```

### 数据库表

| 表 | 字段 |
|----|------|
| `devices` | id TEXT PK, device_name, public_key, encrypted_data_key, is_authorized, created_at |
| `pending_authorizations` | id INTEGER PK, from_device_id, to_device_id, from_device_name, encrypted_data_key, created_at |
| `password_records` | id TEXT, device_id, title, username, encrypted_data, encrypted_notes, url, created_device_id, last_modified_device_id, created_at, updated_at, sync_version, is_deleted |

---

## Phase 4 — BackupHelper

### 新文件 `crypto/BackupHelper.kt`

```
BackupHelper.export(context, outputFile)
  → 读所有 PasswordEntry + TagJoin
  → JSON 序列化
  → K_master 加密
  → 写 .pwdbackup 文件

BackupHelper.import(context, inputFile)
  → 读 .pwdbackup 文件
  → K_master 解密
  → JSON 反序列化
  → 批量写入 Room DB
```

---

## 验证状态

- [x] Android 编译通过
- [x] Rust `cargo check` 通过（0 errors, 0 warnings）
