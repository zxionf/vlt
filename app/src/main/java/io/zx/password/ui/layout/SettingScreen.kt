package io.zx.password.ui.layout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.zx.password.ui.theme.LocalThemeState
import io.zx.password.ui.theme.ThemeMode
import io.zx.password.ui.theme.ThemePreferences
import io.zx.password.ui.theme.ThemeState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.zx.password.PwdViewModelFactory
import io.zx.password.SyncManager
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingScreen() {
    val context = LocalContext.current
    val themePreferences = remember { ThemePreferences(context) }
    val scope = rememberCoroutineScope()
    var autoLockEnabled by remember { mutableStateOf(true) }
    val themeState = LocalThemeState.current

    // 读取保存的自动锁定偏好
    androidx.compose.runtime.LaunchedEffect(Unit) {
        autoLockEnabled = themePreferences.autoLockEnabledFlow.firstOrNull() ?: true
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // 主题模式卡片（使用下拉菜单）
        SettingItem(
            icon = Icons.Default.BrightnessMedium,
            title = "深色模式",
            subtitle = "切换亮色/暗色模式",
            trailing = {
                TextMenu(themeState)
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 安全设置卡片
        SettingItem(
            icon = Icons.Default.Lock,
            title = "自动锁定",
            subtitle = "应用切换到后台后自动锁定",
            trailing = {
                Switch(
                    checked = autoLockEnabled,
                    onCheckedChange = {
                        autoLockEnabled = it
                        scope.launch { themePreferences.saveAutoLockEnabled(it) }
                    }
                )
            }
        )

        // 设备信息
        var deviceInfo by remember { mutableStateOf("加载中...") }
        LaunchedEffect(Unit) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val device = io.zx.password.PwdDB.getInstance(context).DeviceDao().getCurrentDevice()
                deviceInfo = device?.let {
                    "ID: ${it.deviceId.take(8)}...\n设备: ${it.deviceName}\n公钥: ${it.publicKey.take(32)}..."
                } ?: "未初始化"
            }
        }
        SettingItem(
            icon = Icons.Default.AutoMode,
            title = "设备信息",
            subtitle = deviceInfo,
            trailing = { }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 同步
        var serverUrl by remember { mutableStateOf(io.zx.password.SyncManager.baseUrl) }
        var syncStatus by remember { mutableStateOf("") }
        var syncLoading by remember { mutableStateOf(false) }
        SettingItem(
            icon = Icons.Default.DownloadForOffline,
            title = "服务器同步",
            subtitle = "同步密码到服务器",
            trailing = { },
            onClick = { }
        )
        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it; io.zx.password.SyncManager.baseUrl = it },
            label = { Text("服务器地址") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    syncLoading = true
                    syncStatus = "注册中..."
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        val device = io.zx.password.PwdDB.getInstance(context).DeviceDao().getCurrentDevice()
                        if (device == null) {
                            withContext(kotlinx.coroutines.Dispatchers.Main) {
                                syncStatus = "设备未初始化"
                                syncLoading = false
                            }
                            return@launch
                        }
                        val result = io.zx.password.SyncManager.registerDevice(
                            deviceId = device.deviceId,
                            deviceName = device.deviceName,
                            publicKey = device.publicKey,
                            encryptedDataKey = device.encryptedDataKey ?: ""
                        )
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            syncStatus = result.fold(
                                onSuccess = { "注册成功: $it" },
                                onFailure = { "注册失败: ${it.message}" }
                            )
                            syncLoading = false
                        }
                    }
                },
                enabled = !syncLoading,
                modifier = Modifier.weight(1f)
            ) { Text("注册设备") }
            OutlinedButton(
                onClick = {
                    syncLoading = true
                    syncStatus = "同步中..."
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        val db = io.zx.password.PwdDB.getInstance(context)
                        val entries = kotlinx.coroutines.runBlocking {
                            var list = emptyList<io.zx.password.PasswordEntry>()
                            db.PwdDao().getAll().collect { list = it; return@collect }
                            list
                        }
                        val pushResult = io.zx.password.SyncManager.pushRecords(entries)
                        // 同时拉取
                        val lastSync = context.getSharedPreferences("sync_prefs", android.content.Context.MODE_PRIVATE)
                            .getLong("last_sync", 0L)
                        val pullResult = io.zx.password.SyncManager.pullRecords(lastSync)
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            syncStatus = buildString {
                                pushResult.fold(
                                    onSuccess = { append("上传成功; ") },
                                    onFailure = { append("上传失败: ${it.message}; ") }
                                )
                                pullResult.fold(
                                    onSuccess = { records ->
                                        append("下载 ${records.size} 条")
                                        if (records.isNotEmpty()) {
                                            context.getSharedPreferences("sync_prefs", android.content.Context.MODE_PRIVATE)
                                                .edit().putLong("last_sync", System.currentTimeMillis()).apply()
                                        }
                                    },
                                    onFailure = { append("下载失败: ${it.message}") }
                                )
                            }
                            syncLoading = false
                        }
                    }
                },
                enabled = !syncLoading,
                modifier = Modifier.weight(1f)
            ) { Text("同步") }
        }
        if (syncStatus.isNotBlank()) {
            Text(
                text = syncStatus,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 数据库调试
        var showDbDebug by remember { mutableStateOf(false) }
        val dbViewModel: io.zx.password.PwdViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
            factory = io.zx.password.PwdViewModelFactory(context)
        )
        val dbItems by dbViewModel.items.collectAsStateWithLifecycle()
        val dbAllTags by dbViewModel.allTags.collectAsStateWithLifecycle()
        val dbTagMap by dbViewModel.tagMap.collectAsStateWithLifecycle()

        SettingItem(
            icon = Icons.Default.UnfoldMore,
            title = "数据库调试",
            subtitle = "查看所有表数据",
            trailing = { },
            onClick = { showDbDebug = true }
        )

        if (showDbDebug) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showDbDebug = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())
                    ) {
                        Text("数据库调试", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(12.dp))

                        Text("passwd 表 (${dbItems.size} 条)", style = MaterialTheme.typography.titleMedium)
                        dbItems.forEach { item ->
                            Text(
                                "  ID:${item.id} | ${item.title} | ${item.username}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("tags 表 (${dbAllTags.size} 条)", style = MaterialTheme.typography.titleMedium)
                        dbAllTags.forEach { tag ->
                            Text(
                                "  ID:${tag.id} | ${tag.name}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("password_tag_join", style = MaterialTheme.typography.titleMedium)
                        dbItems.forEach { item ->
                            val tags = dbTagMap[item.id] ?: emptyList()
                            if (tags.isNotEmpty()) {
                                Text(
                                    "  passwdId:${item.id} → ${tags.joinToString(", ") { "${it.id}:${it.name}" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(onClick = { showDbDebug = false }, modifier = Modifier.fillMaxWidth()) {
                            Text("关闭")
                        }
                    }
                }
            }
        }

    }
}

@Composable
private fun SettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailing: @Composable () -> Unit = {},
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth().let { if (onClick != null) it.clickable { onClick() } else it },
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(end = 16.dp)
            )
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            trailing()
        }
    }
}

@Composable
private fun TextMenu(
    themeState: ThemeState
) {
    var expanded by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("自动") }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable {expanded = true}
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
        )
        Icon(
            imageVector = Icons.Default.UnfoldMore,
            modifier = Modifier,
            contentDescription = null,
            tint = LocalContentColor.current
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                modifier = /*if (selected) Modifier.background(MaterialTheme.colorScheme.onSecondary) else*/ Modifier,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.AutoMode,
                        modifier = Modifier,
                        contentDescription = null,
                        tint = LocalContentColor.current
                    )
                },
                text = { Text("自动") },
                onClick = {
                    expanded = false
                    text = "自动"
                    themeState.onThemeModeChange(ThemeMode.FOLLOW_SYSTEM)
                },
            )
            DropdownMenuItem(
                modifier = /*if (selected) Modifier.background(MaterialTheme.colorScheme.onSecondary) else*/ Modifier,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.DarkMode,
                        modifier = Modifier,
                        contentDescription = null,
                        tint = LocalContentColor.current
                    )
                },
                text = { Text("深色") },
                onClick = {
                    expanded = false
                    text = "深色"
                    themeState.onThemeModeChange(ThemeMode.DARK)
                },
            )
            DropdownMenuItem(
                modifier = /*if (selected) Modifier.background(MaterialTheme.colorScheme.onSecondary) else*/ Modifier,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.LightMode,
                        modifier = Modifier,
                        contentDescription = null,
                        tint = LocalContentColor.current
                    )
                },
                text = { Text("浅色") },
                onClick = {
                    expanded = false
                    text = "浅色"
                    themeState.onThemeModeChange(ThemeMode.LIGHT)
                },
            )
        }
    }
}