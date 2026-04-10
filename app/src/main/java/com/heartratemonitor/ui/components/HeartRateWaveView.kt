package com.heartratemonitor.ui.components

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
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

    // Long press indicator
    private var selectedX: Float = -1f
    private var selectedIndex: Int = -1
    private val autoHideHandler = Handler(Looper.getMainLooper())
    private val autoHideRunnable = Runnable { clearSelection() }

    private val gestureDetector: GestureDetector

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
    // Indicator paints
    private val indicatorLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = Color.argb(180, 255, 255, 255)
        setPathEffect(DashPathEffect(floatArrayOf(6f, 4f), 0f))
    }
    private val indicatorDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val indicatorRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = waveColor
    }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 30, 30, 30)
        style = Paint.Style.FILL
    }
    private val bubbleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val bubbleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 28f
        typeface = Typeface.DEFAULT_BOLD
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }

    init {
        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                handleTouch(e.x)
            }
            override fun onDown(e: MotionEvent): Boolean {
                handleTouch(e.x)
                return true
            }
            override fun onScroll(
                e1: MotionEvent?, e2: MotionEvent,
                dx: Float, dy: Float
            ): Boolean {
                if (selectedIndex >= 0) handleTouch(e2.x)
                return true
            }
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                handleTouch(e.x)
                scheduleAutoHide()
                return true
            }
        })
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        when (event.action) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> scheduleAutoHide()
        }
        return true
    }

    private fun handleTouch(touchX: Float) {
        if (data.isEmpty()) return
        val w = width.toFloat()
        val leftPad = labelWidthPx
        val rightPad = 4f
        val pointCount = data.size.coerceAtLeast(2)
        val stepX = (w - leftPad - rightPad) / (pointCount - 1)

        val index = ((touchX - leftPad) / stepX).toInt()
            .coerceIn(0, data.lastIndex)
        val snappedX = leftPad + index * stepX

        if (index != selectedIndex || touchX != selectedX) {
            selectedIndex = index
            selectedX = snappedX
            autoHideHandler.removeCallbacks(autoHideRunnable)
            invalidate()
        }
    }

    private fun scheduleAutoHide() {
        autoHideHandler.removeCallbacks(autoHideRunnable)
        autoHideHandler.postDelayed(autoHideRunnable, 2000)
    }

    private fun clearSelection() {
        selectedIndex = -1
        selectedX = -1f
        invalidate()
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
        this.data = data.reversed() // 反转使 index 0=最旧(左), index N=最新(右)
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

            // Latest value indicator (rightmost point = newest data)
            if (data.isNotEmpty()) {
                val lastHr = data.last()
                val lastX = w - rightPad
                val normalizedHr = (lastHr - minVal) / range
                val lastY = bottomPad - normalizedHr * chartHeight

                // Dot
                canvas.drawCircle(lastX, lastY, 5f, indicatorDotPaint)
                canvas.drawCircle(lastX, lastY, 7f, indicatorRingPaint)

                // Value label: "65" — always above the dot
                val labelText = "$lastHr"
                bubbleTextPaint.textSize = 32f
                val labelWidth = bubbleTextPaint.measureText(labelText)
                val lbW = labelWidth + 20f
                val lbH = 38f
                val lbX = (lastX - lbW / 2f).coerceIn(leftPad, w - rightPad - lbW)
                val lbY = (lastY - lbH - 12f).coerceAtLeast(topPad)

                val lbRect = RectF(lbX, lbY, lbX + lbW, lbY + lbH)
                canvas.drawRoundRect(lbRect, 6f, 6f, bubblePaint)
                canvas.drawRoundRect(lbRect, 6f, 6f, bubbleStrokePaint)
                canvas.drawText(labelText, lbRect.centerX(), lbRect.centerY() + 11f, bubbleTextPaint)
            }

            // Long press indicator
            if (selectedIndex in data.indices && selectedX >= 0f) {
                val hr = data[selectedIndex]
                val normalizedHr = (hr - minVal) / range
                val dotY = bottomPad - normalizedHr * chartHeight

                // Vertical dashed line
                canvas.drawLine(selectedX, topPad, selectedX, h, indicatorLinePaint)

                // Dot on the curve
                canvas.drawCircle(selectedX, dotY, 6f, indicatorDotPaint)
                canvas.drawCircle(selectedX, dotY, 8f, indicatorRingPaint)

                // Bubble: "65 BPM"
                val label = "$hr BPM"
                bubbleTextPaint.textSize = 28f
                val textWidth = bubbleTextPaint.measureText(label)
                val bubbleW = textWidth + 24f
                val bubbleH = 40f
                val bubbleX = (selectedX - bubbleW / 2f).coerceIn(leftPad, w - rightPad - bubbleW)
                val bubbleY = dotY - bubbleH - 16f

                // Rounded rect background
                val bubbleRect = RectF(bubbleX, bubbleY, bubbleX + bubbleW, bubbleY + bubbleH)
                canvas.drawRoundRect(bubbleRect, 8f, 8f, bubblePaint)
                canvas.drawRoundRect(bubbleRect, 8f, 8f, bubbleStrokePaint)

                // Text
                canvas.drawText(label, bubbleRect.centerX(), bubbleRect.centerY() + 10f, bubbleTextPaint)
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
