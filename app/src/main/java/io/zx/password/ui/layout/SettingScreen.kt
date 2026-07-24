package io.zx.password.ui.layout

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.flow.firstOrNull
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

        // 可以继续添加更多设置项...

    }
}

@Composable
private fun SettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailing: @Composable () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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