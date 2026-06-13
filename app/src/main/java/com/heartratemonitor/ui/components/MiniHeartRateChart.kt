package com.heartratemonitor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.heartratemonitor.ui.theme.AppColors

/**
 * 极简心率折线图，用于主屏卡片内的实时趋势展示。
 * 自动按样本 min/max 归一化，无需外部传坐标范围。
 */
@Composable
fun MiniHeartRateChart(
    samples: List<Int>,
    modifier: Modifier = Modifier,
    lineColor: androidx.compose.ui.graphics.Color = AppColors.HeartRateNormal,
    strokeWidth: Float = 4f
) {
    Canvas(modifier = modifier) {
        if (samples.size < 2) return@Canvas

        val minVal = samples.min().toFloat()
        val maxVal = samples.max().toFloat()
        // 极差为 0（全部相同）时给固定中位，避免除零
        val range = (maxVal - minVal).coerceAtLeast(1f)
        val pad = strokeWidth

        val stepX = (size.width - 2 * pad) / (samples.size - 1)

        val path = Path()
        samples.forEachIndexed { i, v ->
            val x = pad + i * stepX
            // 心率高 → y 小（屏幕顶部），故取反
            val y = pad + (1 - (v.toFloat() - minVal) / range) * (size.height - 2 * pad)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}
