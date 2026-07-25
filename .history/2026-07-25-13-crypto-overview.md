# 13 — 安全与加解密流程全览 — 2026-07-25

## 1. 加密体系总览

```
┌─────────────────────────────────────────────────────┐
│                    算法与参数                          │
├──────────────┬──────────────────────────────────────┤
│ 密码派生      │ PBKDF2WithHmacSHA512, 600,000 迭代     │
│ 对称加密      │ AES-256-GCM, 128-bit tag, 12-byte IV  │
│ 非对称加密    │ RSA-3072（已生成，尚未用于加密）          │
│ 主密码验证    │ Magic Text: "PWD_MASTER_VERIFY_OK"    │
│ IV 生成       │ SecureRandom, 每次加密独立随机         │
└──────────────┴──────────────────────────────────────┘
```

## 2. 数据库存储的加密字段

### `key_pair` 表（主密码相关）

| 字段 | 内容 | 加密状态 |
|------|------|----------|
| `salt` | PBKDF2 盐值 (32 bytes) | 明文（盐可以公开）|
| `magicTextIv` | Magic Text 加密用的 IV | 明文 |
| `magicTextCipher` | `"PWD_MASTER_VERIFY_OK"` 的密文 | AES-GCM 加密 |
| `publicKey` | RSA-3072 公钥 Base64 | 明文（公钥可以公开）|
| `encryptedPrivateKey` | RSA 私钥密文 | AES-GCM 加密 |
| `privateKeyIv` | RSA 私钥加密用的 IV | 明文 |
| `passwordHint` | 用户密码提示 | 明文 |

### `passwd` 表（密码记录）

| 字段 | 内容 | 加密状态 |
|------|------|----------|
| `title`, `username`, `url` | 明文信息 | 明文存储 |
| `encryptedPasswd` | `base64(iv):base64(cipher)` | AES-GCM 加密 |
| `iv` | 加密用 IV | 明文（AES-GCM 允许 IV 公开）|
| `notes` | `base64(iv):base64(cipher)` 或 null | AES-GCM 加密 |

> **注意**：`encryptedPasswd` 和 `iv` 是独立的两个列，但同时 `encryptedPasswd` 内部又含 IV。存在冗余 — 同一 IV 存了两份。

## 3. 首次设置流程（SetMasterPasswordScreen）

```
用户输入主密码 + 确认 + 密码提示(可选)
         │
         ▼
┌─────────────────────────────────────┐
│ 1. PBKDF2 派生                      │
│    password + salt → AES-256 key    │
│    salt = SecureRandom(32 bytes)    │
└──────────────┬──────────────────────┘
               │
         ┌─────┴─────┐
         ▼           ▼
    ┌─────────┐  ┌──────────────┐
    │ 保存 AES │  │ 生成 RSA-3072 │
    │ key 到   │  │ 密钥对        │
    │ Crypto-  │  └──────┬───────┘
    │ Session  │         ▼
    └─────────┘  ┌──────────────────┐
                 │ 用 AES key 加密   │
                 │ RSA 私钥          │
                 └────────┬─────────┘
                          ▼
                 ┌──────────────────┐
                 │ 加密 Magic Text   │
                 │ 用于验证密码正确性  │
                 └────────┬─────────┘
                          ▼
                 ┌──────────────────┐
                 │ 全部存入 key_pair │
                 │ 表                 │
                 └────────┬─────────┘
                          ▼
                      onComplete()
```

## 4. 密码记录保存流程（CreatePasswordScreen）

```
用户输入 title + username + passwd + url + notes + tags
         │
         ▼
┌──────────────────────────────────────────────┐
│ CryptoSession.encrypt(passwd)                 │
│ → 生成随机 IV (12 bytes)                      │
│ → AES-256-GCM 加密                            │
│ → 得到 iv + ciphertext                        │
│                                              │
│ encryptedPasswd = base64(iv):base64(cipher)   │
│ iv = base64(iv)  ← 冗余，同一 IV 存两份        │
│                                              │
│ 同理加密 notes（非空时）                        │
└──────────────┬───────────────────────────────┘
               ▼
        PwdViewModel.addItem() / updateItem()
               ▼
           Room 写入 passwd 表
```

## 5. 密码记录解密流程（PasswordDetailDialog）

```
CryptoSession.decrypt(iv字段, encryptedPasswd字段)
         │
         ▼
┌─────────────────────────────────────┐
│ base64ToBytes(iv) → IV              │
│ base64ToBytes(encryptedPasswd) → CT │
│ （注意：encryptedPasswd 是完整密文，  │
│  不是 "iv:ct" 格式切分）             │
│ decrypt(IV, CT, CryptoSession.key)  │
│ → 明文密码                           │
└─────────────────────────────────────┘
```

## 6. 后续启动解锁流程（MainActivity）

```
App 启动
   │
   ├── DB 有 key_pair 记录? ─── 否 ──→ SetMasterPasswordScreen（首次）
   │
   是
   │
   ▼
onResume → lock() → 显示锁屏
   │
   ▼
点击"解锁" → 生物识别 (BiometricPrompt)
   │
   ├── 成功 → needsPasswordInput = true → 弹出主密码输入框
   │                                              │
   │                                              ▼
   │                              CryptoSession.verifyAndLoad(password)
   │                                              │
   │                                    ┌─────────┴─────────┐
   │                                    正确                  错误
   │                                    │                    │
   │                                    ▼                    ▼
   │                              key = derivedKey       显示"密码错误"
   │                              unlock()
   │
   ├── 无硬件/未录入 → 降级：直接 unlock()
   │                    (⚠️ 但 key 仍为 null!)
   │
   └── 失败 → 留在锁屏
```

## 7. 已实现功能

- [x] PBKDF2 + 随机盐 → AES-256 密钥派生
- [x] AES-256-GCM 加密/解密 passwd 和 notes
- [x] Magic Text 验证主密码正确性
- [x] RSA-3072 密钥对生成与存储（公钥明文，私钥 AES 加密）
- [x] 首次启动主密码设置 UI
- [x] 生物识别 + 主密码双重验证解锁
- [x] `CryptoSession` 单例保存会话密钥

## 8. 待完善 / 已知风险

| 问题 | 严重度 | 说明 |
|------|--------|------|
| **降级路径无密钥** | 🔴 高 | 无生物识别设备时直接 unlock()，但 CryptoSession.key = null，加密操作会 NPE |
| **encryptedPasswd + iv 冗余** | 🟡 中 | `encryptedPasswd` 已含 base64(iv):base64(ct)，`iv` 列存储相同 IV |
| **RSA 密钥未使用** | 🟡 中 | RSA-3072 公/私钥已生成但未参与任何数据的加解密（预留多端同步）|
| **应用被杀后 key 丢失** | 🟡 中 | `CryptoSession.key` 存内存，进程被杀后需重新验证主密码 |
| **密码存储两份格式不一致** | 🟡 中 | passwd 的 `encryptedPasswd` 和 `iv` 分开存，但 notes 的 `encrypted` 内嵌 IV |
| **主密码无强度校验** | 🟢 低 | 仅检查 ≥ 6 位，无 zxcvbn 等强度评估 |
| **Android Keystore 未集成** | 🟢 低 | 当前 AES key 存在进程内存，尚未用 Android KeyChain 硬件保护 |
| **密钥轮换无支持** | 🟢 低 | 用户无法更换主密码（需重新加密所有数据）|

## 9. 涉及源文件

| 文件 | 职责 |
|------|------|
| `crypto/CryptoManager.kt` | 底层算法：PBKDF2、AES-GCM、RSA、Base64 |
| `crypto/CryptoSession.kt` | 会话密钥持有 + verifyAndLoad |
| `Pwd.kt` | `KeyPairEntity` 定义 key_pair 表 |
| `ui/layout/SetMasterPasswordScreen.kt` | 首次设置主密码 UI + 密钥生成 |
| `ui/layout/CreatePasswordScreen.kt` | 加密保存密码记录 |
| `ui/component/PasswordDetailDialog.kt` | 解密显示密码 |
| `MainActivity.kt` | 启动检测、锁定/解锁流程、主密码验证弹窗 |
