# 02 — 子页面隐藏底部导航 — 2026-07-25

## 概述
导航到 `create_password` 等非主导航页面时，底部导航栏应隐藏。

## 改动

| 文件 | 改动 |
|------|------|
| `MainScreen.kt` | `MainScreen()` 中读取 `currentRoute`，`bottomBar` 仅当路由为 `home/search/setting` 时渲染 |

## 验证状态

- [x] 编译通过
