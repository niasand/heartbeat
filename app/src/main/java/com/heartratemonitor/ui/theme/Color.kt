package com.heartratemonitor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

private val LightColors = lightColorScheme(
    primary = AppColors.Primary,
    secondary = AppColors.Secondary,
    error = AppColors.Error,
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
)

private val DarkColors = darkColorScheme(
    primary = AppColors.Primary,
    secondary = AppColors.Secondary,
    error = AppColors.Error,
    background = Color(0xFF1C1B1F),
    surface = Color(0xFF1C1B1F),
)

@Composable
fun HeartRateMonitorTheme(
    themeColor: String = AppColors.Primary.toColorString(),
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        DarkColors.copy(primary = parseColorFromHex(themeColor))
    } else {
        LightColors.copy(primary = parseColorFromHex(themeColor))
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

fun Color.toColorString(): String {
    return String.format("#%08X", this.toArgb())
}

fun parseColorFromHex(hexColor: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hexColor))
    } catch (e: Exception) {
        AppColors.Primary
    }
}
