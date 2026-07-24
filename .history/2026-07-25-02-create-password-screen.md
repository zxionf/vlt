# 01 — 新建密码记录页面 — 2026-07-25

## 概述
首页右上角加号按钮改为导航到新建记录页面，包含完整表单字段。

---

## 改动

### 新增文件

| 文件 | 说明 |
|------|------|
| `app/src/main/java/io/zx/password/ui/layout/CreatePasswordScreen.kt` | 新建记录全屏页面，含 5 个表单字段 + 验证 + Snackbar 提示 |

### 修改文件

| 文件 | 改动 |
|------|------|
| `MainScreen.kt` | NavigationGraph 添加 `"create_password"` 路由，`HomeScreen` 传入 `onAddClick` 回调 |
| `HomeScreen.kt` | 新增 `onAddClick` 参数，加号按钮改为调用 `onAddClick`；移除硬编码测试条目代码 |

---

## 表单字段

| 字段 | 对应 PasswdEntity | 必填 | 说明 |
|------|-------------------|------|------|
| 标题 * | `title` | ✅ | 如 Google、GitHub |
| 用户名/账号 | `username` | | 如 user@gmail.com |
| 密码 * | `passwd` | ✅ | 密码掩码显示 |
| 网址 | `url` | | 如 https://github.com |
| 备注 | `notes` | | 多行文本 |

保存后通过 `PwdViewModel.addItem()` 写入 Room，自动填充 `encryptedPasswd=""`（预留加密）、`iv=""`、`createdAt`/`updatedAt` 时间戳。

---

## 路由

```
home → create_password (push)
create_password → home (popBackStack, 自动)
```

---

## 验证状态

- [x] 编译通过 (`./gradlew assembleDebug`)
- [x] 单元测试通过 (`./gradlew test`)
