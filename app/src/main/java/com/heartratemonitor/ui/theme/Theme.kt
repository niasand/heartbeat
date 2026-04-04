package com.heartratemonitor.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

object AppColors {
    // 默认主题色
    val Primary = Color(0xFF6200EE)
    val Secondary = Color(0xFF03DAC6)
    val Error = Color(0xFFB00020)
    val Warning = Color(0xFFFF6200)
    val Success = Color(0xFF4CAF50)

    // 心率相关颜色
    val HeartRateNormal = Color(0xFF4CAF50)
    val HeartRateHigh = Color(0xFFFF6200)
    val HeartRateCritical = Color(0xFFB00020)
}

fun parseThemeColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        AppColors.Primary
    }
}
