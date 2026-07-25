# 16 — 生物识别跳过主密码修复全记录 — 2026-07-25

## 问题演进

### v1（记录 #15）：初始实现
用 `setUserAuthenticationRequired(true)` 的 Keystore 密钥加密 K_master → 真机不支持 → 静默失败 → 生物识别后还是要输入主密码。

### v2：兼容性修复
去掉 `setUserAuthenticationRequired(true)`，改用普通 Keystore 密钥加密 K_master。生物识别安全由系统 `BiometricPrompt` 保证。

### v3（本次修复）：根因定位
真机日志显示 `hasCachedKmaster=false` 且 `KEYSTORE` 标签从未出现。

**根因**：`storeKmForBiometric` 只在 `SetMasterPasswordScreen`（首次设置）中调用。但 APK 更新后数据库已存在 → `needsSetup=false` → 设置页面被跳过 → K_master 从未缓存。

**修复**：在 `SessionManager.unlock()` 密码解锁成功后自动缓存 K_master。

## 修复后流程

```
App启动 → 数据库已存在 → 跳过设置 → 锁屏
  → 生物识别 → hasCachedKmaster=true → 自动解锁 ✅
  
  或（首次/缓存未命中）：
  → 生物识别 → hasCachedKmaster=false → 降级密码输入
  → 输入主密码 → 解锁成功 → storeKmForBiometric 自动写入
  → 下次起生物识别直接解锁
```

## 涉及文件

| 文件 | 改动 |
|------|------|
| `crypto/KeystoreHelper.kt` | `apply()`→`commit()` 同步写入；移除 `setUserAuthenticationRequired`；`storeKmForBiometric`/`getKmFromBiometric` 改用普通 Keystore 密钥 |
| `crypto/SessionManager.kt` | `unlock()` 成功后自动缓存 K_master；添加关键节点 Log |
| `MainActivity.kt` | 生物识别回调加 Log 追踪流程 |
| `SetMasterPasswordScreen.kt` | 错误信息改为 `异常类名: 消息`；Keystore 失败不阻塞；Log.e 打堆栈 |

## 验证状态

- [x] 编译通过
- [ ] 真机：输入一次主密码后，下次生物识别直接解锁（看 `storeKmForBiometric 完成, commit=true`）


## 相关日志过滤

package:mine &(tag:AUTH | tag:SESSION | tag:KEYSTORE | tag:SetMasterPassword)