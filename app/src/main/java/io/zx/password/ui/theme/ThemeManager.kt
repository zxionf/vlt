package io.zx.password.ui.theme

// ui/theme/ThemeManager.kt
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class ThemeMode{
    FOLLOW_SYSTEM, LIGHT, DARK
}

private val Context.dataStore by preferencesDataStore("theme_prefs")
private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")

class ThemePreferences(context: Context) {
    private val dataStore = context.dataStore

    val themeModeFlow: Flow<ThemeMode> = dataStore.data.map { prefs ->
        val modeStr = prefs[THEME_MODE_KEY] ?: ThemeMode.FOLLOW_SYSTEM.name
        try {
            ThemeMode.valueOf(modeStr)
        } catch (e: IllegalArgumentException) {
            ThemeMode.FOLLOW_SYSTEM
        }
    }

    suspend fun saveThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs ->
            prefs[THEME_MODE_KEY] = mode.name
        }
    }
}

@Stable
class ThemeState(
    val themeMode: ThemeMode,
    val onThemeModeChange: (ThemeMode) -> Unit
)

val LocalThemeState = staticCompositionLocalOf<ThemeState> {
    error("No ThemeState provided")
}

@Composable
fun rememberThemeState(themePreferences: ThemePreferences): ThemeState {
    var themeMode by remember { mutableStateOf(ThemeMode.FOLLOW_SYSTEM) }
    // 初次加载读取保存的值（简化示例，实际建议用 Flow 收集）
    androidx.compose.runtime.LaunchedEffect(Unit) {
        themeMode = themePreferences.themeModeFlow.firstOrNull() ?: ThemeMode.FOLLOW_SYSTEM
    }
    return ThemeState(
        themeMode = themeMode,
        onThemeModeChange = { newMode ->
            themeMode = newMode
            // 异步保存，不阻塞UI
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                themePreferences.saveThemeMode(newMode)
            }
        }
    )
}