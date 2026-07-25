# 14 — Phase 1 加密重构完整实施 — 2026-07-25

## 概述
按 `.design/加密流程.md` 方案完整重写加解密架构：三层密钥体系、统一加密格式、新数据表、Keystore 集成。

## 密钥体系变化

```
旧: 主密码 → K_master → 直接用 K_master 加密密码记录
新: 主密码 → K_master → 设备私钥 → Data Key → 加密密码记录
```

## 文件变更

### 数据模型
- `Pwd.kt`: `PasswdEntity`→`PasswordEntry` (UUID主键, `encryptedPassword`, `encryptedNotes`, sync字段); 新增 `DeviceEntity` 表; `KeyPairEntity` 去掉 `publicKey` 列
- `PwdDao.kt`: 新实体DAO; 表名 `passwd`→`passwords`; `passwdId`→`passwordId`; 新增 `DeviceDao`
- `PwdDB.kt`: entities 添加 `DeviceEntity`; version 3→4; 暴露 `DeviceDao()`

### 加密层
- `CryptoManager.kt`: + `rsaEncrypt`/`rsaDecrypt` (OAEP-SHA256); + `generateDataKey`/`rawToAesKey`
- `KeystoreHelper.kt` (新): Android Keystore 硬件保护 AES-256-GCM 密钥
- `SessionManager.kt` (新): 替代 `CryptoSession`; 持有 Data Key; `unlock()` 完成 K_master派生→Magic Text验证→私钥解密→Data Key解密→K_master焚毁; 统一 `encrypt()`/`decrypt()` 接口

### UI层
- `SetMasterPasswordScreen.kt`: 新流程 — 生成RSA密钥对 + Data Key → RSA加密Data Key → 存devices表
- `CreatePasswordScreen.kt`: `PasswdEntity`→`PasswordEntry`; `SessionManager.encrypt()`; UUID id; deviceId字段
- `PasswordDetailDialog.kt`: `SessionManager.decrypt()`; 统一解密格式 `base64(iv):base64(ct)`
- `HomeScreen.kt`, `SearchScreen.kt`, `MainScreen.kt`: `PasswdEntity`→`PasswordEntry`
- `MainActivity.kt`: `CryptoSession`→`SessionManager`
- `PwdViewModel.kt`, `PwdRepository.kt`: 所有 `PasswdEntity`→`PasswordEntry`; Long id→String id

### 删除
- `CryptoSession.kt` (被 SessionManager 取代)
- `EditPwdDialog.kt`, `InfoDialog.kt` (不再使用)

## 验证状态

- [x] 编译通过 (`./gradlew assembleDebug`)
