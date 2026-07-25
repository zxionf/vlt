package io.zx.password.preview

import android.R.color.white
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview
import io.zx.password.ui.layout.SettingScreen
import io.zx.password.ui.theme.LocalThemeState
import io.zx.password.ui.theme.ThemeMode
import io.zx.password.ui.theme.ThemeState

@OptIn(ExperimentalMaterial3Api::class)
@Preview(
    showBackground = true,
    backgroundColor = white.toLong()
)
@Composable
fun PreviewSettingScreen() {
    // 创建一个占位 ThemeState，实际模式可任意选，不影响预览布局
    val previewThemeState = ThemeState(
        themeMode = ThemeMode.FOLLOW_SYSTEM,
        onThemeModeChange = {}
    )
    CompositionLocalProvider(LocalThemeState provides previewThemeState) {
        SettingScreen()
    }
}