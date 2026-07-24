# 05 — 复制反馈改为图标切换 — 2026-07-25

## 概述
复制后不再显示 Snackbar，改为图标切换：复制图标 → 对号（Check），1.5 秒后自动恢复。

## 改动

| 文件 | 改动 |
|------|------|
| `PasswordDetailDialog.kt` | 移除 Snackbar；DetailField 内部 `copied` 状态 + `LaunchedEffect` 延迟恢复；对号用 `primary` 色强调 |

## 验证状态

- [x] 编译通过
