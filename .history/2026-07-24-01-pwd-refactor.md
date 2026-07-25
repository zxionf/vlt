# Pwd.kt 数据模型重构适配 — 2026-07-24

## 概述
`ai` 分支上 `Pwd.kt` 被重写，旧模型 `Pwd` (table `pwd`, 3 字段) 替换为新模型 `PasswdEntity` + `Tag` + `PasswordTagJoin` (table `passwd`, 10 字段 + 标签关联)。更新所有引用的数据层和 UI 层文件以适配新模型。

---

## 新数据模型

- **PasswdEntity** — 表 `passwd`: id (Long), title, username, encryptedPasswd, iv, notes, url, passwd, createdAt, updatedAt
- **Tag** — 表 `tags`: id (Long), name
- **PasswordTagJoin** — 关联表 `password_tag_join`: passwdId (Long), tagId (Long)

## 修改清单

### 数据层

| 文件 | 改动 |
|------|------|
| `Pwd.kt` | 修复索引：`category` 不存在→删除；`created_at`→`createdAt`；`tagId` 添加 Index；删除无用 import `TableInfo` |
| `PwdDao.kt` | `Pwd`→`PasswdEntity`，表名 `pwd`→`passwd`，id 类型 `Int`→`Long`，按 `updatedAt DESC` 排序；新增 `TagDao` 接口 (Tag 和 Join 表 CRUD + 标签关联查询) |
| `PwdDB.kt` | entities 添加 `Tag`、`PasswordTagJoin`；version `1`→`2`；暴露 `TagDao()` |
| `PwdRepository.kt` | 所有 `Pwd`→`PasswdEntity` |
| `PwdViewModel.kt` | 所有 `Pwd`→`PasswdEntity`；清理注释代码和调试日志 |
| `PwdViewModelFactory.kt` | 无需修改（泛型自动适配） |

### UI 层

| 文件 | 改动 |
|------|------|
| `HomeScreen.kt` | `Pwd`→`PasswdEntity`；`item.description`→`item.title`；测试条目适配新字段 (title, username, encryptedPasswd, iv) |
| `SearchScreen.kt` | `Pwd`→`PasswdEntity`；搜索从 `description` 改为 `title + username` 双字段匹配；结果卡片显示 `title` + `username` |
| `InfoDialog.kt` | `Pwd`→`PasswdEntity`；显示字段从 2 个扩展到 5 个 (title, username, passwd, notes, url) |
| `EditPwdDialog.kt` | `Pwd`→`PasswdEntity`；编辑字段 `description`→`title` |

---

## 涉及文件（共 10 个）

| 文件 | 操作 |
|------|------|
| `app/src/main/java/io/zx/password/Pwd.kt` | 修改 — 修复索引 |
| `app/src/main/java/io/zx/password/PwdDao.kt` | 重写 — 新实体 + TagDao |
| `app/src/main/java/io/zx/password/PwdDB.kt` | 修改 — entities + version |
| `app/src/main/java/io/zx/password/PwdRepository.kt` | 修改 — PasswdEntity |
| `app/src/main/java/io/zx/password/PwdViewModel.kt` | 修改 — PasswdEntity |
| `app/src/main/java/io/zx/password/ui/layout/HomeScreen.kt` | 修改 — PasswdEntity |
| `app/src/main/java/io/zx/password/ui/layout/SearchScreen.kt` | 修改 — PasswdEntity |
| `app/src/main/java/io/zx/password/ui/component/InfoDialog.kt` | 修改 — PasswdEntity |
| `app/src/main/java/io/zx/password/ui/component/EditPwdDialog.kt` | 修改 — PasswdEntity |

---

## 验证状态

- [x] 编译通过 (`./gradlew assembleDebug`) — 仅 1 个预存警告 (LocalClipboardManager deprecated)
- [x] 单元测试通过 (`./gradlew test`)

## 注意事项

- 数据库 version 已从 1 升到 2，配合 `fallbackToDestructiveMigration(false)` 保护已有数据
- `TagDao` 已创建但尚未集成到 ViewModel — 标签功能留待后续 UI 实现
- `Pwd.kt` 文件名保持原名（未改为 `PasswdEntity.kt`），避免大面积 import 路径变更
