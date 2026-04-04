package com.heartratemonitor.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import com.heartratemonitor.R
import com.heartratemonitor.ble.BleConnectionManager
import com.heartratemonitor.ble.BleScanner
import com.heartratemonitor.ui.MainActivity
import com.heartratemonitor.ble.HeartRateData
import com.heartratemonitor.data.pref.PreferencesManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

import android.media.RingtoneManager
import android.media.Ringtone

/**
 * 前台服务
 * 负责在后台持续监听心率带数据
 */
@AndroidEntryPoint
class BleHeartRateService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val CHANNEL_ID = "heart_rate_monitor_channel"
        const val NOTIFICATION_ID = 1
        private const val TAG = "BleHeartRateService"
        const val ACTION_STOP = "ACTION_STOP"
    }

    @Inject
    lateinit var bleScanner: BleScanner

    @Inject
    lateinit var bleConnectionManager: BleConnectionManager

    @Inject
    lateinit var preferencesManager: PreferencesManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val binder = LocalBinder()

    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false
    private var vibrator: Vibrator? = null

    // 阈值设置 (Removed local cache, will use PreferencesManager directly)
    // private var highThreshold = 180
    // private var lowThreshold = 60

    // 最后一次报警的时间，避免重复报警
    private var lastAlertTime = 0L
    private val alertCooldown = 30000L // 30秒冷却时间

    private val _serviceState = MutableStateFlow<ServiceState>(ServiceState.IDLE)
    val serviceState: StateFlow<ServiceState> = _serviceState

    sealed class ServiceState {
        data object IDLE : ServiceState()
        data object SCANNING : ServiceState()
        data object CONNECTING : ServiceState()
        data object MONITORING : ServiceState()
        data class ERROR(val message: String) : ServiceState()
    }

    inner class LocalBinder : Binder() {
        fun getService(): BleHeartRateService = this@BleHeartRateService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        // 初始化TTS
        textToSpeech = TextToSpeech(this, this)

        // 初始化震动器
        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator

        // 创建通知通道
        createNotificationChannel()

        // 监听BLE状态
        observeBleStates()
        
        // 监听阈值设置
        observeThresholds()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopMonitoring()
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    /**
     * 开始监测心率
     */
    fun startMonitoring() {
        serviceScope.launch {
            _serviceState.value = ServiceState.SCANNING
            bleScanner.startScan()

            // 开始扫描后，让用户选择设备
            // 这里简化处理，扫描到第一个Coros设备就自动连接
            // 实际应用中应该让用户在UI上选择
        }
    }

    /**
     * 连接到指定设备
     */
    fun connectToDevice(deviceAddress: String) {
        bleScanner.stopScan()
        serviceScope.launch {
            _serviceState.value = ServiceState.CONNECTING
            val success = bleConnectionManager.connectToDevice(deviceAddress)
            if (!success) {
                _serviceState.value = ServiceState.ERROR("连接失败")
            }
            serviceScope.launch {
                kotlinx.coroutines.delay(15000)
                if (_serviceState.value is ServiceState.CONNECTING) {
                    bleConnectionManager.disconnect()
                    _serviceState.value = ServiceState.ERROR("连接超时")
                }
            }
        }
    }

    /**
     * 停止监测
     */
    fun stopMonitoring() {
        bleConnectionManager.disconnect()
        bleScanner.stopScan()
        _serviceState.value = ServiceState.IDLE
    }

    /**
     * 设置心率阈值
     */
    fun setThresholds(high: Int, low: Int) {
        // No-op, using PreferencesManager
    }

    private fun observeThresholds() {
        // No longer needed as we use cached values from PreferencesManager directly
    }

    private fun observeBleStates() {
        serviceScope.launch {
            bleConnectionManager.connectionState.collect { state ->
                when (state) {
                    is com.heartratemonitor.ble.ConnectionState.CONNECTED -> {
                        _serviceState.value = ServiceState.MONITORING
                        // 连接成功后不立即更新通知，等待收到真实心率数据
                        // updateNotification(bleConnectionManager.heartRateFlow.value?.value ?: 0)
                    }
                    is com.heartratemonitor.ble.ConnectionState.DISCONNECTED -> {
                        _serviceState.value = ServiceState.IDLE
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    }
                    is com.heartratemonitor.ble.ConnectionState.CONNECTING -> {
                        _serviceState.value = ServiceState.CONNECTING
                    }
                    is com.heartratemonitor.ble.ConnectionState.ERROR -> {
                        _serviceState.value = ServiceState.ERROR(state.message)
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    }
                    else -> {
                        // Handle other states if needed
                    }
                }
            }
        }

        serviceScope.launch {
            bleConnectionManager.heartRateFlow.collect { heartRateData ->
                heartRateData?.let { data ->
                    updateNotification(data.value)
                    checkThresholds(data.value)
                }
            }
        }
    }

    /**
     * 检查心率是否超出阈值
     */
    private fun checkThresholds(heartRate: Int) {
        val highThreshold = preferencesManager.cachedHighThreshold
        val lowThreshold = preferencesManager.cachedLowThreshold

        // 如果当前心率在阈值范围内（正常范围），则不报警
        if (heartRate in lowThreshold..highThreshold) {
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastAlertTime < alertCooldown) {
            return // 冷却时间内不重复报警
        }

        when {
            heartRate > highThreshold -> {
                triggerAlert("心率过高警告，当前心率$heartRate")
                lastAlertTime = now
            }
            heartRate < lowThreshold -> {
                triggerAlert("心率过低警告，当前心率$heartRate")
                lastAlertTime = now
            }
        }
    }

    /**
     * 触发警报（震动 + 语音 + 铃声）
     */
    private fun triggerAlert(message: String) {
        Log.d(TAG, "Alert: $message")

        // 震动
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(
                    longArrayOf(0, 500, 200, 500),
                    -1
                )
                it.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(longArrayOf(0, 500, 200, 500), -1)
            }
        }

        // 播放铃声
        try {
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val r = RingtoneManager.getRingtone(applicationContext, notification)
            r.play()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play ringtone", e)
        }

        // 语音提示
        if (isTtsReady) {
            textToSpeech?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "alert")
        }
    }

    /**
     * 更新通知内容
     */
    private fun updateNotification(heartRate: Int) {
        val notification = createNotification(heartRate)
        startForeground(NOTIFICATION_ID, notification)
    }

    /**
     * 创建通知
     */
    @SuppressLint("NotificationPermission")
    private fun createNotification(heartRate: Int): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val stopIntent = Intent(this, BleHeartRateService::class.java).apply {
            action = ACTION_STOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_title))
            .setContentText(getString(R.string.service_content, heartRate))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setNumber(heartRate) // Set the heart rate as the notification badge number
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "停止",
                stopPendingIntent
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "心率监测",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "持续监测心率数据"
                setShowBadge(true) // Enable badge for this channel
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    // TextToSpeech OnInitListener
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.CHINA
            isTtsReady = true
            Log.d(TAG, "TTS initialized")
        } else {
            Log.e(TAG, "TTS initialization failed")
            isTtsReady = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        stopMonitoring()
        textToSpeech?.shutdown()
        serviceScope.cancel()
    }
}
