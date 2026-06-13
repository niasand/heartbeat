package com.heartratemonitor.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import android.widget.Toast
import com.heartratemonitor.ui.components.MiniHeartRateChart
import com.heartratemonitor.R
import com.heartratemonitor.ui.theme.AppColors
import com.heartratemonitor.viewmodel.HeartRateViewModel
import com.heartratemonitor.ble.ConnectionState
import com.heartratemonitor.ble.AutoReconnectState
import com.heartratemonitor.ui.screens.SettingsActivity
import android.os.Build
import android.Manifest
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.heartratemonitor.ble.BleScanner
import com.heartratemonitor.data.entity.AlarmRecordEntity
import com.heartratemonitor.service.TimerCountdownService
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.outlined.Notifications
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val DEFAULT_TIMER_SECONDS = "40"
private const val DEFAULT_TIMER_SECONDS_INT = 40

/**
 * 主屏幕 - 心率监测
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeartRateScreen(viewModel: HeartRateViewModel = viewModel()) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    var connectAttemptId by remember { mutableIntStateOf(0) }

    // 自动连接状态在 RealTimeHeartRateScreen 内读取（该处才使用），避免此处重复 collect

    // 倒计时本地输入状态
    var timerInputMinutes by remember { mutableStateOf("0") }
    var timerInputSeconds by remember { mutableStateOf(DEFAULT_TIMER_SECONDS) }
    var timerTagInput by remember { mutableStateOf("平板支撑") }

    // 倒计时运行状态从 TimerCountdownService 派生
    val timerServiceState by viewModel.timerServiceState.collectAsState()

    val inputComputedTotal = (timerInputMinutes.toIntOrNull() ?: 0) * 60 + (timerInputSeconds.toIntOrNull() ?: 0)

    val timerTotalSeconds: Int = when (val s = timerServiceState) {
        is TimerCountdownService.TimerServiceState.RUNNING -> s.totalSeconds
        is TimerCountdownService.TimerServiceState.PAUSED -> s.totalSeconds
        is TimerCountdownService.TimerServiceState.COMPLETED -> s.totalSeconds
        is TimerCountdownService.TimerServiceState.IDLE -> if (inputComputedTotal > 0) inputComputedTotal else DEFAULT_TIMER_SECONDS_INT
    }

    val timerRemainingSeconds: Int = when (val s = timerServiceState) {
        is TimerCountdownService.TimerServiceState.RUNNING -> s.remainingSeconds
        is TimerCountdownService.TimerServiceState.PAUSED -> s.remainingSeconds
        is TimerCountdownService.TimerServiceState.COMPLETED -> 0
        is TimerCountdownService.TimerServiceState.IDLE -> timerTotalSeconds
    }

    val timerIsRunning: Boolean = timerServiceState is TimerCountdownService.TimerServiceState.RUNNING

    // 读取硅基流动 API Key
    val siliconFlowApiKey by viewModel.siliconFlowApiKey.collectAsState()

    // 倒计时完成：Toast + 重置输入
    // 用 wasTimerActive 替代 wasTimerCompleted，避免后台完成时错过 COMPLETED 状态导致输入不重置
    var wasTimerActive by remember { mutableStateOf(false) }
    LaunchedEffect(timerServiceState) {
        when (timerServiceState) {
            is TimerCountdownService.TimerServiceState.RUNNING,
            is TimerCountdownService.TimerServiceState.PAUSED -> {
                wasTimerActive = true
            }
            is TimerCountdownService.TimerServiceState.COMPLETED -> {
                wasTimerActive = true
                Toast.makeText(context, "倒计时结束！", Toast.LENGTH_LONG).show()
            }
            is TimerCountdownService.TimerServiceState.IDLE -> {
                if (wasTimerActive) {
                    wasTimerActive = false
                    timerInputMinutes = "0"
                    timerInputSeconds = DEFAULT_TIMER_SECONDS
                }
            }
        }
    }

    // Start scanning when the screen is first shown? Maybe not, better on button click.
    
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.app_name),
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = {
                        val intent = android.content.Intent(context, SettingsActivity::class.java)
                        context.startActivity(intent)
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.settings)
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Favorite, contentDescription = null) },
                    label = { Text("实时") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text(stringResource(R.string.history)) },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Outlined.Notifications, contentDescription = null) },
                    label = { Text("计时") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )
            }
        }
    ) { padding ->
        // 使用Box而不是Column，以避免fillMaxSize导致的布局问题
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                0 -> RealTimeHeartRateScreen(
                    viewModel,
                    connectAttemptId,
                    { connectAttemptId = it },
                    TimerState(
                        totalSeconds = timerTotalSeconds,
                        remainingSeconds = timerRemainingSeconds,
                        isRunning = timerIsRunning,
                        inputMinutes = timerInputMinutes,
                        inputSeconds = timerInputSeconds,
                        tagInput = timerTagInput,
                        onTotalSecondsChange = {}, // derived from service state or inputs
                        onRemainingSecondsChange = {}, // derived from service state or inputs
                        onIsRunningChange = { wantRunning ->
                            when {
                                wantRunning && timerServiceState is TimerCountdownService.TimerServiceState.RUNNING -> {
                                    // already running, no-op
                                }
                                wantRunning && timerServiceState is TimerCountdownService.TimerServiceState.PAUSED -> {
                                    val serviceTotal = (timerServiceState as TimerCountdownService.TimerServiceState.PAUSED).totalSeconds
                                    if (inputComputedTotal == serviceTotal && inputComputedTotal > 0) {
                                        viewModel.resumeTimerService()
                                    } else if (inputComputedTotal > 0) {
                                        viewModel.startTimerService(inputComputedTotal, timerTagInput.ifBlank { null })
                                        timerTagInput.ifBlank { null }?.let { viewModel.addRecentTimerTag(it) }
                                    }
                                }
                                wantRunning -> {
                                    // IDLE or COMPLETED — start new timer
                                    if (inputComputedTotal > 0) {
                                        viewModel.startTimerService(inputComputedTotal, timerTagInput.ifBlank { null })
                                        timerTagInput.ifBlank { null }?.let { viewModel.addRecentTimerTag(it) }
                                    }
                                }
                                else -> {
                                    // wantRunning == false — pause if running
                                    if (timerServiceState is TimerCountdownService.TimerServiceState.RUNNING) {
                                        viewModel.pauseTimerService()
                                    }
                                }
                            }
                        },
                        onInputMinutesChange = { timerInputMinutes = it },
                        onInputSecondsChange = { timerInputSeconds = it },
                        onTagInputChange = { timerTagInput = it }
                    ),
                    siliconFlowApiKey ?: ""
                )
                1 -> HeartRateHistoryScreen(viewModel)
                2 -> TimerHistoryScreen(viewModel)
            }
        }
    }
}

@Composable
private fun ActiveAlarmCard(
    alarm: AlarmRecordEntity,
    onCancel: () -> Unit
) {
    var nowMillis by remember(alarm.id) { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(alarm.id) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000L)
        }
    }

    val remainingSeconds = ((alarm.targetTimeMillis - nowMillis) / 1000L).coerceAtLeast(0L)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "当前闹钟",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.72f)
                )
                Text(
                    text = alarm.label,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = "${formatAlarmTargetTime(alarm.targetTimeMillis)} · 剩余 ${formatAlarmRemainingDuration(remainingSeconds)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.72f)
                )
            }
            TextButton(onClick = onCancel) {
                Text("取消")
            }
        }
    }
}

private fun formatAlarmRemainingDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L

    return when {
        hours > 0L -> "${hours}小时${minutes.toString().padStart(2, '0')}分"
        minutes > 0L -> "${minutes}分${seconds.toString().padStart(2, '0')}秒"
        else -> "${seconds}秒"
    }
}

/**
 * 倒计时状态（提升到 HeartRateScreen 以支持 Tab 切换后保持状态）
 */
data class TimerState(
    val totalSeconds: Int,
    val remainingSeconds: Int,
    val isRunning: Boolean,
    val inputMinutes: String,
    val inputSeconds: String,
    val tagInput: String,
    val onTotalSecondsChange: (Int) -> Unit,
    val onRemainingSecondsChange: (Int) -> Unit,
    val onIsRunningChange: (Boolean) -> Unit,
    val onInputMinutesChange: (String) -> Unit,
    val onInputSecondsChange: (String) -> Unit,
    val onTagInputChange: (String) -> Unit
)

/**
 * 实时心率屏幕
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun RealTimeHeartRateScreen(
    viewModel: HeartRateViewModel = viewModel(),
    connectAttemptId: Int,
    onConnectAttemptIdChange: (Int) -> Unit = {},
    timerState: TimerState,
    siliconFlowApiKey: String = ""
) {
    val context = LocalContext.current
    val currentHeartRate by viewModel.currentHeartRate.collectAsState()
    val recentHeartRates by viewModel.recentHeartRates.collectAsState()
    val recentTimerTags by viewModel.recentTimerTags.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val highThreshold by viewModel.highThreshold.collectAsState()
    val lowThreshold by viewModel.lowThreshold.collectAsState()
    val autoReconnectState by viewModel.autoReconnectState.collectAsState()
    val lastDeviceAddress by viewModel.lastDeviceAddress.collectAsState()
    val hasAutoConnectAttempted by viewModel.hasAutoConnectAttempted.collectAsState()
    val hasAutoConnectedDevice by viewModel.hasAutoConnectedDevice.collectAsState()
    val activeAlarmRecord by viewModel.activeAlarmRecord.collectAsState()

    // 权限状态（Android 13+ 需要 POST_NOTIFICATIONS 才能显示前台服务通知）
    val permissionsState = rememberMultiplePermissionsState(
        permissions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    )

    // 智能计时对话框状态
    var showVoiceInputDialog by remember { mutableStateOf(false) }
    var showAlarmInputDialog by remember { mutableStateOf(false) }

    // Track previous state to avoid showing toast when switching tabs
    var previousState by remember { mutableStateOf(connectionState) }

    // 只保留连接成功和连接失败的 Toast
    LaunchedEffect(connectionState) {
        when {
            connectionState is ConnectionState.CONNECTED && previousState !is ConnectionState.CONNECTED ->
                Toast.makeText(context, "已连接高驰心率带", Toast.LENGTH_SHORT).show()
            connectionState is ConnectionState.ERROR && previousState !is ConnectionState.ERROR ->
                Toast.makeText(context, "连接失败: ${(connectionState as ConnectionState.ERROR).message}", Toast.LENGTH_LONG).show()
        }
        previousState = connectionState
    }

    // 自动扫描并连接心率带（只执行一次）
    // 优先检查是否已连接，如果已连接则直接显示数据
    LaunchedEffect(permissionsState.allPermissionsGranted) {
        if (!permissionsState.allPermissionsGranted) return@LaunchedEffect
        if (hasAutoConnectAttempted) return@LaunchedEffect

        viewModel.markAutoConnectAttempted()

        // 首先检查是否已经连接了心率带
        if (connectionState is ConnectionState.CONNECTED) {
            viewModel.markAutoConnectedDevice()
            return@LaunchedEffect
        }

        // 未连接，等待一小段时间确保 lastDeviceAddress 从 DataStore 加载完成
        kotlinx.coroutines.delay(500)

        // 如果有保存的设备地址，直接尝试连接
        val savedAddress = lastDeviceAddress
        if (!savedAddress.isNullOrEmpty()) {
            viewModel.connectToDevice(savedAddress)
        } else {
            // 没有保存的设备地址，开始扫描
            viewModel.startScan()
        }
    }

    // 检测到意外断开时重置自动连接标记，允许重新连接
    LaunchedEffect(connectionState) {
        if (connectionState is ConnectionState.DISCONNECTED && hasAutoConnectAttempted && hasAutoConnectedDevice) {
            // 延迟一下再重试，避免和 BleConnectionManager 内部的自动重连冲突
            kotlinx.coroutines.delay(3000)
            if (connectionState is ConnectionState.DISCONNECTED) {
                viewModel.resetAutoConnectAttempted()
            }
        }
    }

    // 自动连接扫描到的第一个设备
    val scannedDevices by viewModel.scannedDevices.collectAsState()
    val scanState by viewModel.scanState.collectAsState()
    val savedDeviceAddress = lastDeviceAddress // 缓存避免重复获取
    
    LaunchedEffect(scannedDevices.size, connectionState) {
        if (hasAutoConnectedDevice) return@LaunchedEffect
        if (connectionState is ConnectionState.CONNECTED || connectionState is ConnectionState.CONNECTING) {
            viewModel.markAutoConnectedDevice()
            return@LaunchedEffect
        }

        // 如果正在扫描且找到了设备，自动连接第一个
        if (scannedDevices.isNotEmpty() && savedDeviceAddress.isNullOrEmpty()) {
            viewModel.markAutoConnectedDevice()
            val firstDevice = scannedDevices.first()
            viewModel.stopScan()
            viewModel.connectToDevice(firstDevice.address)
        }
    }

    var showDeviceList by remember { mutableStateOf(false) }
    var pendingDeviceScanAfterPermission by remember { mutableStateOf(false) }
    val canOpenDeviceScan = connectionState !is ConnectionState.CONNECTED &&
        connectionState !is ConnectionState.CONNECTING

    fun openDeviceScan() {
        if (!canOpenDeviceScan) return
        if (permissionsState.allPermissionsGranted) {
            showDeviceList = true
        } else {
            pendingDeviceScanAfterPermission = true
            permissionsState.launchMultiplePermissionRequest()
        }
    }

    LaunchedEffect(permissionsState.allPermissionsGranted, pendingDeviceScanAfterPermission, canOpenDeviceScan) {
        if (!canOpenDeviceScan) {
            pendingDeviceScanAfterPermission = false
        } else if (pendingDeviceScanAfterPermission && permissionsState.allPermissionsGranted) {
            pendingDeviceScanAfterPermission = false
            showDeviceList = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        val scanHint = when {
            connectionState is ConnectionState.CONNECTED -> null
            connectionState is ConnectionState.CONNECTING -> null
            !permissionsState.allPermissionsGranted -> "点击授权并扫描"
            scanState is BleScanner.ScanState.SCANNING -> "正在扫描设备..."
            else -> "点击扫描设备"
        }

        // 心率显示卡片；未连接时兼作扫描设备入口
        Card(
            modifier = Modifier
                .size(190.dp)
                .then(
                    if (canOpenDeviceScan) {
                        Modifier.clickable(onClick = ::openDeviceScan)
                    } else {
                        Modifier
                    }
                ),
            shape = RoundedCornerShape(24.dp),
            border = if (canOpenDeviceScan) {
                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
            } else {
                null
            },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 状态指示器
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            when (connectionState) {
                                is ConnectionState.CONNECTED ->
                                    AppColors.HeartRateNormal
                                is ConnectionState.CONNECTING ->
                                    AppColors.HeartRateHigh
                                else -> Color.Gray
                            }
                        )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 心率数值
                Text(
                    text = (currentHeartRate ?: "--").toString(),
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        currentHeartRate == null -> Color.Gray
                        currentHeartRate!! > highThreshold -> AppColors.HeartRateCritical
                        currentHeartRate!! < lowThreshold -> AppColors.Warning
                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                    }
                )

                // BPM标签
                Text(
                    text = stringResource(R.string.bpm),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )

                // 迷你心率折线图（最近样本），样本不足时占位不绘制
                if (recentHeartRates.size >= 2) {
                    Spacer(modifier = Modifier.height(6.dp))
                    MiniHeartRateChart(
                        samples = recentHeartRates,
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .height(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 连接状态
                Text(
                    text = when (connectionState) {
                        is ConnectionState.CONNECTED ->
                            stringResource(R.string.connected)
                        is ConnectionState.CONNECTING -> {
                            when (autoReconnectState) {
                                is AutoReconnectState.RECONNECTING -> "自动重连中..."
                                else -> stringResource(R.string.connecting)
                            }
                        }
                        else -> stringResource(R.string.disconnected)
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )

                if (scanHint != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = scanHint,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // 倒计时
        CountdownTimerCard(
            state = timerState,
            onShowVoiceDialog = { showVoiceInputDialog = true },
            onShowAlarmDialog = { showAlarmInputDialog = true },
            activeAlarm = activeAlarmRecord,
            onCancelAlarm = { alarm -> viewModel.cancelAlarm(alarm.id) },
            tags = recentTimerTags
        )
    }

    // 设备选择对话框
    if (showDeviceList) {
        // Auto start scan when dialog opens
        LaunchedEffect(Unit) {
            viewModel.startScan()
        }

        DeviceListDialog(
            devices = scannedDevices.toList(),
            isScanning = scanState is BleScanner.ScanState.SCANNING,
            onStartScan = { viewModel.startScan() },
            onStopScan = { viewModel.stopScan() },
            onDismiss = {
                viewModel.stopScan()
                showDeviceList = false
            },
            onDeviceSelected = { address ->
                viewModel.stopScan()
                showDeviceList = false
                viewModel.connectToDevice(address)
                onConnectAttemptIdChange(connectAttemptId + 1)
            }
        )
    }

    LaunchedEffect(connectAttemptId) {
        if (connectAttemptId == 0) return@LaunchedEffect
        kotlinx.coroutines.delay(15000)
        val state = viewModel.connectionState.value
        if (state is ConnectionState.CONNECTING) {
            viewModel.disconnect()
        }
    }

    // 语音输入对话框
    if (showVoiceInputDialog) {
        VoiceInputDialog(
            apiKey = siliconFlowApiKey,
            onDismiss = { showVoiceInputDialog = false },
            onResult = { result ->
                showVoiceInputDialog = false
                result?.let { voiceResult ->
                    timerState.onTagInputChange(voiceResult.eventName)
                    timerState.onInputMinutesChange(voiceResult.minutes.toString())
                    timerState.onInputSecondsChange(voiceResult.seconds.toString())
                    val total = voiceResult.minutes * 60 + voiceResult.seconds
                    if (total > 0) {
                        // Start timer directly via ViewModel (bypasses the onIsRunningChange toggle logic)
                        viewModel.startTimerService(total, voiceResult.eventName.ifBlank { null })
                    }
                    Toast.makeText(
                        context,
                        "已设置: ${voiceResult.eventName}, ${voiceResult.minutes}分${voiceResult.seconds}秒",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }

    if (showAlarmInputDialog) {
        SmartAlarmInputDialog(
            apiKey = siliconFlowApiKey,
            onDismiss = { showAlarmInputDialog = false },
            onResult = { result ->
                showAlarmInputDialog = false
                result?.let { alarmResult ->
                    val targetTimeMillis = resolveAlarmTargetMillis(alarmResult)
                    val secondsUntilAlarm = (((targetTimeMillis - System.currentTimeMillis()) + 999L) / 1000L)
                        .coerceAtLeast(1L)
                        .coerceAtMost(Int.MAX_VALUE.toLong())
                        .toInt()
                    val alarmTag = "闹钟：${alarmResult.eventName.ifBlank { "智能闹钟" }}"

                    timerState.onTagInputChange(alarmTag)
                    timerState.onInputMinutesChange((secondsUntilAlarm / 60).toString())
                    timerState.onInputSecondsChange((secondsUntilAlarm % 60).toString())
                    viewModel.startAlarmService(
                        durationSeconds = secondsUntilAlarm,
                        label = alarmResult.eventName,
                        targetTimeMillis = targetTimeMillis
                    )

                    Toast.makeText(
                        context,
                        "已设置闹钟: ${alarmResult.eventName}, ${formatAlarmTargetTime(targetTimeMillis)}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }
}

private fun resolveAlarmTargetMillis(
    alarmResult: VoiceAlarmResult,
    nowMillis: Long = System.currentTimeMillis()
): Long {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        add(Calendar.DATE, alarmResult.dateOffsetDays)
        set(Calendar.HOUR_OF_DAY, alarmResult.hour)
        set(Calendar.MINUTE, alarmResult.minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    if (alarmResult.dateOffsetDays == 0 && calendar.timeInMillis <= nowMillis) {
        calendar.add(Calendar.DATE, 1)
    }

    return calendar.timeInMillis
}

private fun formatAlarmTargetTime(targetTimeMillis: Long): String {
    return SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(targetTimeMillis))
}

/**
 * 简易倒计时组件
 * 支持设置分钟和秒、开始/暂停，倒计时结束播放铃声
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountdownTimerCard(
    state: TimerState,
    onShowVoiceDialog: () -> Unit,
    onShowAlarmDialog: () -> Unit,
    activeAlarm: AlarmRecordEntity?,
    onCancelAlarm: (AlarmRecordEntity) -> Unit,
    tags: List<String> = listOf("平板支撑", "煮鸡蛋", "跳绳", "烧水", "冥想", "拉伸")
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "倒计时",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )

            // 倒计时显示 MM:SS（左）+ 开始/暂停按钮（右），同一行作为主路径
            val minutes = state.remainingSeconds / 60
            val seconds = state.remainingSeconds % 60
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "%02d:%02d".format(minutes, seconds),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (state.remainingSeconds == 0) AppColors.HeartRateCritical
                        else MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = { state.onIsRunningChange(!state.isRunning) },
                    enabled = state.remainingSeconds > 0
                ) {
                    Text(if (state.isRunning) "暂停" else "开始")
                }
            }

            // 分/秒双输入框：保留默认 40 秒意图（方便平板支撑分组等非整分钟场景）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = state.inputMinutes,
                    onValueChange = { text ->
                        if (text.isEmpty() || (text.all { it.isDigit() } && text.toIntOrNull()?.let { it in 0..999 } == true)) {
                            state.onInputMinutesChange(text)
                            if (!state.isRunning) {
                                val mins = text.toIntOrNull() ?: 0
                                val secs = state.inputSeconds.toIntOrNull() ?: 0
                                val total = mins * 60 + secs
                                if (total > 0) { state.onTotalSecondsChange(total); state.onRemainingSecondsChange(total) }
                            }
                        }
                    },
                    modifier = Modifier.width(64.dp),
                    singleLine = true,
                    enabled = !state.isRunning,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, textAlign = TextAlign.Center)
                )
                Text("分", fontSize = 14.sp)
                OutlinedTextField(
                    value = state.inputSeconds,
                    onValueChange = { text ->
                        if (text.isEmpty() || (text.all { it.isDigit() } && text.toIntOrNull()?.let { it in 0..59 } == true)) {
                            state.onInputSecondsChange(text)
                            if (!state.isRunning) {
                                val mins = state.inputMinutes.toIntOrNull() ?: 0
                                val secs = text.toIntOrNull() ?: 0
                                val total = mins * 60 + secs
                                if (total > 0) { state.onTotalSecondsChange(total); state.onRemainingSecondsChange(total) }
                            }
                        }
                    },
                    modifier = Modifier.width(64.dp),
                    singleLine = true,
                    enabled = !state.isRunning,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, textAlign = TextAlign.Center)
                )
                Text("秒", fontSize = 14.sp)
            }
        }
    }

    // 标签下拉框（Card 外部），选项来自最近使用记录
    var tagExpanded by remember { mutableStateOf(false) }
    val tagOptions = tags
    ExposedDropdownMenuBox(
        expanded = tagExpanded && !state.isRunning,
        onExpandedChange = { if (!state.isRunning) tagExpanded = it }
    ) {
        OutlinedTextField(
            value = state.tagInput,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = !state.isRunning)
                .fillMaxWidth(),
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, textAlign = TextAlign.Center),
            enabled = !state.isRunning,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = tagExpanded) }
        )
        ExposedDropdownMenu(
            expanded = tagExpanded && !state.isRunning,
            onDismissRequest = { tagExpanded = false }
        ) {
            tagOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, fontSize = 14.sp) },
                    onClick = {
                        state.onTagInputChange(option)
                        tagExpanded = false
                    }
                )
            }
        }
    }

    // 智能计时 / 智能闹钟：同级次级入口，并排各占一半，避免与卡片内「开始」主按钮抢视觉权重
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onShowVoiceDialog,
            modifier = Modifier.weight(1f),
            enabled = !state.isRunning
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("智能计时", fontSize = 14.sp)
        }

        OutlinedButton(
            onClick = onShowAlarmDialog,
            modifier = Modifier.weight(1f),
            enabled = !state.isRunning
        ) {
            Icon(
                imageVector = Icons.Outlined.Notifications,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("智能闹钟", fontSize = 14.sp)
        }
    }

    activeAlarm?.let { alarm ->
        ActiveAlarmCard(
            alarm = alarm,
            onCancel = { onCancelAlarm(alarm) }
        )
    }
    }
}
