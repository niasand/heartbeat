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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heartratemonitor.ui.theme.AppColors
import com.heartratemonitor.ui.components.HeartRateWaveView
import com.heartratemonitor.viewmodel.HeartRateViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.heartratemonitor.data.entity.HeartRateEntity
import com.heartratemonitor.data.dao.DailyHeartRateStats

import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 历史心率屏幕 - 简化版本
 */
@Composable
fun HeartRateHistoryScreen(viewModel: HeartRateViewModel = viewModel()) {
    // 1. 获取所有数据（用于图表）
    val allHeartRateHistory by viewModel.heartRateHistory.collectAsState()

    // 2. 获取最近600条数据（用于列表）
    val recentHeartRateHistory = remember(allHeartRateHistory) {
        allHeartRateHistory.take(600)
    }

    val heartRateStats by viewModel.heartRateStats.collectAsState()
    val dailyStats by viewModel.dailyStats.collectAsState()

    var showDailyStats by remember { mutableStateOf(false) }

    // 固定标题
    val chartTitle = "过去12小时心率趋势"

    // 过去12小时心率数据，降采样为 ~120 个点（每 6 分钟一个点）
    val twelveHoursAgo = System.currentTimeMillis() - 12 * 60 * 60 * 1000L
    val waveHrData = remember(allHeartRateHistory) {
        val entities = allHeartRateHistory.filter { it.timestamp >= twelveHoursAgo }
        if (entities.isEmpty()) {
            emptyList()
        } else {
            val bucketCount = 120
            val minTs = entities.minOf { it.timestamp }
            val maxTs = entities.maxOf { it.timestamp }
            val range = (maxTs - minTs).coerceAtLeast(1L)
            val bucketSize = range / bucketCount
            (0 until bucketCount).map { i ->
                val bucketStart = minTs + i * bucketSize
                val bucketEnd = if (i == bucketCount - 1) maxTs + 1 else bucketStart + bucketSize
                entities
                    .filter { it.timestamp in bucketStart until bucketEnd }
                    .map { it.heartRate }
                    .average()
                    .toInt()
                    .coerceIn(40, 220)
            }
        }
    }

    // 波形时间范围文本
    val waveTimeRange = remember(allHeartRateHistory) {
        if (allHeartRateHistory.isEmpty()) "" else {
            val sdf = SimpleDateFormat("HH:mm", Locale.CHINA)
            "${sdf.format(twelveHoursAgo)} - ${sdf.format(System.currentTimeMillis())}"
        }
    }

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
        if (waveHrData.isNotEmpty() || dailyStats.isNotEmpty()) {
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
                    } else if (waveHrData.isNotEmpty()) {
                        // 过去12小时心率波形
                        if (waveTimeRange.isNotEmpty()) {
                            Text(
                                text = waveTimeRange,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }

                        HeartRateWaveView(
                            heartRateHistory = waveHrData,
                            modifier = Modifier
                                .fillMaxSize(),
                            waveColor = Color(0xFFEE4000),
                            fixedHeight = null,
                            showYAxis = true,
                            dataUpdateKey = allHeartRateHistory.size
                        )
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
