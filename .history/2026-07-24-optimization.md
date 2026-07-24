# 项目优化记录 — 2026-07-24

## 概述
对 password (vlt) Android 密码管理器进行全面代码优化，涵盖功能修复、代码质量、工程卫生三大方面。

---

## 修改清单

### 🔴 功能缺陷修复

| # | 问题 | 文件 | 改动 |
|---|------|------|------|
| 1 | **SearchScreen 搜索功能不可用** — 使用硬编码 `sampleItems`，无法搜索真实数据 | `SearchScreen.kt` | 注入 `PwdViewModel`，基于 `items.description` 做实时过滤，删除 `PasswordItem` 和 `sampleItems` |
| 2 | **"自动锁定"开关无实际效果** — 局部 `remember`，不影响 `MainActivity` | `SettingScreen.kt`、`MainActivity.kt`、`ThemeManager.kt` | DataStore 持久化 `autoLockEnabled`，`MainViewModel.lock()` 检查开关，`SettingScreen` 写入偏好 |
| 3 | **数据库无迁移策略** — 改 schema 即崩溃 | `PwdDB.kt` | 添加 `.fallbackToDestructiveMigration()` |

### 🟡 代码质量

| # | 问题 | 文件 | 改动 |
|---|------|------|------|
| 4 | **死代码和注释代码** — `var i:Int=0`、注释的 `SwipeableItem`、注释的 `TopAppBar colors`、注释的 `FLAG_SECURE` | `HomeScreen.kt`、`MainActivity.kt` | 删除所有死代码和注释块，清理未使用的 `WindowManager` 导入 |
| 5 | **`PwdDB.getInstance()` 缺少 double-check** | `PwdDB.kt` | `synchronized` 块内增加二次 null 检查，使用 `also` 链式赋值 |
| 6 | **不规范协程使用** — 直接 `CoroutineScope(Dispatchers.IO)` | `ThemeManager.kt` | 改用 `rememberCoroutineScope()` + `scope.launch` |
| 7 | **Dialog 组件重复** — `UpdateAppDialog` 与 `CommonDialog` 完全相同 | `UpdateAppDialog.kt` | 删除文件（无引用） |

### 🟢 工程卫生

| # | 问题 | 文件 | 改动 |
|---|------|------|------|
| 8 | **预构建 APK 可能被提交** | `.gitignore` | 添加 `app/debug/*.apk` 和 `app/release/*.apk` 忽略规则 |

### 🟢 InfoDialog 改进

| 文件 | 改动 |
|------|------|
| `InfoDialog.kt` | 清理代码格式，信息展示添加描述标签（"ID:"、"描述:"、"密码:"） |

---

## 涉及文件（共 10 个）

| 文件 | 操作 |
|------|------|
| `app/src/main/java/io/zx/password/PwdDB.kt` | 修改 — double-check + fallbackToDestructiveMigration |
| `app/src/main/java/io/zx/password/MainActivity.kt` | 修改 — 清理死代码 + autoLockEnabled 读取 |
| `app/src/main/java/io/zx/password/ui/theme/ThemeManager.kt` | 修改 — 协程修复 + autoLockEnabled 偏好 |
| `app/src/main/java/io/zx/password/ui/layout/HomeScreen.kt` | 修改 — 删除死代码（3处） |
| `app/src/main/java/io/zx/password/ui/layout/SearchScreen.kt` | 重写 — 连接数据库 |
| `app/src/main/java/io/zx/password/ui/layout/SettingScreen.kt` | 修改 — autoLock 持久化 |
| `app/src/main/java/io/zx/password/ui/component/InfoDialog.kt` | 修改 — 清理代码 |
| `app/src/main/java/io/zx/password/ui/component/UpdateAppDialog.kt` | 删除 |
| `.gitignore` | 修改 — 添加 APK 忽略规则 |

---

## 未包含的改动

- **`passwd` → `password` 字段重命名**：因为涉及 Room 数据库 schema，且 `passwd` 在 Unix 传统中是常见缩写（`/etc/passwd`），单独重命名收益不高，留待后续数据库大版本迁移时统一处理。

## 验证状态

- [x] 编译通过 (`./gradlew assembleDebug`) — 无错误，无警告
- [x] 单元测试通过 (`./gradlew test`)
