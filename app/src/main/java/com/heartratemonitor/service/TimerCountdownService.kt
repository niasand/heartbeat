package com.heartratemonitor.service

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.util.Log
import androidx.core.app.NotificationCompat
import com.heartratemonitor.R
import com.heartratemonitor.data.pref.PreferencesManager
import com.heartratemonitor.data.repository.TimerSessionRepository
import com.heartratemonitor.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Foreground service for countdown timer.
 * Uses endTimeMs (wall clock) for drift-free countdown.
 * AlarmManager.setExactAndAllowWhileIdle() ensures timer fires in Doze mode.
 * WakeLock ensures CPU stays awake during sound playback on timer completion.
 */
@AndroidEntryPoint
class TimerCountdownService : Service() {

    companion object {
        private const val TAG = "TimerCountdown"
        const val CHANNEL_ID = "timer_countdown_channel"
        const val NOTIFICATION_ID = 2
        const val ACTION_START = "com.heartratemonitor.timer.START"
        const val ACTION_PAUSE = "com.heartratemonitor.timer.PAUSE"
        const val ACTION_RESUME = "com.heartratemonitor.timer.RESUME"
        const val ACTION_STOP = "com.heartratemonitor.timer.STOP"
        private const val ACTION_ALARM_FIRE = "com.heartratemonitor.timer.ALARM_FIRE"
        const val EXTRA_TOTAL_SECONDS = "extra_total_seconds"
        const val EXTRA_TAG = "extra_tag"
        private const val WAKE_LOCK_TIMEOUT_MS = 10_000L
    }

    sealed class TimerServiceState {
        data object IDLE : TimerServiceState()
        data class RUNNING(
            val totalSeconds: Int,
            val remainingSeconds: Int,
            val endTimeMs: Long,
            val tag: String?
        ) : TimerServiceState()
        data class PAUSED(
            val totalSeconds: Int,
            val remainingSeconds: Int,
            val tag: String?
        ) : TimerServiceState()
        data class COMPLETED(
            val totalSeconds: Int,
            val tag: String?
        ) : TimerServiceState()
    }

    @Inject
    lateinit var preferencesManager: PreferencesManager

    @Inject
    lateinit var timerSessionRepository: TimerSessionRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val binder = LocalBinder()

    private val _serviceState = MutableStateFlow<TimerServiceState>(TimerServiceState.IDLE)
    val serviceState: StateFlow<TimerServiceState> = _serviceState

    private var countdownJob: Job? = null
    @Volatile private var totalSeconds = 0
    @Volatile private var remainingSeconds = 0
    @Volatile private var endTimeMs = 0L
    @Volatile private var tag: String? = null
    private var alarmPendingIntent: PendingIntent? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaPlayer: MediaPlayer? = null
    @Volatile private var isCompleted = false

    inner class LocalBinder : Binder() {
        fun getService(): TimerCountdownService = this@TimerCountdownService
    }

    private val alarmReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "Alarm fired — triggering completion")
            if (_serviceState.value is TimerServiceState.RUNNING) {
                onTimerComplete()
            }
        }
    }

    // region Lifecycle

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mediaPlayer = MediaPlayer()
        registerReceiver(alarmReceiver, IntentFilter(ACTION_ALARM_FIRE), RECEIVER_NOT_EXPORTED)
        Log.d(TAG, "Service created")
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val seconds = intent.getIntExtra(EXTRA_TOTAL_SECONDS, 0)
                val timerTag = intent.getStringExtra(EXTRA_TAG)
                if (seconds > 0) startCountdown(seconds, timerTag)
            }
            ACTION_PAUSE -> pauseCountdown()
            ACTION_RESUME -> resumeCountdown()
            ACTION_STOP -> stopCountdown()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroying")
        countdownJob?.cancel()
        cancelExactAlarm()
        releaseWakeLock()
        try { mediaPlayer?.release() } catch (_: Exception) {}
        try { unregisterReceiver(alarmReceiver) } catch (_: Exception) {}
        serviceScope.cancel()
    }

    // endregion

    // region Countdown control

    private fun startCountdown(totalSecs: Int, timerTag: String?) {
        countdownJob?.cancel()
        cancelExactAlarm()
        isCompleted = false

        this.totalSeconds = totalSecs
        this.remainingSeconds = totalSecs
        this.tag = timerTag
        this.endTimeMs = System.currentTimeMillis() + totalSecs.toLong() * 1000L

        _serviceState.value = TimerServiceState.RUNNING(totalSeconds, remainingSeconds, endTimeMs, tag)

        // Start foreground immediately to meet the 5-second deadline
        startForegroundSafely(createRunningNotification())

        // Schedule exact alarm as Doze-mode safety net
        setExactAlarm(endTimeMs)

        // Tick loop: updates notification + StateFlow every second
        countdownJob = serviceScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val remaining = ((endTimeMs - now) / 1000).toInt().coerceAtLeast(0)

                if (remaining <= 0) {
                    this@TimerCountdownService.remainingSeconds = 0
                    onTimerComplete()
                    break
                }

                this@TimerCountdownService.remainingSeconds = remaining
                _serviceState.value = TimerServiceState.RUNNING(
                    totalSeconds, remaining, endTimeMs, tag
                )
                updateRunningNotification()

                delay(1000L)
            }
        }
    }

    private fun pauseCountdown() {
        countdownJob?.cancel()
        cancelExactAlarm()

        val pausedRemaining = remainingSeconds
        _serviceState.value = TimerServiceState.PAUSED(totalSeconds, pausedRemaining, tag)
        updatePausedNotification(pausedRemaining)
    }

    private fun resumeCountdown() {
        // Re-calculate endTimeMs based on current remaining
        this.endTimeMs = System.currentTimeMillis() + remainingSeconds.toLong() * 1000L

        _serviceState.value = TimerServiceState.RUNNING(
            totalSeconds, remainingSeconds, endTimeMs, tag
        )
        setExactAlarm(endTimeMs)

        countdownJob = serviceScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val remaining = ((endTimeMs - now) / 1000).toInt().coerceAtLeast(0)

                if (remaining <= 0) {
                    this@TimerCountdownService.remainingSeconds = 0
                    onTimerComplete()
                    break
                }

                this@TimerCountdownService.remainingSeconds = remaining
                _serviceState.value = TimerServiceState.RUNNING(
                    totalSeconds, remaining, endTimeMs, tag
                )
                updateRunningNotification()

                delay(1000L)
            }
        }
    }

    private fun stopCountdown() {
        countdownJob?.cancel()
        cancelExactAlarm()
        releaseWakeLock()
        try { mediaPlayer?.stop() } catch (_: Exception) {}
        _serviceState.value = TimerServiceState.IDLE
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun onTimerComplete() {
        // Guard against double invocation from concurrent tick + alarm race
        if (isCompleted) return
        isCompleted = true

        countdownJob?.cancel()
        cancelExactAlarm()

        // Acquire WakeLock so CPU stays awake for sound playback + save
        acquireWakeLock()

        _serviceState.value = TimerServiceState.COMPLETED(totalSeconds, tag)

        // Vibrate
        vibrate()

        // Play completion sound
        playCompletionSound()

        // Save session to database
        serviceScope.launch {
            timerSessionRepository.saveSession(totalSeconds, tag)
        }

        // Show dismissible completion notification
        showCompletionNotification()

        // Release foreground once sound finishes or after safety timeout (5s)
        fun releaseAfterSound() {
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_DETACH)
            _serviceState.value = TimerServiceState.IDLE
            stopSelf()
        }
        mediaPlayer?.setOnCompletionListener {
            Log.d(TAG, "Completion sound finished, releasing foreground")
            releaseAfterSound()
        }
        // Safety timeout: some sounds may not trigger OnCompletionListener
        serviceScope.launch {
            delay(TimeUnit.SECONDS.toMillis(5))
            releaseAfterSound()
        }
    }

    // endregion

    // region AlarmManager (Doze-mode safety net)

    private fun setExactAlarm(triggerTime: Long) {
        cancelExactAlarm()
        try {
            val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
            val intent = Intent(ACTION_ALARM_FIRE)
            alarmPendingIntent = PendingIntent.getBroadcast(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerTime, alarmPendingIntent!!
                )
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, alarmPendingIntent!!)
            }
            Log.d(TAG, "Exact alarm set for trigger at $triggerTime")
        } catch (e: SecurityException) {
            Log.e(TAG, "No permission to set exact alarm — timer relies on foreground service tick", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set exact alarm", e)
        }
    }

    private fun cancelExactAlarm() {
        try {
            alarmPendingIntent?.let {
                (getSystemService(ALARM_SERVICE) as AlarmManager).cancel(it)
                it.cancel()
            }
            alarmPendingIntent = null
        } catch (_: Exception) {}
    }

    // endregion

    // region WakeLock

    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld == true) return
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "$packageName:timer_wakelock"
            ).apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
            Log.d(TAG, "WakeLock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
                Log.d(TAG, "WakeLock released")
            }
            wakeLock = null
        } catch (_: Exception) {}
    }

    // endregion

    // region Sound & vibration

    private fun playCompletionSound() {
        try {
            mediaPlayer?.apply {
                reset()
                val uriString = preferencesManager.cachedTimerSoundUri
                val uri: Uri = if (!uriString.isNullOrEmpty()) Uri.parse(uriString)
                    else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                setDataSource(this@TimerCountdownService, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                prepare()
                start()
            }
            Log.d(TAG, "Completion sound playing")
        } catch (e: Exception) {
            Log.e(TAG, "Custom sound failed, falling back to default", e)
            try {
                mediaPlayer?.apply {
                    reset()
                    setDataSource(
                        this@TimerCountdownService,
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    )
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    prepare()
                    start()
                }
            } catch (e2: Exception) {
                Log.e(TAG, "Default sound also failed", e2)
            }
        }
    }

    private fun vibrate() {
        try {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as? android.os.Vibrator ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 500, 200, 500), -1)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibration failed", e)
        }
    }

    // endregion

    // region Notification

    @SuppressLint("NotificationPermission")
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "倒计时",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "倒计时提醒"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun startForegroundSafely(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground with type, falling back", e)
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createRunningNotification(): Notification {
        return buildTimerNotification(
            contentText = "剩余 ${formatTime(remainingSeconds)}",
            ongoing = true,
            actions = listOf(
                NotificationAction(android.R.drawable.ic_media_pause, "暂停", ACTION_PAUSE)
            )
        )
    }

    private fun updateRunningNotification() {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, createRunningNotification())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update running notification", e)
        }
    }

    private fun updatePausedNotification(pausedRemaining: Int) {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(
                NOTIFICATION_ID,
                buildTimerNotification(
                    contentText = "已暂停 - 剩余 ${formatTime(pausedRemaining)}",
                    ongoing = true,
                    actions = listOf(
                        NotificationAction(android.R.drawable.ic_media_play, "继续", ACTION_RESUME),
                        NotificationAction(android.R.drawable.ic_delete, "取消", ACTION_STOP)
                    )
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update paused notification", e)
        }
    }

    private fun showCompletionNotification() {
        try {
            val durationText = formatDuration(totalSeconds)
            val tagLabel = tag?.let { "$it — " } ?: ""
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(
                NOTIFICATION_ID,
                buildTimerNotification(
                    title = "${tagLabel}倒计时结束!",
                    contentText = "时长 $durationText",
                    ongoing = false
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show completion notification", e)
        }
    }

    private data class NotificationAction(
        val icon: Int,
        val title: String,
        val action: String
    )

    @SuppressLint("NotificationPermission")
    private fun buildTimerNotification(
        title: String = "倒计时${tag?.let { " — $it" } ?: ""}",
        contentText: String,
        ongoing: Boolean,
        actions: List<NotificationAction> = emptyList()
    ): Notification {
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, NOTIFICATION_ID, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(contentPendingIntent)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)

        if (!ongoing) {
            builder.setAutoCancel(true)
        }

        actions.forEachIndexed { index, action ->
            val actionIntent = Intent(this, TimerCountdownService::class.java).apply {
                this.action = action.action
            }
            val actionPendingIntent = PendingIntent.getService(
                this, NOTIFICATION_ID + 10 + index, actionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(action.icon, action.title, actionPendingIntent)
        }

        return builder.build()
    }

    // endregion

    // region Utility

    private fun formatTime(totalSecs: Int): String {
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return "%02d:%02d".format(mins, secs)
    }

    private fun formatDuration(totalSecs: Int): String {
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return if (mins > 0) "${mins}分${secs}秒" else "${secs}秒"
    }

    // endregion
}
