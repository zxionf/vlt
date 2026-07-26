package io.zx.password.preview

import android.R.color.white
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.zx.password.ui.layout.PwdItemCard
import io.zx.password.ui.layout.PwdItemIconCard
import io.zx.password.ui.theme.LocalThemeState
import io.zx.password.ui.theme.ThemeMode
import io.zx.password.ui.theme.ThemeState

@OptIn(ExperimentalMaterial3Api::class)
@Preview(
    showBackground = true,
    backgroundColor = white.toLong()
)
@Composable
fun PreviewHomeScreen() {
    // 创建一个占位 ThemeState，实际模式可任意选，不影响预览布局
    val previewThemeState = ThemeState(
        themeMode = ThemeMode.DARK,
        onThemeModeChange = {}
    )
    CompositionLocalProvider(LocalThemeState provides previewThemeState) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                PwdItemIconCard(
                    title = "了解 PWD",
                    subtitle = "查看常见问题",
                    imageVector = Icons.AutoMirrored.Filled.Help,
                    onClickLabel = "打开 PWD 文档页面",
                    onClick = { }
                )
            }
            item {
                PwdItemCard(
                    title = "Google",
                    subtitle = "user",
                    tags = listOf("学校"),
                    onClick = { }
                )
            }
        }
    }
}