package io.zx.password.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext


// 定义扩展颜色类
data class ExtendedColors(
    val primary: Color,
    val primaryContainer: Color,
    val cardBackground: Color,
    val cardBorder: Color,
    val specialBackground: Color,
    val specialContent: Color
)

// 默认扩展颜色
val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        primary = Color.Unspecified,
        primaryContainer = Color.Unspecified,
        cardBackground = Color.Unspecified,
        cardBorder = Color.Unspecified,
        specialBackground = Color.Unspecified,
        specialContent = Color.Unspecified
    )
}

// 创建扩展颜色提供者
@Composable
fun ProvideExtendedColors(
    colors: ExtendedColors,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalExtendedColors provides colors,
        content = content
    )
}
private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,

    primaryContainer = Purple10,

//    Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),

)

// 在主题中定义具体颜色
val LightExtendedColors = ExtendedColors(
    primary = Color(0xFFE3E3EB),
    primaryContainer = Color(0xFFEEEDF4),
    cardBackground = Color(0xFFF5F5F5),
    cardBorder = Color(0xFFE0E0E0),
    specialBackground = Color(0xFFFFE0B2),
    specialContent = Color(0xFFE65100)
)
val DarkExtendedColors = ExtendedColors(
    primary = Color(0xFFE3E3EB),
    primaryContainer = Color(0xFF222222),
    cardBackground = Color(0xFF2C2C2C),
    cardBorder = Color(0xFF404040),
    specialBackground = Color(0xFF4A2C2C),
    specialContent = Color(0xFFFFB74D)
)

@Composable
fun PasswordTheme(
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val themeState = LocalThemeState.current
    val darkTheme = when (themeState.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
//    val colorScheme = if(darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors
    ProvideExtendedColors(colors = extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }

}

object PwdTheme {
    val colors: ExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalExtendedColors.current
}