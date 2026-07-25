# 21 — 服务端数据库按新设计重写 — 2026-07-26

## 概述
服务端数据库三表按终局设计重写，删除 cli/ 数据库依赖，拆出 routes 模块使 main.rs 更简洁。

## 新表结构

| 表 | 用途 | 关键字段 |
|----|------|----------|
| `registered_devices` | 设备注册/授权 | device_id PK, public_key, signature, is_authed |
| `encrypted_data_keys` | 加密 Data Key 副本 | device_id FK, encrypted_data_key |
| `sync_records` | 加密密码同步记录 | record_id PK, encrypted_blob, sync_version, operation, client/server_updated_at |

与旧表的主要差异：
- `encrypted_data_keys` 从 devices 表中拆出成独立表（1:1）
- `sync_records` 用 `encrypted_blob` 替代分字段加密（由客户端自行打包）
- 新增 `operation` 字段区分 create/update/delete
- 新增 `client_updated_at` + `server_updated_at` 双时间戳用于冲突判定
- 新增 `pending_authorizations` 表用于设备间授权流程
- 新增 `signature` 字段用于设备身份签名

## 文件变更

| 文件 | 改动 |
|------|------|
| `server/src/main.rs` | 无需修改 |
| `server/src/db.rs` | 三张新表 + 一张 pending 表 |
| `server/src/models.rs` | 按新 schema 重构所有请求/响应结构体 |
| `server/src/routes.rs` | 所有 SQL 适配新表名和新字段；拆出路由配置 |
| `cli/` 目录 | 未修改（自身有独立 DB） |

## 验证状态

- [x] `cargo check` 通过
