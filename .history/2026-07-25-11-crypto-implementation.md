# 11 — 安全架构实现 — 2026-07-25

## 概述
实现主密码 + RSA-3072 + AES-GCM 加密架构，覆盖首次设置、数据加密存储、解密显示全流程。

## 新增文件

| 文件 | 说明 |
|------|------|
| `crypto/CryptoManager.kt` | PBKDF2(600k次) → AES-256-GCM 加解密 + RSA-3072 密钥对生成/序列化 |
| `crypto/CryptoSession.kt` | 单例持有当前会话的 AES 密钥，提供便捷 encrypt/decrypt 方法 |
| `SetMasterPasswordScreen.kt` | 首次启动设置主密码 UI：两次输入 + 密码提示(可选) + 强度建议 |

## 修改文件

| 文件 | 改动 |
|------|------|
| `Pwd.kt` | `PasswdEntity` 删除 `passwd` 字段；新增 `KeyPairEntity`(key_pair 表) |
| `PwdDB.kt` | entities 添加 KeyPairEntity；version 2→3 |
| `PwdDao.kt` | 新增 `KeyPairDao` |
| `MainActivity.kt` | 首次启动检测(查 key_pair 表)；未设置显示 SetMasterPasswordScreen |
| `CreatePasswordScreen.kt` | `passwd` + `notes` 加密存储为 `iv:ciphertext` 格式 |
| `PasswordDetailDialog.kt` | 解密显示 `passwd` + `notes` |

## 删除文件
- `EditPwdDialog.kt` (已被 PasswordDetailDialog 替代，且引用旧字段)
- `InfoDialog.kt` (已被 PasswordDetailDialog 替代)

## 加密格式
```
encryptedPasswd = base64(iv) + ":" + base64(ciphertext)
iv            = base64(iv)
notes         = base64(iv) + ":" + base64(ciphertext)  (如非空)
```

## 待完成 (Keystore 集成)

当前 `CryptoSession.key` 未在解锁时自动加载。后续需要：
- 生物识别通过后，要求用户输入主密码（如已过有效期）
- 验证 magic text 解密 → 派生 AES key → 赋值 `CryptoSession.key`
- 或集成 Android Keystore 免去重复输入

## 验证状态

- [x] 编译通过
