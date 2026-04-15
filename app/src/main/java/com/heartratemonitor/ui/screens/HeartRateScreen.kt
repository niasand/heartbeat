package com.heartratemonitor.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.focus.FocusState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import android.widget.Toast
import com.heartratemonitor.R
import com.heartratemonitor.ui.theme.AppColors
import com.heartratemonitor.viewmodel.HeartRateViewModel
import com.heartratemonitor.ble.ConnectionState
import com.heartratemonitor.ble.AutoReconnectState
import com.heartratemonitor.ui.screens.SettingsActivity
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import android.os.Build
import android.Manifest
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.heartratemonitor.ble.BleScanner
import com.heartratemonitor.service.TimerCountdownService
import android.content.Intent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.Notifications
import com.google.accompanist.permissions.rememberPermissionState

/**
 * 主屏幕 - 心率监测
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeartRateScreen(viewModel: HeartRateViewModel = viewModel()) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    var connectAttemptId by remember { mutableIntStateOf(0) }

    // 自动连接状态从 ViewModel 读取，避免 Activity 切换时重置导致重复连接
    val hasAutoConnectAttempted by viewModel.hasAutoConnectAttempted.collectAsState()
    val hasAutoConnectedDevice by viewModel.hasAutoConnectedDevice.collectAsState()

    // 倒计时本地输入状态
    var timerInputMinutes by remember { mutableStateOf("0") }
    var timerInputSeconds by remember { mutableStateOf("40") }
    var timerTagInput by remember { mutableStateOf("平板支撑") }

    // 倒计时运行状态从 TimerCountdownService 派生
    val timerServiceState by viewModel.timerServiceState.collectAsState()

    val inputComputedTotal = (timerInputMinutes.toIntOrNull() ?: 0) * 60 + (timerInputSeconds.toIntOrNull() ?: 0)

    val timerTotalSeconds: Int = when (val s = timerServiceState) {
        is TimerCountdownService.TimerServiceState.RUNNING -> s.totalSeconds
        is TimerCountdownService.TimerServiceState.PAUSED -> s.totalSeconds
        is TimerCountdownService.TimerServiceState.COMPLETED -> s.totalSeconds
        is TimerCountdownService.TimerServiceState.IDLE -> if (inputComputedTotal > 0) inputComputedTotal else 40
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
    // 用 wasTimerActive 追踪是否曾处于计时状态（RUNNING/PAUSED），
    // 而非仅追踪 COMPLETED，以便处理倒计时在后台完成时用户通过通知返回的场景
    var wasTimerActive by remember { mutableStateOf(false) }
    LaunchedEffect(timerServiceState) {
        when (timerServiceState) {
            is TimerCountdownService.TimerServiceState.RUNNING,
            is TimerCountdownService.TimerServiceState.PAUSED -> {
                wasTimerActive = true
            }
            is TimerCountdownService.TimerServiceState.COMPLETED -> {
                Toast.makeText(context, "倒计时结束！", Toast.LENGTH_LONG).show()
            }
            is TimerCountdownService.TimerServiceState.IDLE -> {
                if (wasTimerActive) {
                    wasTimerActive = false
                    timerInputMinutes = "0"
                    timerInputSeconds = "40"
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
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
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
                                    }
                                }
                                wantRunning -> {
                                    // IDLE or COMPLETED — start new timer
                                    if (inputComputedTotal > 0) {
                                        viewModel.startTimerService(inputComputedTotal, timerTagInput.ifBlank { null })
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
    val connectionState by viewModel.connectionState.collectAsState()
    val highThreshold by viewModel.highThreshold.collectAsState()
    val lowThreshold by viewModel.lowThreshold.collectAsState()
    val autoReconnectState by viewModel.autoReconnectState.collectAsState()
    val lastDeviceAddress by viewModel.lastDeviceAddress.collectAsState()
    val hasAutoConnectAttempted by viewModel.hasAutoConnectAttempted.collectAsState()
    val hasAutoConnectedDevice by viewModel.hasAutoConnectedDevice.collectAsState()
    val heartRateHistory by viewModel.heartRateHistory.collectAsState()

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 心率显示卡片（紧凑版）
        Card(
            modifier = Modifier.size(180.dp),
            shape = RoundedCornerShape(24.dp),
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
            }
        }

        // 控制按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    if (permissionsState.allPermissionsGranted) {
                        showDeviceList = true
                    } else {
                        permissionsState.launchMultiplePermissionRequest()
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = connectionState !is ConnectionState.CONNECTED
                    && connectionState !is ConnectionState.CONNECTING
            ) {
                Text(stringResource(R.string.scan_device))
            }
        }

        // 倒计时
        CountdownTimerCard(
            state = timerState,
            onShowVoiceDialog = { showVoiceInputDialog = true }
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
            apiKey = siliconFlowApiKey ?: "",
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
}

/**
 * 简易倒计时组件
 * 支持设置分钟和秒、开始/暂停，倒计时结束播放铃声
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountdownTimerCard(
    state: TimerState,
    onShowVoiceDialog: () -> Unit
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

            // 倒计时显示 MM:SS
            val minutes = state.remainingSeconds / 60
            val seconds = state.remainingSeconds % 60
            Text(
                text = "%02d:%02d".format(minutes, seconds),
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = if (state.remainingSeconds == 0) AppColors.HeartRateCritical
                    else MaterialTheme.colorScheme.onSecondaryContainer
            )

            // 输入框在左，开始按钮最后
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
                    modifier = Modifier
                        .width(64.dp),
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
                    modifier = Modifier
                        .width(64.dp),
                    singleLine = true,
                    enabled = !state.isRunning,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, textAlign = TextAlign.Center)
                )
                Text("秒", fontSize = 14.sp)
                Button(
                    onClick = { state.onIsRunningChange(!state.isRunning) },
                    enabled = state.remainingSeconds > 0
                ) {
                    Text(if (state.isRunning) "暂停" else "开始")
                }
            }
        }
    }

    // 标签下拉框（Card 外部）
    var tagExpanded by remember { mutableStateOf(false) }
    val tagOptions = listOf("平板支撑", "煮鸡蛋", "跳绳", "烧水", "冥想", "拉伸")
    ExposedDropdownMenuBox(
        expanded = tagExpanded && !state.isRunning,
        onExpandedChange = { if (!state.isRunning) tagExpanded = it }
    ) {
        OutlinedTextField(
            value = state.tagInput,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.menuAnchor().fillMaxWidth(),
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

    // 智能计时按钮
    Button(
        onClick = onShowVoiceDialog,
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.isRunning
    ) {
        Text("智能计时", fontSize = 14.sp)
    }
    }
}
