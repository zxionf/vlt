# 23 — vlt CLI upload/download 完成 — 2026-07-26

## 概述
完成 `vlt upload` / `vlt download` 网络同步功能。

## 用法

```bash
# 默认服务器 127.0.0.1:8080
vlt upload | vlt download

# 指定服务器
VLT_SERVER=http://myserver:8080 vlt upload
vlt -s http://myserver:8080 download
```

服务器地址优先级：`-s/--server` > `VLT_SERVER` 环境变量 > `http://127.0.0.1:8080`

## 改动

| 文件 | 改动 |
|------|------|
| `cli/Cargo.toml` | + `reqwest 0.12` (blocking + json) |
| `cli/src/main.rs` | `cmd_upload` + `cmd_download` 完整实现；新增 `-s/--server` 参数 |

## 流程

- **upload**: 读取本地全量 → 逐条 `POST /api/sync/push`（encrypted_blob 内嵌完整加密字段）→ 统计成功/失败
- **download**: `GET /api/sync/pull/0` → 解析 `encrypted_blob` JSON → 跳过已存在 ID → 写入本地

## 验证状态

- [x] `cargo build --release` 通过
