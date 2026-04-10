package com.heartratemonitor.ui.components

import android.content.Context
import android.graphics.*
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * 心电图风格心率曲线（AndroidView 版，原生 View.onDraw 保证每次数据变化都能重绘）
 */
@Composable
fun HeartRateWaveView(
    heartRateHistory: List<Int>,
    modifier: Modifier = Modifier,
    waveColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFFEE4000),
    gridColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFF1a3a1a),
    gridBigColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFF2a5a2a),
    backgroundColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFF0a0a0a),
    fixedHeight: Dp? = null,
    showYAxis: Boolean = false,
    yAxisRange: IntRange = 50..200,
) {
    val yAxisLabels = yAxisRange.step(50).toList()
    val density = LocalDensity.current
    val labelWidthPx = with(density) { (if (showYAxis) 40.dp else 0.dp).toPx() }

    // 将 Compose 颜色转为 Int，避免在 View 内重复转换
    val waveColorInt = waveColor.toArgb()
    val gridColorInt = gridColor.toArgb()
    val gridBigColorInt = gridBigColor.toArgb()
    val bgColorInt = backgroundColor.toArgb()

    AndroidView(
        factory = { ctx ->
            WaveChartView(ctx).apply {
                setWillNotDraw(false)
            }
        },
        update = { view ->
            view.setData(
                data = heartRateHistory,
                yAxisLabels = yAxisLabels,
                showYAxis = showYAxis,
                labelWidthPx = labelWidthPx,
                waveColor = waveColorInt,
                gridColor = gridColorInt,
                gridBigColor = gridBigColorInt,
                bgColor = bgColorInt,
            )
        },
        modifier = modifier
    )
}

private class WaveChartView(context: Context) : View(context) {

    private var data: List<Int> = emptyList()
    private var yAxisLabels: List<Int> = emptyList()
    private var showYAxis: Boolean = false
    private var labelWidthPx: Float = 0f
    private var waveColor: Int = Color.RED
    private var gridColor: Int = Color.GREEN
    private var gridBigColor: Int = Color.GREEN
    private var bgColor: Int = Color.BLACK

    // Pre-allocated paints
    private val bgPaint = Paint()
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridBigPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 2f
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val refLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.5f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f
        typeface = Typeface.DEFAULT
        textAlign = Paint.Align.RIGHT
    }

    fun setData(
        data: List<Int>,
        yAxisLabels: List<Int>,
        showYAxis: Boolean,
        labelWidthPx: Float,
        waveColor: Int,
        gridColor: Int,
        gridBigColor: Int,
        bgColor: Int,
    ) {
        this.data = data
        this.yAxisLabels = yAxisLabels
        this.showYAxis = showYAxis
        this.labelWidthPx = labelWidthPx
        this.waveColor = waveColor
        this.gridColor = gridColor
        this.gridBigColor = gridBigColor
        this.bgColor = bgColor
        invalidate() // Force redraw
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Background
        bgPaint.color = bgColor
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // Grid
        drawGrid(canvas, w, h, 10f, gridColor)
        drawGrid(canvas, w, h, 50f, gridBigColor)

        // Wave parameters
        val minVal = 40f
        val maxVal = 220f
        val range = maxVal - minVal
        val topPad = h * 0.15f
        val bottomPad = h * 0.85f
        val chartHeight = bottomPad - topPad
        val leftPad = labelWidthPx
        val rightPad = 4f

        // Y axis
        if (showYAxis) {
            textPaint.color = Color.argb(128, 255, 255, 255)
            refLinePaint.color = Color.argb(30, 255, 255, 255)
            yAxisLabels.forEach { hr ->
                val normalized = (hr - minVal) / range
                val y = bottomPad - normalized * chartHeight
                canvas.drawLine(leftPad, y, w, y, refLinePaint)
                canvas.drawText("$hr", leftPad - 4f, y + 8f, textPaint)
            }
        }

        // Wave curve
        if (data.isNotEmpty()) {
            val pointCount = data.size.coerceAtLeast(2)
            val stepX = if (pointCount > 1) (w - leftPad - rightPad) / (pointCount - 1) else 0f

            // Main curve
            val wavePath = Path()
            data.forEachIndexed { index, hr ->
                val x = leftPad + index * stepX
                val normalizedHr = (hr - minVal) / range
                val y = bottomPad - normalizedHr * chartHeight
                if (index == 0) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
            }
            wavePaint.color = waveColor
            canvas.drawPath(wavePath, wavePaint)

            // Glow gradient
            if (data.size > 1) {
                val glowPath = Path()
                data.forEachIndexed { index, hr ->
                    val x = leftPad + index * stepX
                    val normalizedHr = (hr - minVal) / range
                    val y = bottomPad - normalizedHr * chartHeight
                    if (index == 0) glowPath.moveTo(x, y) else glowPath.lineTo(x, y)
                }
                glowPath.lineTo(leftPad + (data.size - 1) * stepX, h)
                glowPath.lineTo(leftPad, h)
                glowPath.close()

                val glowShader = LinearGradient(0f, topPad, 0f, h, intArrayOf(
                    Color.argb(38, Color.red(waveColor), Color.green(waveColor), Color.blue(waveColor)),
                    Color.argb(5, Color.red(waveColor), Color.green(waveColor), Color.blue(waveColor)),
                    Color.TRANSPARENT
                ), null, Shader.TileMode.CLAMP)
                glowPaint.shader = glowShader
                canvas.drawPath(glowPath, glowPaint)
                glowPaint.shader = null
            }
        }
    }

    private fun drawGrid(canvas: Canvas, w: Float, h: Float, gridSize: Float, color: Int) {
        gridPaint.color = color
        gridPaint.strokeWidth = 1f
        var y = 0f
        while (y < h) {
            canvas.drawLine(0f, y, w, y, gridPaint)
            y += gridSize
        }
        var x = 0f
        while (x < w) {
            canvas.drawLine(x, 0f, x, h, gridPaint)
            x += gridSize
        }
    }
}
