package com.heartratemonitor.ui.screens

import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.background
import com.heartratemonitor.R
import com.heartratemonitor.ui.theme.AppColors
import com.heartratemonitor.ui.theme.toColorString
import com.heartratemonitor.viewmodel.HeartRateViewModel
import android.content.Intent
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 设置Activity
 */
@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {

    private val viewModel: HeartRateViewModel by viewModels()

    private lateinit var finishCallback: () -> Unit

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finishCallback = {
            finish()
        }
        setContent {
            val themeColor by viewModel.themeColor.collectAsState()
            com.heartratemonitor.ui.theme.HeartRateMonitorTheme(themeColor = themeColor) {
                SettingsScreen(finishCallback, viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(finishCallback: () -> Unit, viewModel: HeartRateViewModel) {
    val highThreshold by viewModel.highThreshold.collectAsState()
    val lowThreshold by viewModel.lowThreshold.collectAsState()
    val themeColor by viewModel.themeColor.collectAsState()
    val savedSoundUri by viewModel.timerSoundUri.collectAsState()
    val heartbeatSoundEnabled by viewModel.heartbeatSoundEnabled.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()
    val focusManager = LocalFocusManager.current
    val context = androidx.compose.ui.platform.LocalContext.current

    var highThresholdInput by remember { mutableStateOf(highThreshold.toString()) }
    var lowThresholdInput by remember { mutableStateOf(lowThreshold.toString()) }

    LaunchedEffect(highThreshold) {
        highThresholdInput = highThreshold.toString()
    }
    LaunchedEffect(lowThreshold) {
        lowThresholdInput = lowThreshold.toString()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { finishCallback.invoke() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                        highThresholdInput.toIntOrNull()?.let { viewModel.saveHighThreshold(it) }
                        lowThresholdInput.toIntOrNull()?.let { viewModel.saveLowThreshold(it) }
                    })
                },
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 心率阈值设置卡片
            SettingsCard(title = "心率阈值设置") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // 高心率阈值
                    ThresholdSettingItem(
                        label = stringResource(R.string.threshold_high),
                        value = highThresholdInput,
                        onValueChange = {
                            if (it.all { char -> char.isDigit() } || it.isEmpty()) {
                                highThresholdInput = it
                            }
                        },
                        onSave = {
                            highThresholdInput.toIntOrNull()?.let { value ->
                                viewModel.saveHighThreshold(value)
                            }
                        }
                    )

                    Divider()

                    // 低心率阈值
                    ThresholdSettingItem(
                        label = stringResource(R.string.threshold_low),
                        value = lowThresholdInput,
                        onValueChange = {
                            if (it.all { char -> char.isDigit() } || it.isEmpty()) {
                                lowThresholdInput = it
                            }
                        },
                        onSave = {
                            lowThresholdInput.toIntOrNull()?.let { value ->
                                viewModel.saveLowThreshold(value)
                            }
                        }
                    )
                }
            }

            // 主题颜色设置卡片
            SettingsCard(title = stringResource(R.string.theme_color)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "选择应用主题颜色",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // 预设颜色
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        listOf(
                            AppColors.Primary,
                            Color(0xFFE91E63),
                            Color(0xFF9C27B0),
                            Color(0xFF2196F3),
                            Color(0xFF4CAF50),
                            Color(0xFFFF9800)
                        ).forEach { color ->
                            ColorOption(
                                color = color,
                                selected = themeColor == color.toColorString(),
                                onClick = {
                                    viewModel.saveThemeColor(color.toColorString())
                                }
                            )
                        }
                    }
                }
            }

            // 心率声音开关
            SettingsCard(title = "心率声音") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "连接心率带时，发出类似医院监护仪的\"滴\"声",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("启用心率声音", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = heartbeatSoundEnabled,
                            onCheckedChange = { viewModel.saveHeartbeatSoundEnabled(it) }
                        )
                    }
                }
            }

            // 倒计时铃声设置卡片
            TimerSoundSettingCard(
                savedSoundUri = savedSoundUri,
                context = context,
                onSaveSound = { uri -> viewModel.saveTimerSoundUri(uri.toString()) }
            )

            // 数据同步卡片
            DataSyncCard(
                syncState = syncState,
                restoreState = viewModel.restoreState.collectAsState().value,
                lastSyncTime = lastSyncTime,
                hasLocalBackup = viewModel.hasLocalBackup,
                localBackupTime = viewModel.localBackupTime,
                onSync = { viewModel.syncToCloud() },
                onRestore = { viewModel.restoreFromBackup() }
            )

            // 提示信息
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "提示",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• 高心率阈值必须大于低心率阈值\n• 阈值设置后，超出范围时App会震动和语音提醒\n• 主题颜色会实时应用到整个App",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun ThresholdSettingItem(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit
) {
    var wasFocused by remember { mutableStateOf(false) }
    
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { state ->
                    if (state.isFocused) {
                        wasFocused = true
                    } else if (wasFocused) {
                        wasFocused = false
                        onSave()
                    }
                },
            label = { Text("BPM") },
            singleLine = true,
            suffix = { Text("BPM") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { onSave() }
            )
        )
    }
}

@Composable
fun ColorOption(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(color = color)
            .then(
                if (selected) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    )
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * 倒计时铃声设置卡片（自建弹窗，点击即试听）
 */
@Composable
fun TimerSoundSettingCard(
    savedSoundUri: String?,
    context: android.content.Context,
    onSaveSound: (Uri) -> Unit
) {
    val soundName = remember(savedSoundUri) {
        if (savedSoundUri == null) "系统默认" else {
            try {
                RingtoneManager.getRingtone(context, Uri.parse(savedSoundUri))?.getTitle(context) ?: "自定义铃声"
            } catch (_: Exception) {
                "自定义铃声"
            }
        }
    }

    val previewPlayer = remember { MediaPlayer() }
    var isPreviewPlaying by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var playingUri by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try { previewPlayer.release() } catch (_: Exception) {}
        }
    }

    fun playPreview(uri: Uri) {
        try {
            previewPlayer.stop()
            previewPlayer.reset()
            previewPlayer.setDataSource(context, uri)
            previewPlayer.setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            previewPlayer.prepare()
            previewPlayer.start()
            isPreviewPlaying = true
            playingUri = uri.toString()
            previewPlayer.setOnCompletionListener {
                isPreviewPlaying = false
                playingUri = null
            }
        } catch (_: Exception) {}
    }

    fun stopPreview() {
        try { previewPlayer.stop() } catch (_: Exception) {}
        isPreviewPlaying = false
        playingUri = null
    }

    // 试听当前铃声按钮
    SettingsCard(title = "倒计时铃声") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "选择倒计时结束时的提示铃声",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = {
                    if (isPreviewPlaying) stopPreview() else {
                        val uri = if (savedSoundUri != null) Uri.parse(savedSoundUri)
                            else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                        playPreview(uri)
                    }
                }) {
                    Icon(
                        imageVector = if (isPreviewPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                        contentDescription = if (isPreviewPlaying) "停止" else "试听",
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    text = "当前: $soundName",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { showPicker = true }) {
                    Text("选择铃声")
                }
            }
        }
    }

    // 自建铃声选择弹窗
    if (showPicker) {
        RingtonePickerDialog(
            currentUri = savedSoundUri,
            playingUri = playingUri,
            context = context,
            onPlay = { playPreview(it) },
            onStop = { stopPreview() },
            onDismiss = { stopPreview(); showPicker = false },
            onConfirm = { uri ->
                onSaveSound(uri)
                showPicker = false
            }
        )
    }
}

/**
 * 自建铃声选择弹窗 — 点击列表项即时播放试听
 */
@Composable
private fun RingtonePickerDialog(
    currentUri: String?,
    playingUri: String?,
    context: android.content.Context,
    onPlay: (Uri) -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (Uri) -> Unit
) {
    // 获取系统通知铃声列表
    val ringtoneList = remember {
        val cursor = RingtoneManager(context).also {
            it.setType(RingtoneManager.TYPE_NOTIFICATION)
        }.cursor
        val list = mutableListOf<Pair<String, String>>() // title to uriString
        if (cursor != null) {
            while (cursor.moveToNext()) {
                val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX) ?: ""
                val uri = cursor.getString(RingtoneManager.URI_COLUMN_INDEX) + "/" + cursor.getString(RingtoneManager.ID_COLUMN_INDEX)
                list.add(title to uri)
            }
            cursor.close()
        }
        list
    }

    var selectedUri by remember(currentUri) { mutableStateOf(currentUri) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择倒计时铃声") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                items(ringtoneList.size) { index ->
                    val (title, uri) = ringtoneList[index]
                    val isSelected = uri == selectedUri
                    val isPlaying = uri == playingUri

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedUri = uri
                                onPlay(Uri.parse(uri))
                            }
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent
                            )
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 试听/停止图标
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "停止" else "试听",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "已选中",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    if (index < ringtoneList.size - 1) {
                        androidx.compose.material3.HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onStop()
                selectedUri?.let { onConfirm(Uri.parse(it)) }
            }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 数据同步卡片
 */
@Composable
fun DataSyncCard(
    syncState: com.heartratemonitor.viewmodel.HeartRateViewModel.SyncState,
    restoreState: com.heartratemonitor.viewmodel.HeartRateViewModel.RestoreState,
    lastSyncTime: Long,
    hasLocalBackup: Boolean,
    localBackupTime: String?,
    onSync: () -> Unit,
    onRestore: () -> Unit
) {
    val isSyncing = syncState is com.heartratemonitor.viewmodel.HeartRateViewModel.SyncState.SYNCING
    val isRestoring = restoreState is com.heartratemonitor.viewmodel.HeartRateViewModel.RestoreState.RESTORING

    SettingsCard(title = "数据备份与恢复") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "同步时同时备份到云端和本地，恢复时优先使用本地备份",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 上次同步时间
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (lastSyncTime > 0) {
                        "上次同步: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(lastSyncTime))}"
                    } else {
                        "尚未同步"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 本地备份信息
            Text(
                text = if (hasLocalBackup && localBackupTime != null) {
                    "本地备份: $localBackupTime"
                } else {
                    "本地备份: 无"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (hasLocalBackup) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 同步按钮
            Button(
                onClick = onSync,
                enabled = !isSyncing && !isRestoring,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("备份中...")
                } else {
                    Text("同步到云端并本地备份")
                }
            }

            // 恢复按钮
            OutlinedButton(
                onClick = onRestore,
                enabled = !isSyncing && !isRestoring,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isRestoring) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("恢复中...")
                } else {
                    Text("恢复数据")
                }
            }

            // 同步结果反馈
            when (syncState) {
                is com.heartratemonitor.viewmodel.HeartRateViewModel.SyncState.SUCCESS -> {
                    val state = syncState
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFE8F5E9)
                    ) {
                        Text(
                            text = "同步成功！心率 ${state.syncedHeartRates} 条，计时 ${state.syncedTimerSessions} 条",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
                is com.heartratemonitor.viewmodel.HeartRateViewModel.SyncState.ERROR -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFFEBEE)
                    ) {
                        Text(
                            text = "同步失败: ${syncState.message}",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFC62828)
                        )
                    }
                }
                else -> {}
            }

            // 恢复结果反馈
            when (restoreState) {
                is com.heartratemonitor.viewmodel.HeartRateViewModel.RestoreState.SUCCESS -> {
                    val state = restoreState
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFE8F5E9)
                    ) {
                        Text(
                            text = "恢复成功！心率 ${state.restoredHeartRates} 条，计时 ${state.restoredTimerSessions} 条",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
                is com.heartratemonitor.viewmodel.HeartRateViewModel.RestoreState.ERROR -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFFEBEE)
                    ) {
                        Text(
                            text = "恢复失败: ${restoreState.message}",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFC62828)
                        )
                    }
                }
                else -> {}
            }
        }
    }
}
