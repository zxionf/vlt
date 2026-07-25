package io.zx.password.ui.layout

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import io.zx.password.ui.theme.LocalThemeState
import io.zx.password.ui.theme.ThemeMode
import io.zx.password.ui.theme.ThemeState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themeState = LocalThemeState.current

    val viewModel: SettingsViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory(context.applicationContext as Application)
    )

    val autoLockEnabled by viewModel.autoLockEnabled.collectAsState()
    val deviceInfo by viewModel.deviceInfo.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val syncLoading by viewModel.syncLoading.collectAsState()

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
                        scope.launch { viewModel.setAutoLock(it) }
                    }
                )
            }
        )
        Spacer(modifier = Modifier.height(8.dp))

        // 设备信息
        ExpandableSettingItem(
            icon = Icons.Default.Info,
            title = "设备信息",
            subtitle = "用于同步的设备标识信息"
        ) {
            Text(text = deviceInfo, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(8.dp))

        // 同步
        ExpandableSettingItem(
            icon = Icons.Default.Sync,
            title = "服务器同步",
            subtitle = "从服务器同步"
        ) {
            // 展开后显示的内容，例如一个选项列表、滑块或详细说明
            Column {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { viewModel.updateServerUrl(it) },
                    label = { Text("服务器地址") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.registerDevice() },
                        enabled = !syncLoading,
                        modifier = Modifier.weight(1f)
                    ) { Text("注册设备") }
                    OutlinedButton(
                        onClick = { viewModel.syncData() },
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
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

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
        modifier = Modifier.fillMaxWidth()
            .let { if (onClick != null) it.clickable { onClick() } else it },
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
        modifier = Modifier.clickable { expanded = true }
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

@Composable
fun ExpandableSettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    Column {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Column {
                // 可点击的标题栏，替代原来的 Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
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
                    // 尾部展开/折叠箭头，动画旋转
                    Icon(
                        imageVector = Icons.Default.ExpandMore, // 也可以用 KeyboardArrowDown
                        contentDescription = if (expanded) "收起" else "展开",
                        modifier = Modifier
                            .rotate(if (expanded) 180f else 0f)
                            .animateContentSize(),
                        tint = LocalContentColor.current
                    )
                }

                // 可展开的内容区域，带淡入淡出
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    // 这里可以自定义 padding 和样式
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 56.dp, end = 16.dp, bottom = 16.dp)
                    ) {
                        content()
                    }
                }
            }
        }
    }
}