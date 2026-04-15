package com.heartratemonitor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heartratemonitor.data.entity.HeartRateEntity
import com.heartratemonitor.data.pref.PreferencesManager
import com.heartratemonitor.data.repository.HeartRateRepository
import com.heartratemonitor.data.repository.TimerSessionRepository
import com.heartratemonitor.data.entity.TimerSessionEntity
import com.heartratemonitor.data.sync.SyncRepository
import com.heartratemonitor.data.sync.SyncResult
import com.heartratemonitor.data.dao.DateCountPair
import com.heartratemonitor.data.dao.DailyHeartRateStats
import com.heartratemonitor.ble.BleConnectionManager
import com.heartratemonitor.ble.BleScanner
import kotlinx.coroutines.flow.combine
import com.heartratemonitor.ble.DeviceInfo
import com.heartratemonitor.ble.ConnectionState
import com.heartratemonitor.ble.AutoReconnectState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.heartratemonitor.service.BleHeartRateService
import com.heartratemonitor.service.TimerCountdownService
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * 心率数据ViewModel
 */
@HiltViewModel
class HeartRateViewModel @Inject constructor(
    private val heartRateRepository: HeartRateRepository,
    private val timerSessionRepository: TimerSessionRepository,
    private val preferencesManager: PreferencesManager,
    private val bleScanner: BleScanner,
    private val bleConnectionManager: BleConnectionManager,
    private val syncRepository: SyncRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _currentHeartRate = MutableStateFlow<Int?>(null)
    val currentHeartRate: StateFlow<Int?> = _currentHeartRate

    // 严格递增的数据版本号，每次保存心率时 +1，用于强制 UI 刷新
    private val _dataVersion = MutableStateFlow(0)
    val dataVersion: StateFlow<Int> = _dataVersion

    // 跟踪是否已尝试自动连接（跨 Activity 切换不丢失）
    private val _hasAutoConnectAttempted = MutableStateFlow(false)
    val hasAutoConnectAttempted: StateFlow<Boolean> = _hasAutoConnectAttempted
    private val _hasAutoConnectedDevice = MutableStateFlow(false)
    val hasAutoConnectedDevice: StateFlow<Boolean> = _hasAutoConnectedDevice

    fun resetAutoConnectAttempted() {
        _hasAutoConnectAttempted.value = false
        _hasAutoConnectedDevice.value = false
    }

    // 心率声音开关
    @Volatile private var isBeepEnabled = preferencesManager.cachedHeartbeatSoundEnabled

    val heartbeatSoundEnabled: StateFlow<Boolean> = preferencesManager.heartbeatSoundEnabledFlow.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        PreferencesManager.DEFAULT_HEARTBEAT_SOUND_ENABLED
    )

    // Use connection state from BleConnectionManager
    val connectionState: StateFlow<ConnectionState> = bleConnectionManager.connectionState
    val sessionStartTime: StateFlow<Long?> = bleConnectionManager.sessionStartTime
    val autoReconnectState: StateFlow<AutoReconnectState> = bleConnectionManager.autoReconnectState

    val scannedDevices: StateFlow<Set<DeviceInfo>> = bleScanner.devices

    val scanState: StateFlow<BleScanner.ScanState> = bleScanner.scanState

    private val _heartRateHistory = MutableStateFlow<List<HeartRateEntity>>(emptyList())
    val heartRateHistory: StateFlow<List<HeartRateEntity>> = _heartRateHistory

    private val _heartRateStats = MutableStateFlow<HeartRateStats?>(null)
    val heartRateStats: StateFlow<HeartRateStats?> = _heartRateStats

    private val _dailyStats = MutableStateFlow<List<DailyHeartRateStats>>(emptyList())
    val dailyStats: StateFlow<List<DailyHeartRateStats>> = _dailyStats

    private val _serviceState = MutableStateFlow<ServiceState>(ServiceState.IDLE)
    val serviceState: StateFlow<ServiceState> = _serviceState

    val highThreshold: StateFlow<Int> = preferencesManager.highThresholdFlow.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        PreferencesManager.DEFAULT_HIGH_THRESHOLD
    )

    val lowThreshold: StateFlow<Int> = preferencesManager.lowThresholdFlow.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        PreferencesManager.DEFAULT_LOW_THRESHOLD
    )

    val themeColor: StateFlow<String> = preferencesManager.themeColorFlow.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        PreferencesManager.DEFAULT_THEME_COLOR
    )
    
    val lastDeviceAddress: StateFlow<String?> = preferencesManager.lastDeviceAddressFlow.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        null
    )

    val timerSoundUri: StateFlow<String?> = preferencesManager.timerSoundUriFlow.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        null
    )

    val siliconFlowApiKey: StateFlow<String?> = preferencesManager.siliconFlowApiKeyFlow.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        null
    )

    val timerSessionHistory: StateFlow<List<TimerSessionEntity>> = timerSessionRepository.getAllSessions().stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        emptyList()
    )

    val timerCountByDate: StateFlow<List<DateCountPair>> = timerSessionRepository.getCountByDate().stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        emptyList()
    )

    // Timer countdown service state (from TimerCountdownService)
    private val _timerServiceState = MutableStateFlow<TimerCountdownService.TimerServiceState>(
        TimerCountdownService.TimerServiceState.IDLE
    )
    val timerServiceState: StateFlow<TimerCountdownService.TimerServiceState> = _timerServiceState
    private var timerStateCollectJob: Job? = null

    // Timer sessions filtered by time range
    private val _timerFilterDays = MutableStateFlow(7)
    val timerFilterDays: StateFlow<Int> = _timerFilterDays

    // Timer sessions filtered by tag (null = show all)
    private val _timerFilterTag = MutableStateFlow<String?>(null)
    val timerFilterTag: StateFlow<String?> = _timerFilterTag

    // Time-range filtered sessions (before tag filter)
    private val _sessionsInTimeRange = MutableStateFlow<List<TimerSessionEntity>>(emptyList())
    val sessionsInTimeRange: StateFlow<List<TimerSessionEntity>> = _sessionsInTimeRange

    // Final filtered sessions = time range filter + tag filter
    val filteredTimerSessions: StateFlow<List<TimerSessionEntity>> = combine(
        _sessionsInTimeRange, _timerFilterTag
    ) { sessions, tag ->
        if (tag.isNullOrBlank()) sessions else sessions.filter { it.tag == tag }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Tag-filtered count by date for chart
    val filteredTimerCountByDate: StateFlow<List<DateCountPair>> = combine(
        _sessionsInTimeRange, _timerFilterTag
    ) { sessions, tag ->
        val filtered = if (tag.isNullOrBlank()) sessions else sessions.filter { it.tag == tag }
        filtered
            .groupBy { session ->
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    .format(java.util.Date(session.timestamp))
            }
            .map { (date, list) -> DateCountPair(date, list.size) }
            .sortedBy { it.date }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _syncState = MutableStateFlow<SyncState>(SyncState.IDLE)
    val syncState: StateFlow<SyncState> = _syncState

    val lastSyncTime: StateFlow<Long> = preferencesManager.lastSyncTimeFlow.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        0L
    )

    val hasLocalBackup: Boolean
        get() = syncRepository.hasLocalBackup()

    val localBackupTime: String?
        get() = syncRepository.getLocalBackupTime()

    sealed class SyncState {
        data object IDLE : SyncState()
        data object SYNCING : SyncState()
        data class SUCCESS(val syncedHeartRates: Int, val syncedTimerSessions: Int) : SyncState()
        data class ERROR(val message: String) : SyncState()
    }

    private val _restoreState = MutableStateFlow<RestoreState>(RestoreState.IDLE)
    val restoreState: StateFlow<RestoreState> = _restoreState

    sealed class RestoreState {
        data object IDLE : RestoreState()
        data object RESTORING : RestoreState()
        data class SUCCESS(val restoredHeartRates: Int, val restoredTimerSessions: Int) : RestoreState()
        data class ERROR(val message: String) : RestoreState()
    }

    sealed class ServiceState {
        data object IDLE : ServiceState()
        data object SCANNING : ServiceState()
        data object CONNECTING : ServiceState()
        data object MONITORING : ServiceState()
        data class ERROR(val message: String) : ServiceState()
    }

    data class HeartRateStats(
        val avg: Double,
        val max: Int,
        val min: Int,
        val count: Int
    )

    init {
        // 加载所有历史心率数据（限制条数）
        viewModelScope.launch {
            heartRateRepository.getAllHeartRates().collect { entities ->
                _heartRateHistory.value = entities
                calculateStats(entities)
            }
        }

        // Listen to heart rate data from BleConnectionManager
        viewModelScope.launch {
            bleConnectionManager.heartRateFlow.collect { heartRateData ->
                heartRateData?.let {
                    updateCurrentHeartRate(it.value)
                }
            }
        }

        // 同步声音开关状态到 BleConnectionManager
        viewModelScope.launch {
            preferencesManager.heartbeatSoundEnabledFlow.collect { enabled ->
                isBeepEnabled = enabled
                bleConnectionManager.setBeepEnabled(enabled)
            }
        }


        // 加载上次连接的设备地址
        viewModelScope.launch {
            lastDeviceAddress.collect { address ->
                address?.let {
                    bleConnectionManager.setLastDeviceAddress(it)
                }
            }
        }

        // 加载过去7天的每日心率统计（随数据变化自动刷新）
        viewModelScope.launch {
            heartRateRepository.getAllHeartRates().collect {
                val sevenDaysAgo = System.currentTimeMillis() - 7 * 24 * 3600 * 1000
                _dailyStats.value = heartRateRepository.getDailyStats(sevenDaysAgo)
            }
        }

        // Load timer sessions filtered by time range (tag filter applied via combine above)
        viewModelScope.launch {
            _timerFilterDays.collect { days ->
                val afterTimestamp = System.currentTimeMillis() - days.toLong() * 24 * 3600 * 1000
                timerSessionRepository.getSessionsAfter(afterTimestamp).collect { sessions ->
                    _sessionsInTimeRange.value = sessions
                }
            }
        }
    }

    fun startScan() {
        bleScanner.startScan()
    }

    fun markAutoConnectAttempted() {
        _hasAutoConnectAttempted.value = true
    }

    fun markAutoConnectedDevice() {
        _hasAutoConnectedDevice.value = true
    }

    fun stopScan() {
        bleScanner.stopScan()
    }

    fun connectToDevice(address: String) {
        viewModelScope.launch {
            bleScanner.stopScan()

            // 保存设备地址
            preferencesManager.saveLastDeviceAddress(address)

            bleConnectionManager.connectToDevice(address)
        }
    }

    // 连接成功后启动前台服务（避免在连接过程中弹通知把其他 Activity 顶掉）
    init {
        viewModelScope.launch {
            bleConnectionManager.connectionState.collect { state ->
                if (state is ConnectionState.CONNECTED) {
                    val intent = Intent(context, BleHeartRateService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                }
            }
        }
    }

    fun disconnect() {
        bleConnectionManager.disconnect()
        // 手动断开后重置自动连接标记，下次启动时可以重新尝试
        _hasAutoConnectAttempted.value = false
        _hasAutoConnectedDevice.value = false
    }
    
    fun setAutoReconnectEnabled(enabled: Boolean) {
        bleConnectionManager.setAutoReconnectEnabled(enabled)
    }
    
    fun stopAutoReconnect() {
        bleConnectionManager.stopAutoReconnect()
    }

    /**
     * 更新当前心率
     */
    fun updateCurrentHeartRate(heartRate: Int) {
        // 校验心率数据有效性：心率不能为0，也不能超过300
        // 正常人体心率范围约为40-220 BPM，允许一定的容差
        val isValidHeartRate = heartRate in 30..300

        if (!isValidHeartRate) {
            Log.w("HeartRateViewModel", "Invalid heart rate value: $heartRate, ignoring")
            return
        }

        _currentHeartRate.value = heartRate
        _dataVersion.value++

        // 保存到数据库
        viewModelScope.launch {
            heartRateRepository.saveHeartRate(heartRate)
        }
    }

    /**
     * 更新服务状态
     */
    fun updateServiceState(state: ServiceState) {
        _serviceState.value = state
    }

    /**
     * 计算统计数据
     */
    private fun calculateStats(entities: List<HeartRateEntity>) {
        // 只统计过去24小时的数据，与图表窗口一致
        val oneDayAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        val recentEntities = entities.filter { it.timestamp >= oneDayAgo }

        if (recentEntities.isEmpty()) {
            _heartRateStats.value = null
            return
        }

        val avg = recentEntities.map { it.heartRate }.average()
        val max = recentEntities.maxOfOrNull { it.heartRate } ?: 0
        val min = recentEntities.minOfOrNull { it.heartRate } ?: 0
        val count = recentEntities.size

        _heartRateStats.value = HeartRateStats(avg, max, min, count)
    }

    /**
     * 保存高心率阈值
     */
    fun saveHighThreshold(value: Int) {
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            preferencesManager.saveHighThreshold(value)
        }
    }

    /**
     * 保存低心率阈值
     */
    fun saveLowThreshold(value: Int) {
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            preferencesManager.saveLowThreshold(value)
        }
    }

    /**
     * Set timer history filter time range in days
     */
    fun setTimerFilterDays(days: Int) {
        _timerFilterDays.value = days
    }

    fun setTimerFilterTag(tag: String?) {
        _timerFilterTag.value = tag
    }

    /**
     * Delete a timer session by timestamp, both local and cloud.
     * Local deletion first for instant UI feedback.
     */
    fun deleteTimerSession(timestamp: Long) {
        viewModelScope.launch {
            timerSessionRepository.deleteSession(timestamp)
            syncRepository.deleteFromCloud(listOf(timestamp))
        }
    }

    /**
     * Save timer session when countdown finishes
     */
    fun saveTimerSession(durationSeconds: Int, tag: String? = null) {
        viewModelScope.launch {
            timerSessionRepository.saveSession(durationSeconds, tag)
        }
    }

    /**
     * 保存主题颜色
     */
    fun saveThemeColor(value: String) {
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            preferencesManager.saveThemeColor(value)
        }
    }

    /**
     * 保存倒计时铃声 URI
     */
    fun saveTimerSoundUri(value: String) {
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            preferencesManager.saveTimerSoundUri(value)
        }
    }

    /**
     * 保存硅基流动 API Key
     */
    fun saveSiliconFlowApiKey(value: String) {
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            preferencesManager.saveSiliconFlowApiKey(value)
        }
    }

    /**
     * Sync local data to Cloudflare D1
     */
    fun syncToCloud() {
        viewModelScope.launch {
            _syncState.value = SyncState.SYNCING
            _restoreState.value = RestoreState.IDLE
            val result = syncRepository.syncToCloud()
            _syncState.value = if (result.success) {
                SyncState.SUCCESS(result.syncedHeartRates, result.syncedTimerSessions)
            } else {
                SyncState.ERROR(result.error ?: "Sync failed")
            }
        }
    }

    /**
     * Restore data: local backup first, fallback to cloud
     */
    fun restoreFromBackup() {
        viewModelScope.launch {
            _restoreState.value = RestoreState.RESTORING
            _syncState.value = SyncState.IDLE
            val result = syncRepository.restoreFromBackup()
            _restoreState.value = if (result.success) {
                RestoreState.SUCCESS(result.restoredHeartRates, result.restoredTimerSessions)
            } else {
                RestoreState.ERROR(result.error ?: "Restore failed")
            }
        }
    }

    /**
     * @deprecated Use restoreFromBackup() instead
     */
    fun restoreFromCloud() {
        restoreFromBackup()
    }

    /**
     * 清空历史数据
     */
    fun clearHistory() {
        viewModelScope.launch {
            heartRateRepository.deleteAll()
        }
    }

    /**
     * 格式化时间戳为中国时间
     */
    fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.CHINA)
        return sdf.format(Date(timestamp))
    }

    /**
     * 格式化日期为中文格式
     */
    fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss", Locale.CHINA)
        return sdf.format(Date(timestamp))
    }

    /**
     * 获取指定时间范围的数据
     */
    fun getHeartRatesBetween(startTime: Long, endTime: Long) {
        viewModelScope.launch {
            heartRateRepository.getHeartRatesBetween(startTime, endTime).collect { entities ->
                _heartRateHistory.value = entities
                calculateStats(entities)
            }
        }
    }

    /**
     * 保存心率声音开关
     */
    fun saveHeartbeatSoundEnabled(value: Boolean) {
        isBeepEnabled = value
        bleConnectionManager.setBeepEnabled(value)
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            preferencesManager.saveHeartbeatSoundEnabled(value)
        }
    }

    /**
     * Bind to TimerCountdownService to observe its state
     */
    fun bindTimerService(service: TimerCountdownService) {
        timerStateCollectJob?.cancel()
        timerStateCollectJob = viewModelScope.launch {
            service.serviceState.collect { state ->
                _timerServiceState.value = state
            }
        }
    }

    fun unbindTimerService() {
        timerStateCollectJob?.cancel()
        timerStateCollectJob = null
    }

    /**
     * Start timer countdown via foreground service
     */
    fun startTimerService(totalSeconds: Int, tag: String?) {
        val intent = Intent(context, TimerCountdownService::class.java).apply {
            action = TimerCountdownService.ACTION_START
            putExtra(TimerCountdownService.EXTRA_TOTAL_SECONDS, totalSeconds)
            putExtra(TimerCountdownService.EXTRA_TAG, tag)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun pauseTimerService() {
        val intent = Intent(context, TimerCountdownService::class.java).apply {
            action = TimerCountdownService.ACTION_PAUSE
        }
        context.startService(intent)
    }

    fun resumeTimerService() {
        val intent = Intent(context, TimerCountdownService::class.java).apply {
            action = TimerCountdownService.ACTION_RESUME
        }
        context.startService(intent)
    }

    fun stopTimerService() {
        val intent = Intent(context, TimerCountdownService::class.java).apply {
            action = TimerCountdownService.ACTION_STOP
        }
        context.startService(intent)
    }

    override fun onCleared() {
        super.onCleared()
    }
}
