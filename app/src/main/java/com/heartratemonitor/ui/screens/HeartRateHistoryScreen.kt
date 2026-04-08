package com.heartratemonitor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heartratemonitor.ui.theme.AppColors
import com.heartratemonitor.viewmodel.HeartRateViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.heartratemonitor.data.entity.HeartRateEntity
import com.heartratemonitor.data.dao.DailyHeartRateStats

import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.entry.entryModelOf
import com.patrykandpatrick.vico.core.entry.FloatEntry
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import java.text.SimpleDateFormat
import java.util.Locale

import com.patrykandpatrick.vico.core.marker.Marker
import com.patrykandpatrick.vico.compose.component.shapeComponent
import com.patrykandpatrick.vico.compose.component.textComponent
import com.patrykandpatrick.vico.core.component.shape.Shapes
import com.patrykandpatrick.vico.core.component.shape.cornered.Corner
import com.patrykandpatrick.vico.core.context.MeasureContext
import com.patrykandpatrick.vico.core.extension.copyColor
import com.patrykandpatrick.vico.core.marker.MarkerLabelFormatter
import com.patrykandpatrick.vico.compose.dimensions.dimensionsOf
import com.patrykandpatrick.vico.core.chart.dimensions.HorizontalDimensions
import com.patrykandpatrick.vico.core.chart.insets.Insets
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.core.component.shape.shader.DynamicShaders
import com.patrykandpatrick.vico.compose.component.shape.shader.fromBrush
import com.patrykandpatrick.vico.core.chart.values.AxisValuesOverrider
import com.patrykandpatrick.vico.compose.chart.scroll.rememberChartScrollSpec
import com.patrykandpatrick.vico.core.scroll.InitialScroll
import com.patrykandpatrick.vico.core.scroll.AutoScrollCondition

/**
 * 历史心率屏幕 - 简化版本
 */
@Composable
fun rememberMarker(): Marker {
    val label = textComponent(
        color = MaterialTheme.colorScheme.onSurface,
        background = shapeComponent(
            shape = Shapes.pillShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            strokeColor = MaterialTheme.colorScheme.outline,
            strokeWidth = 1.dp,
        ),
        padding = dimensionsOf(8.dp, 4.dp),
        typeface = android.graphics.Typeface.MONOSPACE
    )
    val indicator = shapeComponent(
        shape = Shapes.pillShape,
        color = MaterialTheme.colorScheme.primary,
        strokeColor = MaterialTheme.colorScheme.surface,
        strokeWidth = 2.dp,
    )
    val guideline = shapeComponent(
        shape = Shapes.pillShape,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
    )
    
    return remember(label, indicator, guideline) {
        object : Marker {
            override fun draw(
                context: com.patrykandpatrick.vico.core.context.DrawContext,
                bounds: android.graphics.RectF,
                markedEntries: List<com.patrykandpatrick.vico.core.marker.Marker.EntryModel>,
                chartValuesProvider: com.patrykandpatrick.vico.core.chart.values.ChartValuesProvider
            ) {
                with(context) {
                    drawGuideline(context, bounds, markedEntries)
                    val halfIndicatorSize = 16f / 2f // 8dp equivalent
                    
                    markedEntries.forEach { entry ->
                        val x = entry.location.x
                        val y = entry.location.y
                        
                        indicator.draw(
                            context,
                            x - halfIndicatorSize,
                            y - halfIndicatorSize,
                            x + halfIndicatorSize,
                            y + halfIndicatorSize
                        )
                        
                        val labelText = "${entry.entry.y.toInt()} BPM"
                        val labelWidth = label.getTextBounds(context, labelText).width()
                        val labelHeight = label.getTextBounds(context, labelText).height()
                        
                        // Simple label positioning above the point
                        val labelX = x - labelWidth / 2
                        val labelY = y - labelHeight - halfIndicatorSize - 10f
                        
                        label.drawText(
                            context,
                            labelText,
                            labelX,
                            labelY,
                            maxTextWidth = bounds.width().toInt()
                        )
                    }
                }
            }
            
            private fun drawGuideline(
                context: com.patrykandpatrick.vico.core.context.DrawContext,
                bounds: android.graphics.RectF,
                markedEntries: List<com.patrykandpatrick.vico.core.marker.Marker.EntryModel>,
            ) {
                markedEntries
                    .map { it.location.x }
                    .toSet()
                    .forEach { x ->
                        guideline.draw(
                            context,
                            x,
                            bounds.top,
                            x,
                            bounds.bottom,
                        )
                    }
            }
        }
    }
}

@Composable
fun HeartRateHistoryScreen(viewModel: HeartRateViewModel = viewModel()) {
    // 1. 获取所有数据（用于图表）
    val allHeartRateHistory by viewModel.heartRateHistory.collectAsState()

    // 2. 获取最近600条数据（用于列表）
    val recentHeartRateHistory = remember(allHeartRateHistory) {
        allHeartRateHistory.take(600)
    }

    // 3. 预过滤最近24小时数据（用于图表），仅在数据变化时计算，避免 tick 触发全量扫描
    val recentDayData = remember(allHeartRateHistory) {
        val oneDayAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        allHeartRateHistory.filter { it.timestamp >= oneDayAgo }
    }

    val heartRateStats by viewModel.heartRateStats.collectAsState()
    val dailyStats by viewModel.dailyStats.collectAsState()

    var showDailyStats by remember { mutableStateOf(false) }

    // 每5秒 tick 一次，确保新心率数据及时反映到图表
    // baseTime 已对齐到分钟边界，不会因 tick 频繁变化而横跳
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(5_000L)
            tick++
        }
    }

    // 图表数据：过去24小时，每1分钟一个点（取最大心率），基于预过滤数据确保实时更新
    // baseTime 对齐到分钟边界，确保 x 坐标在两次 tick 之间不会偏移
    val (entries, baseTime) = remember(recentDayData, tick) {
        if (recentDayData.isEmpty()) {
            Pair(emptyList<FloatEntry>(), 0L)
        } else {
            val chartWindowStart = (System.currentTimeMillis() / 60_000L) * 60_000L - 24 * 60 * 60 * 1000L
            val chartData = recentDayData.filter { it.timestamp >= chartWindowStart }
            if (chartData.isEmpty()) {
                Pair(emptyList<FloatEntry>(), 0L)
            } else {
                val baseTime = chartWindowStart
                val interval = 60 * 1000L // 每1分钟一个点
                val groupedData = chartData.groupBy { (it.timestamp / interval) * interval }
                val data = groupedData.map { (timestamp, entities) ->
                    val maxHeartRate = entities.maxOf { it.heartRate }.toFloat()
                    val xValue = ((timestamp - baseTime) / 60000f)
                    FloatEntry(xValue, maxHeartRate)
                }.sortedBy { it.x }
                Pair(data, baseTime)
            }
        }
    }

    // X轴由 Vico 自动计算，填满整个图表宽度

    // 时间范围显示（随 tick 更新 endTime）
    val timeRangeText = remember(baseTime, tick) {
        if (baseTime > 0) {
            val sdf = SimpleDateFormat("HH:mm", Locale.CHINA)
            val startTime = sdf.format(baseTime)
            val endTime = sdf.format(System.currentTimeMillis())
            "$startTime - $endTime"
        } else {
            ""
        }
    }

    // 固定标题
    val chartTitle = "过去24小时心率趋势"

    // Handle empty data case for stats
    val displayStats = heartRateStats ?: HeartRateViewModel.HeartRateStats(0.0, 0, 0, 0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 统计信息卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("最高", displayStats.max.toString(), AppColors.HeartRateHigh)
                StatItem("平均", displayStats.avg.toInt().toString(), AppColors.Primary)
                StatItem("最低", displayStats.min.toString(), AppColors.HeartRateNormal)
            }
        }

        // 折线图卡片
        if (entries.isNotEmpty() || dailyStats.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // 标题 + 切换按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (showDailyStats) "过去7天心率统计" else chartTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "BySec",
                                fontSize = 13.sp,
                                fontWeight = if (!showDailyStats) FontWeight.Bold else FontWeight.Normal,
                                color = if (!showDailyStats) AppColors.HeartRateHigh else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clickable { showDailyStats = false }
                            )
                            Text(
                                text = "ByDay",
                                fontSize = 13.sp,
                                fontWeight = if (showDailyStats) FontWeight.Bold else FontWeight.Normal,
                                color = if (showDailyStats) AppColors.HeartRateHigh else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clickable { showDailyStats = true }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (showDailyStats) {
                        // 每日统计柱状图
                        DailyHeartRateChart(dailyStats = dailyStats)
                    } else if (entries.isNotEmpty()) {
                        // 实时心率波形（Canvas 绘制，类似医院监护仪）
                        if (timeRangeText.isNotEmpty()) {
                            Text(
                                text = timeRangeText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }

                        // 提取最近 300 个心率值用于波形显示
                        val recentHrValues = remember(allHeartRateHistory) {
                            allHeartRateHistory.takeLast(300).map { it.heartRate }
                        }

                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1A1A2E))
                        ) {
                            val dataCount = recentHrValues.size
                            if (dataCount < 2) return@Canvas

                            // Y 轴映射：心率 50-220 映射到画布高度
                            val minHr = 50f
                            val maxHr = 220f
                            val hrRange = maxHr - minHr
                            val padding = 30f // 上下留白
                            val drawableHeight = size.height - padding * 2

                            // 画背景网格
                            val gridPaint = androidx.compose.ui.graphics.Paint().apply {
                                color = Color(0xFF2A2A4A)
                                strokeWidth = 1f
                            }
                            val gridSpacing = 40f
                            // 横线
                            var gy = padding
                            while (gy < size.height - padding) {
                                drawLine(Color(0xFF2A2A4A), androidx.compose.ui.geometry.Offset(0f, gy), androidx.compose.ui.geometry.Offset(size.width, gy), strokeWidth = 0.5f)
                                gy += gridSpacing
                            }
                            // 竖线
                            var gx = 0f
                            while (gx < size.width) {
                                drawLine(Color(0xFF2A2A4A), androidx.compose.ui.geometry.Offset(gx, padding), androidx.compose.ui.geometry.Offset(gx, size.height - padding), strokeWidth = 0.5f)
                                gx += gridSpacing
                            }

                            // 画心率曲线
                            val path = Path()
                            val stepX = size.width / 300f
                            val startOffset = 300 - dataCount

                            for (i in 0 until dataCount) {
                                val hr = recentHrValues[i].toFloat()
                                val normalized = (hr - minHr) / hrRange
                                val clamped = normalized.coerceIn(0f, 1f)
                                val x = (startOffset + i) * stepX
                                val y = padding + drawableHeight * (1f - clamped) // 高心率在上方
                                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }

                            drawPath(
                                path = path,
                                color = Color(0xFFEE4000),
                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("暂无趋势数据", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        }
                    }
                }
            }
        } else {
             Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无数据，请先连接设备并开始监测")
            }
        }

        // 心率历史列表
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(16.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(recentHeartRateHistory) { entity ->
                    HistoryItem(
                        time = viewModel.formatDate(entity.timestamp),
                        heartRate = entity.heartRate
                    )
                }
            }
        }

        // 数据点信息
        if (recentHeartRateHistory.isNotEmpty()) {
            Text(
                text = "最近 ${recentHeartRateHistory.size} 条数据",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun StatItem(label: String, value: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = "BPM",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun HistoryItem(time: String, heartRate: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = time,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "$heartRate",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "BPM",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 每日心率统计柱状图 - 过去7天
 * 每天显示3根柱子：最高(红)、平均(蓝)、最低(绿)
 */
@Composable
private fun DailyHeartRateChart(dailyStats: List<DailyHeartRateStats>) {
    if (dailyStats.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无每日数据", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
        return
    }

    val maxBpm = dailyStats.maxOf { it.maxHeartRate }.coerceAtLeast(1)
    // Y轴刻度：向上取整到10的倍数
    val yAxisMax = ((maxBpm + 9) / 10 * 10).coerceAtLeast(100)
    val chartHeight = 180.dp
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    val highColor = AppColors.HeartRateHigh  // 橙
    val avgColor = AppColors.Primary          // 紫
    val lowColor = AppColors.HeartRateNormal   // 绿

    Column(modifier = Modifier.fillMaxSize()) {
        // 柱状图 + 坐标轴
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // 画坐标轴 + Y轴刻度
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 1.dp.toPx()
                // Y 轴
                drawLine(
                    color = axisColor,
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(0f, size.height),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                // X 轴
                drawLine(
                    color = axisColor,
                    start = androidx.compose.ui.geometry.Offset(0f, size.height),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }

            // Y轴刻度标签（绝对定位在左侧）
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(end = 4.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${yAxisMax}", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${yAxisMax * 3 / 4}", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${yAxisMax / 2}", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${yAxisMax / 4}", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("0", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // 柱状图内容
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 28.dp, end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                dailyStats.forEach { day ->
                    val highH = (day.maxHeartRate.toFloat() / yAxisMax) * (chartHeight.value - 20)
                    val avgH = (day.avgHeartRate.toFloat() / yAxisMax) * (chartHeight.value - 20)
                    val lowH = (day.minHeartRate.toFloat() / yAxisMax) * (chartHeight.value - 20)

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        // 3根柱子并排
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            // 最低
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${day.minHeartRate}", fontSize = 7.sp, color = lowColor, maxLines = 1)
                                Box(
                                    modifier = Modifier
                                        .width(10.dp)
                                        .height(lowH.dp.coerceAtLeast(2.dp))
                                        .background(lowColor, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                )
                            }
                            // 平均
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${day.avgHeartRate.toInt()}", fontSize = 7.sp, color = avgColor, maxLines = 1)
                                Box(
                                    modifier = Modifier
                                        .width(10.dp)
                                        .height(avgH.dp.coerceAtLeast(2.dp))
                                        .background(avgColor, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                )
                            }
                            // 最高
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${day.maxHeartRate}", fontSize = 7.sp, color = highColor, maxLines = 1)
                                Box(
                                    modifier = Modifier
                                        .width(10.dp)
                                        .height(highH.dp.coerceAtLeast(2.dp))
                                        .background(highColor, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // 日期标签
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 28.dp, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            dailyStats.forEach { day ->
                val dateLabel = if (day.date.length >= 10) day.date.substring(5) else day.date
                Text(
                    text = dateLabel,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 图例
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendDot(color = highColor, label = "最高")
            Spacer(modifier = Modifier.width(16.dp))
            LegendDot(color = avgColor, label = "平均")
            Spacer(modifier = Modifier.width(16.dp))
            LegendDot(color = lowColor, label = "最低")
        }
    }
}

@Composable
private fun RowScope.LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(8.dp).background(color, RoundedCornerShape(2.dp)))
        Text(text = label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
