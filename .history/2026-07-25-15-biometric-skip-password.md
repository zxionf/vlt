# 15 — 生物识别跳过主密码机制 — 2026-07-25

## 概述
设备支持生物识别时，设置主密码后再次解锁只需生物识别，无需输入主密码。不支持则自动降级为主密码输入。

## 机制

```
                                  App 启动
                                     │
                              ┌──────┴──────┐
                              │ 支持生物识别？ │
                              └──────┬──────┘
                          ┌──────────┴──────────┐
                          ▼                     ▼
                    生物识别验证            主密码输入弹窗
                          │                     │
                   ┌──────┴──────┐              │
                   │有 BiometricKey?│            │
                   └──────┬──────┘              │
                    ┌──────┴──────┐             │
                    ▼             ▼             │
             Keystore解密   降级：输入           │
             恢复K_master   主密码               │
                    │        │                  │
                    └────┬───┘                  │
                         ▼                      │
                unlockWithKMaster               │
                         │                      │
                         ▼                      ▼
                      解密DataKey → 进入首页
```

## 改动

| 文件 | 改动 |
|------|------|
| `crypto/KeystoreHelper.kt` | 新增生物识别保护密钥 alias `pwd_biometric_wrap`（`setUserAuthenticationRequired(true)`, timeout=-1）；`storeKmForBiometric()` 用 Keystore+SP 加密存储 K_master；`getKmFromBiometric()` 解密恢复；`hasBiometricKey()` 检查可用性 |
| `crypto/SessionManager.kt` | 抽取 `unlockWithKMaster(ctx, kMaster)` 通用逻辑；新增 `unlockWithBiometric(ctx)` 从 Keystore 取 K_master 后调用 |
| `SetMasterPasswordScreen.kt` | 初始化时 `KeystoreHelper.storeKmForBiometric(context, kMaster)` 存储 |
| `MainActivity.kt` | 生物识别成功→`hasBiometricKey` 检查→是→`unlockWithBiometric` 直接解锁；否/失败→降级主密码输入；无硬件→直接主密码输入 |

## 验证状态

- [x] 编译通过
