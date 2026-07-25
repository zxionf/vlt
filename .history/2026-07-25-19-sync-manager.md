# 19 — Phase 3 Android 端 SyncManager + 权限 — 2026-07-25

## 概述
创建 `SyncManager.kt` 实现 Android 端网络同步，纯 `HttpURLConnection` 实现无额外依赖。

## SyncManager API

| 方法 | 说明 |
|------|------|
| `registerDevice(deviceId, name, pubKey, encDataKey)` | POST /api/devices/register |
| `getDevice(deviceId)` | GET /api/devices/{id} |
| `getPendingDevices()` | GET /api/devices/pending |
| `authorizeDevice(fromId, toId, name, encKey)` | POST /api/devices/authorize |
| `pushRecords(entries: List<PasswordEntry>)` | POST /api/sync/push |
| `pullRecords(since: Long)` | GET /api/sync/pull/{since} |
| `healthCheck()` | GET /api/health |

## 安全设计

- 传输层：HTTP 明文传输（内网环境，加密数据已由 AES-256-GCM 保护）
- 所有 password/notes 字段在本地已加密，SyncManager 不做加解密，仅传输
- 使用 `Result<T>` 返回错误而非异常抛

## 配套修改

- `AndroidManifest.xml`：添加 `INTERNET` 权限 + `usesCleartextTraffic=true`

## 文件

| 文件 | 状态 |
|------|------|
| `SyncManager.kt` | 新增 158 行 |
| `AndroidManifest.xml` | 修改 |
| `BackupHelper.kt` | 上一轮已完成 |

## 验证状态

- [x] Android 编译通过
- [ ] 服务器启动 + 设备注册 E2E 测试
