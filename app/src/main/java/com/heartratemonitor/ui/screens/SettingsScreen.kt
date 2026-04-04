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
import com.heartratemonitor.R
import com.heartratemonitor.ui.theme.AppColors
import com.heartratemonitor.ui.theme.toColorString
import com.heartratemonitor.viewmodel.HeartRateViewModel
import android.content.Intent
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close

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

            // 倒计时铃声设置卡片
            TimerSoundSettingCard(
                savedSoundUri = savedSoundUri,
                context = context,
                onSaveSound = { uri -> viewModel.saveTimerSoundUri(uri.toString()) }
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
 * 倒计时铃声设置卡片
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

    // 试听 MediaPlayer
    val previewPlayer = remember { MediaPlayer() }
    var isPreviewPlaying by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            try { previewPlayer.release() } catch (_: Exception) {}
        }
    }

    // 铃声选择器
    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) {
                onSaveSound(uri)
            }
        }
    }

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
                // 试听按钮
                IconButton(onClick = {
                    try {
                        if (isPreviewPlaying) {
                            previewPlayer.stop()
                            isPreviewPlaying = false
                        } else {
                            previewPlayer.reset()
                            val uri = if (savedSoundUri != null) Uri.parse(savedSoundUri)
                                else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
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
                            previewPlayer.setOnCompletionListener {
                                isPreviewPlaying = false
                            }
                        }
                    } catch (_: Exception) {}
                }) {
                    Icon(
                        imageVector = if (isPreviewPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                        contentDescription = if (isPreviewPlaying) "停止试听" else "试听",
                        modifier = Modifier.size(24.dp)
                    )
                }
                // 当前铃声名称
                Text(
                    text = "当前: $soundName",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                // 选择铃声按钮
                Button(onClick = {
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "选择倒计时铃声")
                        savedSoundUri?.let { putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it)) }
                    }
                    ringtonePickerLauncher.launch(intent)
                }) {
                    Text("选择铃声")
                }
            }
        }
    }
}
