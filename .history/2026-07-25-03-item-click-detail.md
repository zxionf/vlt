# 03 — 密码条目点击弹出详情 + 编辑模式 — 2026-07-25

## 概述
删除首页条目上的三个按钮（信息/编辑/复制），改为点击整卡弹出详情对话框，详情中集成了复制、编辑、删除功能。编辑时导航回 CreatePasswordScreen（编辑模式）。

## 改动

### 新增

| 文件 | 说明 |
|------|------|
| `PasswordDetailDialog.kt` | 详情弹窗：显示全部字段 + 复制密码(Snackbar) + 编辑 + 删除(红色) |

### 修改

| 文件 | 改动 |
|------|------|
| `HomeScreen.kt` | PwdItemCard 移除三个按钮改为整卡 onClick；HomeScaffold 简化为 detailItem 状态 + PasswordDetailDialog；新增 onEditItem 回调 |
| `CreatePasswordScreen.kt` | 新增 editItem 参数支持编辑模式；save() 判断 isEdit 调用 updateItem/addItem；标题动态显示"新建/编辑记录" |
| `MainScreen.kt` | 新增 editingItem 状态；NavigationGraph 传参；HomeScreen 传入 onEditItem；create_password 路由传入 editItem |

## 交互流程

```
首页列表 → 点击条目 → PasswordDetailDialog 弹出
  ├── 复制密码 → Snackbar "密码已复制"
  ├── 编辑 → 关闭弹窗 → 导航到 CreatePasswordScreen(editItem) → 修改 → 保存(updateItem)
  └── 删除 → 确认删除(deleteItem) → 关闭弹窗
```

## 验证状态

- [x] 编译通过 (`./gradlew assembleDebug`)
