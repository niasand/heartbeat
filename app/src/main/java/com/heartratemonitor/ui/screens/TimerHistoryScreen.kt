package com.heartratemonitor.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heartratemonitor.ui.theme.AppColors
import com.heartratemonitor.viewmodel.HeartRateViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

private val filterOptions = listOf(
    "7天" to 7,
    "30天" to 30,
    "6个月" to 180,
    "1年" to 365
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TimerHistoryScreen(viewModel: HeartRateViewModel) {
    val sessions by viewModel.filteredTimerSessions.collectAsState()
    val context = LocalContext.current
    val countByDate by viewModel.filteredTimerCountByDate.collectAsState()
    val countByMonth by viewModel.filteredTimerCountByMonth.collectAsState()
    val currentFilter by viewModel.timerFilterDays.collectAsState()
    val currentTagFilter by viewModel.timerFilterTag.collectAsState()
    val allSessionsInRange by viewModel.sessionsInTimeRange.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Filter tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            filterOptions.forEach { (label, days) ->
                val selected = currentFilter == days
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) AppColors.HeartRateHigh else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { viewModel.setTimerFilterDays(days) }
                )
            }
        }

        // Bar chart — switch data source based on filter range
        val isMonthly = currentFilter >= 180
        TimerBarChart(
            countByDate = if (isMonthly) countByMonth else countByDate,
            isMonthly = isMonthly,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Table header
        val availableTags = allSessionsInRange.mapNotNull { it.tag }.distinct()
        var tagMenuExpanded by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("日期", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.weight(1f))
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = if (currentTagFilter.isNullOrBlank()) "计时事件" else "计时事件($currentTagFilter)",
                    fontSize = 12.sp,
                    fontWeight = if (currentTagFilter != null) FontWeight.Bold else FontWeight.Normal,
                    color = if (currentTagFilter != null) AppColors.HeartRateHigh else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.clickable { tagMenuExpanded = true }
                )
                DropdownMenu(
                    expanded = tagMenuExpanded,
                    onDismissRequest = { tagMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("全部", fontSize = 14.sp) },
                        onClick = {
                            viewModel.setTimerFilterTag(null)
                            tagMenuExpanded = false
                        }
                    )
                    availableTags.forEach { tag ->
                        DropdownMenuItem(
                            text = { Text(tag, fontSize = 14.sp) },
                            onClick = {
                                viewModel.setTimerFilterTag(tag)
                                tagMenuExpanded = false
                            }
                        )
                    }
                }
            }
            Text("计时时间", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }

        // History list
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
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                items(sessions) { session ->
                    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
                        .format(Date(session.timestamp))
                    val mins = session.durationSeconds / 60
                    val secs = session.durationSeconds % 60
                    val durationStr = if (mins > 0) "${mins}m${secs}s" else "${secs}s"

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .combinedClickable(
                                onClick = { },
                                onLongClick = {
                                    viewModel.deleteTimerSession(session.timestamp)
                                }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = dateStr,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (!session.tag.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                ) {
                                    Text(
                                        text = session.tag,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
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
    modifier: Modifier = Modifier,
    isMonthly: Boolean = false
) {
    if (countByDate.isEmpty()) {
        Box(modifier = modifier.padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
            Text("暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
        return
    }

    val maxCount = (countByDate.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
    val barAreaHeight = 140.dp
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)

    // Daily view: limit to 14 bars; Monthly view: show all (max ~12 bars)
    val displayData = if (isMonthly) countByDate else if (countByDate.size > 14) countByDate.takeLast(14) else countByDate

    Column(modifier = modifier) {
        Text(
            text = "计时次数",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Bar chart + axes
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barAreaHeight)
        ) {
            // Draw axes
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

            // Bars
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp)
                    .fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                displayData.forEach { pair ->
                    val barHeight = (pair.count.toFloat() / maxCount) * (barAreaHeight.value - 16)
                        .coerceAtLeast(1f)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "${pair.count}次",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
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

        // X-axis date labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            displayData.forEach { pair ->
                val dateLabel = if (isMonthly) {
                    // "yyyy-MM" → "M月" (e.g. "2026-01" → "1月")
                    pair.date.substring(5).replaceFirst("^0".toRegex(), "") + "月"
                } else {
                    pair.date.substring(5) // MM-DD
                }
                Text(
                    text = dateLabel,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
