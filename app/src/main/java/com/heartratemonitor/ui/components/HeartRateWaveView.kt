package com.heartratemonitor.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 心电图风格实时心率曲线（Compose 版）
 * 参考 WaveProject 实现，适配 Jetpack Compose
 */
@Composable
fun HeartRateWaveView(
    heartRateHistory: List<Int>,
    modifier: Modifier = Modifier,
    waveColor: Color = Color(0xFFEE4000),
    gridColor: Color = Color(0xFF1a3a1a),
    gridBigColor: Color = Color(0xFF2a5a2a),
    backgroundColor: Color = Color(0xFF0a0a0a),
    fixedHeight: Dp? = 100.dp
) {
    // 扫描线动画
    val scanProgress = remember { Animatable(0f) }

    LaunchedEffect(heartRateHistory.size) {
        if (heartRateHistory.isNotEmpty()) {
            scanProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
            )
        }
    }

    Canvas(
        modifier = modifier.then(
            if (fixedHeight != null) Modifier.fillMaxWidth().height(fixedHeight)
            else Modifier.fillMaxSize()
        )
    ) {
        drawRect(backgroundColor)

        val w = size.width
        val h = size.height
        val gridSmall = 10f
        val gridBig = 50f

        // 画小网格
        drawGrid(w, h, gridSmall, gridColor, 1f)
        // 画大网格
        drawGrid(w, h, gridBig, gridBigColor, 1f)

        // 画波形曲线
        if (heartRateHistory.isNotEmpty()) {
            val data = heartRateHistory.takeLast(200) // 最多显示最近200个点
            val maxVal = 220f
            val minVal = 40f
            val range = maxVal - minVal

            val path = Path()
            val stepX = w / 200f

            data.forEachIndexed { index, hr ->
                val x = index * stepX
                val normalizedHr = (hr - minVal) / range
                // 心率越高，波形越往下（模拟心电图方向）
                val y = h * 0.15f + normalizedHr * h * 0.7f

                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }

            drawPath(
                path = path,
                color = waveColor,
                style = Stroke(
                    width = 2f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // 波形发光效果（半透明渐变）
            if (data.size > 1) {
                val gradientPath = Path()
                data.forEachIndexed { index, hr ->
                    val x = index * stepX
                    val normalizedHr = (hr - minVal) / range
                    val y = h * 0.15f + normalizedHr * h * 0.7f

                    if (index == 0) {
                        gradientPath.moveTo(x, y)
                    } else {
                        gradientPath.lineTo(x, y)
                    }
                }
                // 闭合到底部形成渐变区域
                gradientPath.lineTo((data.size - 1) * stepX, h)
                gradientPath.lineTo(0f, h)
                gradientPath.close()

                drawPath(
                    path = gradientPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            waveColor.copy(alpha = 0.15f),
                            waveColor.copy(alpha = 0.02f),
                            Color.Transparent
                        )
                    )
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGrid(
    w: Float,
    h: Float,
    gridSize: Float,
    color: Color,
    strokeWidth: Float
) {
    val path = Path()
    // 横线
    var y = 0f
    while (y < h) {
        path.moveTo(0f, y)
        path.lineTo(w, y)
        y += gridSize
    }
    // 竖线
    var x = 0f
    while (x < w) {
        path.moveTo(x, 0f)
        path.lineTo(x, h)
        x += gridSize
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth)
    )
}
