package com.heartratemonitor.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heartratemonitor.viewmodel.HeartRateViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerHistoryScreen(viewModel: HeartRateViewModel) {
    val sessions by viewModel.timerSessionHistory.collectAsState()
    val countByDate by viewModel.timerCountByDate.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top half: bar chart
        TimerBarChart(
            countByDate = countByDate,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )

        HorizontalDivider()

        // Bottom half: history list
        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无计时记录",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(sessions) { session ->
                    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
                        .format(Date(session.timestamp))
                    val mins = session.durationSeconds / 60
                    val secs = session.durationSeconds % 60
                    val durationStr = if (mins > 0) "${mins}m${secs}s" else "${secs}s"

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = dateStr,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = durationStr,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimerBarChart(
    countByDate: List<com.heartratemonitor.data.dao.DateCountPair>,
    modifier: Modifier = Modifier
) {
    if (countByDate.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
        return
    }

    val recentData = countByDate.takeLast(7)
    if (recentData.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
        return
    }

    val maxCount = (recentData.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
    val barAreaHeight = 140.dp
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)

    Column(modifier = modifier) {
        Text(
            text = "计时统计",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // 柱状图 + 坐标轴
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barAreaHeight)
        ) {
            // 画坐标轴: Y轴(左侧竖线) + X轴(底部横线)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 1.dp.toPx()
                drawLine(
                    color = axisColor,
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(0f, size.height),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = axisColor,
                    start = androidx.compose.ui.geometry.Offset(0f, size.height),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }

            // 柱状图（加左内边距避开Y轴）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp)
                    .fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                recentData.forEach { pair ->
                    val barHeight = (pair.count.toFloat() / maxCount) * (barAreaHeight.value - 16) // 留空间给计数标签
                        .coerceAtLeast(1f)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Count label
                        Text(
                            text = "${pair.count}次",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        // Bar
                        Box(
                            modifier = Modifier
                                .width(28.dp)
                                .height(barHeight.dp.coerceAtLeast(4.dp))
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.primary,
                                shape = MaterialTheme.shapes.extraSmall
                            ) {}
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // X轴日期标签
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            recentData.forEach { pair ->
                val dateLabel = pair.date.substring(5) // MM-DD
                Text(
                    text = dateLabel,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}
