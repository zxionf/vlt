# 06 — 删除前验证生物识别 — 2026-07-25

## 概述
点击删除按钮后需要先通过指纹/人脸验证，验证通过才执行删除。设备不支持生物识别时降级为直接删除。

## 改动

| 文件 | 改动 |
|------|------|
| `PasswordDetailDialog.kt` | 添加 `authenticateThenDelete()` 函数；删除按钮 onClick 从直接删除改为先调 BiometricPrompt；降级处理无生物特征设备 |

## 验证状态

- [x] 编译通过
