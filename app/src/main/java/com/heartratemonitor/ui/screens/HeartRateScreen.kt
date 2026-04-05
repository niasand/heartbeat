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
import androidx.lifecycle.viewmodel.compose.viewModel
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
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import dagger.hilt.android.EntryPointAccessors
import com.heartratemonitor.di.PreferencesEntryPoint
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Notifications

/**
 * 主屏幕 - 心率监测
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeartRateScreen(viewModel: HeartRateViewModel = viewModel()) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    var connectAttemptId by remember { mutableIntStateOf(0) }
    
    // 自动连接状态提升到父组件，避免Tab切换时重复触发
    var hasAutoConnectAttempted by remember { mutableStateOf(false) }
    var hasAutoConnectedDevice by remember { mutableStateOf(false) }

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
                    hasAutoConnectAttempted,
                    { hasAutoConnectAttempted = it },
                    hasAutoConnectedDevice,
                    { hasAutoConnectedDevice = it }
                )
                1 -> HeartRateHistoryScreen(viewModel)
                2 -> TimerHistoryScreen(viewModel)
            }
        }
    }
}

/**
 * 实时心率屏幕
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun RealTimeHeartRateScreen(
    viewModel: HeartRateViewModel = viewModel(), 
    connectAttemptId: Int, 
    onConnectAttemptIdChange: (Int) -> Unit = {},
    hasAutoConnectAttempted: Boolean,
    onHasAutoConnectAttemptedChange: (Boolean) -> Unit,
    hasAutoConnectedDevice: Boolean,
    onHasAutoConnectedDeviceChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val currentHeartRate by viewModel.currentHeartRate.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val highThreshold by viewModel.highThreshold.collectAsState()
    val lowThreshold by viewModel.lowThreshold.collectAsState()
    val autoReconnectState by viewModel.autoReconnectState.collectAsState()
    val lastDeviceAddress by viewModel.lastDeviceAddress.collectAsState()

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

    // Track previous state to avoid showing toast when switching tabs
    var previousState by remember { mutableStateOf(connectionState) }

    LaunchedEffect(connectionState) {
        if (connectionState is ConnectionState.CONNECTED && previousState !is ConnectionState.CONNECTED) {
            Toast.makeText(context, "已连接高驰心率带", Toast.LENGTH_SHORT).show()
        }
        previousState = connectionState
    }
    
    // 自动重连提示
    LaunchedEffect(autoReconnectState) {
        when (autoReconnectState) {
            is AutoReconnectState.RECONNECTING -> {
                val state = autoReconnectState as AutoReconnectState.RECONNECTING
                Toast.makeText(context, "正在尝试自动重连 (${state.attempt}/${state.maxAttempts})...", Toast.LENGTH_SHORT).show()
            }
            is AutoReconnectState.FAILED -> {
                Toast.makeText(context, "自动重连失败，请手动连接", Toast.LENGTH_LONG).show()
            }
            else -> {}
        }
    }
    
    // 自动扫描并连接心率带（只执行一次）
    // 优先检查是否已连接，如果已连接则直接显示数据
    LaunchedEffect(permissionsState.allPermissionsGranted) {
        if (!permissionsState.allPermissionsGranted) return@LaunchedEffect
        if (hasAutoConnectAttempted) return@LaunchedEffect

        onHasAutoConnectAttemptedChange(true)

        // 首先检查是否已经连接了心率带
        if (connectionState is ConnectionState.CONNECTED) {
            // 已连接，直接显示数据，不需要任何操作
            Toast.makeText(context, "已连接高驰心率带", Toast.LENGTH_SHORT).show()
            onHasAutoConnectedDeviceChange(true)
            return@LaunchedEffect
        }

        // 未连接，等待一小段时间确保 lastDeviceAddress 从 DataStore 加载完成
        kotlinx.coroutines.delay(500)

        // 如果有保存的设备地址，直接尝试连接
        val savedAddress = lastDeviceAddress
        if (!savedAddress.isNullOrEmpty()) {
            Toast.makeText(context, "正在自动连接心率带...", Toast.LENGTH_SHORT).show()
            viewModel.connectToDevice(savedAddress)
        } else {
            // 没有保存的设备地址，开始扫描
            viewModel.startScan()
        }
    }
    
    // 自动连接扫描到的第一个设备
    val scannedDevices by viewModel.scannedDevices.collectAsState()
    val scanState by viewModel.scanState.collectAsState()
    val savedDeviceAddress = lastDeviceAddress // 缓存避免重复获取
    
    // 显示扫描状态
    LaunchedEffect(scanState) {
        when (scanState) {
            is BleScanner.ScanState.SCANNING -> {
                if (!hasAutoConnectedDevice && savedDeviceAddress.isNullOrEmpty()) {
                    Toast.makeText(context, "正在扫描心率带...", Toast.LENGTH_SHORT).show()
                }
            }
            is BleScanner.ScanState.ERROR -> {
                val error = scanState as BleScanner.ScanState.ERROR
                Toast.makeText(context, "扫描失败: ${error.message}", Toast.LENGTH_LONG).show()
            }
            else -> {}
        }
    }
    
    LaunchedEffect(scannedDevices.size, connectionState) {
        if (hasAutoConnectedDevice) return@LaunchedEffect
        if (connectionState is ConnectionState.CONNECTED || connectionState is ConnectionState.CONNECTING) {
            onHasAutoConnectedDeviceChange(true)
            return@LaunchedEffect
        }
        
        // 如果正在扫描且找到了设备，自动连接第一个
        if (scannedDevices.isNotEmpty() && savedDeviceAddress.isNullOrEmpty()) {
            onHasAutoConnectedDeviceChange(true)
            val firstDevice = scannedDevices.first()
            Toast.makeText(context, "发现设备 ${firstDevice.name}，正在连接...", Toast.LENGTH_SHORT).show()
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
        CountdownTimerCard(viewModel)
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
}

/**
 * 简易倒计时组件
 * 支持设置分钟和秒、开始/暂停，倒计时结束播放铃声
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountdownTimerCard(viewModel: HeartRateViewModel) {
    var totalSeconds by remember { mutableIntStateOf(40) }
    var remainingSeconds by remember { mutableIntStateOf(40) }
    var isRunning by remember { mutableStateOf(false) }
    var inputMinutes by remember { mutableStateOf("0") }
    var inputSeconds by remember { mutableStateOf("40") }
    var tagInput by remember { mutableStateOf("平板支撑") }

    val context = LocalContext.current

    // 读取铃声偏好
    val preferencesManager = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            PreferencesEntryPoint::class.java
        ).preferencesManager()
    }
    var timerSoundUri by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        preferencesManager.timerSoundUriFlow.collect { uri -> timerSoundUri = uri }
    }

    // MediaPlayer 持久持有，防止 GC 回收
    val mediaPlayer = remember { android.media.MediaPlayer() }

    // 倒计时逻辑
    LaunchedEffect(isRunning, remainingSeconds) {
        if (isRunning && remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds--
        } else if (remainingSeconds == 0) {
            isRunning = false
        }
    }

    // 倒计时结束：播放铃声 + Toast
    var hasPlayed by remember { mutableStateOf(false) }
    LaunchedEffect(remainingSeconds, isRunning) {
        if (remainingSeconds == 0 && !isRunning && !hasPlayed) {
            hasPlayed = true
            Toast.makeText(context, "倒计时结束！", Toast.LENGTH_LONG).show()
            viewModel.saveTimerSession(totalSeconds, tagInput.ifBlank { null })
            try {
                mediaPlayer.reset()
                val uri = if (timerSoundUri != null) Uri.parse(timerSoundUri)
                    else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                mediaPlayer.setDataSource(context, uri)
                mediaPlayer.setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                mediaPlayer.prepare()
                mediaPlayer.start()
            } catch (_: Exception) {}
        }
        if (remainingSeconds > 0) {
            hasPlayed = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try { mediaPlayer.release() } catch (_: Exception) {}
        }
    }

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
            val minutes = remainingSeconds / 60
            val seconds = remainingSeconds % 60
            Text(
                text = "%02d:%02d".format(minutes, seconds),
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = if (remainingSeconds == 0) AppColors.HeartRateCritical
                    else MaterialTheme.colorScheme.onSecondaryContainer
            )

            // 输入框在左，标签在右，开始按钮最后
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = inputMinutes,
                    onValueChange = { text ->
                        if (text.isEmpty() || (text.all { it.isDigit() } && text.toIntOrNull()?.let { it in 0..999 } == true)) {
                            inputMinutes = text
                            if (!isRunning) {
                                val mins = text.toIntOrNull() ?: 0
                                val secs = inputSeconds.toIntOrNull() ?: 0
                                val total = mins * 60 + secs
                                if (total > 0) { totalSeconds = total; remainingSeconds = total }
                            }
                        }
                    },
                    modifier = Modifier
                        .width(64.dp)
                        .onFocusEvent { state ->
                            if (state.isFocused) inputMinutes = ""
                        },
                    singleLine = true,
                    enabled = !isRunning,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, textAlign = TextAlign.Center)
                )
                Text("分", fontSize = 14.sp)
                OutlinedTextField(
                    value = inputSeconds,
                    onValueChange = { text ->
                        if (text.isEmpty() || (text.all { it.isDigit() } && text.toIntOrNull()?.let { it in 0..59 } == true)) {
                            inputSeconds = text
                            if (!isRunning) {
                                val mins = inputMinutes.toIntOrNull() ?: 0
                                val secs = text.toIntOrNull() ?: 0
                                val total = mins * 60 + secs
                                if (total > 0) { totalSeconds = total; remainingSeconds = total }
                            }
                        }
                    },
                    modifier = Modifier
                        .width(64.dp)
                        .onFocusEvent { state ->
                            if (state.isFocused) inputSeconds = ""
                        },
                    singleLine = true,
                    enabled = !isRunning,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, textAlign = TextAlign.Center)
                )
                Text("秒", fontSize = 14.sp)
                Button(
                    onClick = { isRunning = !isRunning },
                    enabled = remainingSeconds > 0
                ) {
                    Text(if (isRunning) "暂停" else "开始")
                }
            }
        }
    }

    // 标签下拉框（Card 外部）
    var tagExpanded by remember { mutableStateOf(false) }
    val tagOptions = listOf("平板支撑", "煮鸡蛋", "跳绳", "烧水", "冥想", "拉伸")
    ExposedDropdownMenuBox(
        expanded = tagExpanded && !isRunning,
        onExpandedChange = { if (!isRunning) tagExpanded = it }
    ) {
        OutlinedTextField(
            value = tagInput,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, textAlign = TextAlign.Center),
            enabled = !isRunning,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = tagExpanded) }
        )
        ExposedDropdownMenu(
            expanded = tagExpanded && !isRunning,
            onDismissRequest = { tagExpanded = false }
        ) {
            tagOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, fontSize = 14.sp) },
                    onClick = {
                        tagInput = option
                        tagExpanded = false
                    }
                )
            }
        }
    }
    }
}
