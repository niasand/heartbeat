package com.heartratemonitor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
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
 * 心电图风格心率曲线（Compose 版）
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
    fixedHeight: Dp? = 100.dp,
    showYAxis: Boolean = false,
    yAxisRange: IntRange = 50..200,
) {
    val yAxisLabels = yAxisRange.step(50).toList()
    val labelWidth = 28f // dp reserved for Y-axis labels

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

        // Y 轴参数（与波形映射一致）
        val minVal = 40f
        val maxVal = 220f
        val range = maxVal - minVal
        val topPad = h * 0.15f   // HR=220 对应的 Y
        val bottomPad = h * 0.85f // HR=40 对应的 Y
        val chartHeight = bottomPad - topPad

        // 画 Y 轴刻度和参考线
        if (showYAxis) {
            val nativeCanvas = drawContext.canvas.nativeCanvas
            val textPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.argb(128, 255, 255, 255)
                textSize = 24f
                typeface = android.graphics.Typeface.DEFAULT
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.RIGHT
            }
            yAxisLabels.forEach { hr ->
                val normalized = (hr - minVal) / range
                val y = topPad + normalized * chartHeight
                // 参考线
                drawLine(
                    color = Color.White.copy(alpha = 0.12f),
                    start = Offset(labelWidth, y),
                    end = Offset(w, y),
                    strokeWidth = 0.5f
                )
                // 刻度文字（基线对齐到参考线）
                nativeCanvas.drawText("$hr", labelWidth - 4f, y + 8f, textPaint)
            }
        }

        // 画波形曲线
        if (heartRateHistory.isNotEmpty()) {
            val data = heartRateHistory
            val pointCount = data.size.coerceAtLeast(2)
            val leftPad = if (showYAxis) labelWidth else 4f
            val rightPad = 4f

            val path = Path()
            val stepX = (w - leftPad - rightPad) / (pointCount - 1)

            data.forEachIndexed { index, hr ->
                val x = leftPad + index * stepX
                val normalizedHr = (hr - minVal) / range
                val y = topPad + normalizedHr * chartHeight

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
                    val x = leftPad + index * stepX
                    val normalizedHr = (hr - minVal) / range
                    val y = topPad + normalizedHr * chartHeight

                    if (index == 0) {
                        gradientPath.moveTo(x, y)
                    } else {
                        gradientPath.lineTo(x, y)
                    }
                }
                gradientPath.lineTo(leftPad + (data.size - 1) * stepX, h)
                gradientPath.lineTo(leftPad, h)
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
