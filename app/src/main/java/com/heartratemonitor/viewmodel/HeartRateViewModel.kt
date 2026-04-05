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

    // Timer sessions filtered by time range
    private val _timerFilterDays = MutableStateFlow(7)
    val timerFilterDays: StateFlow<Int> = _timerFilterDays

    // Timer sessions filtered by tag (null = show all)
    private val _timerFilterTag = MutableStateFlow<String?>(null)
    val timerFilterTag: StateFlow<String?> = _timerFilterTag

    // Time-range filtered sessions (before tag filter)
    private val _sessionsInTimeRange = MutableStateFlow<List<TimerSessionEntity>>(emptyList())

    // Final filtered sessions = time range filter + tag filter
    val filteredTimerSessions: StateFlow<List<TimerSessionEntity>> = combine(
        _sessionsInTimeRange, _timerFilterTag
    ) { sessions, tag ->
        if (tag.isNullOrBlank()) sessions else sessions.filter { it.tag == tag }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _filteredTimerCountByDate = MutableStateFlow<List<DateCountPair>>(emptyList())
    val filteredTimerCountByDate: StateFlow<List<DateCountPair>> = _filteredTimerCountByDate

    private val _syncState = MutableStateFlow<SyncState>(SyncState.IDLE)
    val syncState: StateFlow<SyncState> = _syncState

    val lastSyncTime: StateFlow<Long> = preferencesManager.lastSyncTimeFlow.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        0L
    )

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
        
        // 加载上次连接的设备地址
        viewModelScope.launch {
            lastDeviceAddress.collect { address ->
                address?.let {
                    bleConnectionManager.setLastDeviceAddress(it)
                }
            }
        }

        // 加载过去7天的每日心率统计
        viewModelScope.launch {
            val sevenDaysAgo = System.currentTimeMillis() - 7 * 24 * 3600 * 1000
            _dailyStats.value = heartRateRepository.getDailyStats(sevenDaysAgo)
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

        viewModelScope.launch {
            _timerFilterDays.collect { days ->
                val afterTimestamp = System.currentTimeMillis() - days.toLong() * 24 * 3600 * 1000
                timerSessionRepository.getCountByDateAfter(afterTimestamp).collect { pairs ->
                    _filteredTimerCountByDate.value = pairs
                }
            }
        }
    }

    fun startScan() {
        bleScanner.startScan()
    }

    fun stopScan() {
        bleScanner.stopScan()
    }

    fun connectToDevice(address: String) {
        viewModelScope.launch {
            bleScanner.stopScan()
            
            // 保存设备地址
            preferencesManager.saveLastDeviceAddress(address)
            
            // Start the service to ensure it runs in the background
            val intent = Intent(context, BleHeartRateService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            
            bleConnectionManager.connectToDevice(address)
        }
    }

    fun disconnect() {
        bleConnectionManager.disconnect()
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
        if (entities.isEmpty()) {
            _heartRateStats.value = null
            return
        }

        val avg = entities.map { it.heartRate }.average()
        val max = entities.maxOfOrNull { it.heartRate } ?: 0
        val min = entities.minOfOrNull { it.heartRate } ?: 0
        val count = entities.size

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
     * Sync local data to Cloudflare D1
     */
    fun syncToCloud() {
        viewModelScope.launch {
            _syncState.value = SyncState.SYNCING
            val result = syncRepository.syncToCloud()
            _syncState.value = if (result.success) {
                SyncState.SUCCESS(result.syncedHeartRates, result.syncedTimerSessions)
            } else {
                SyncState.ERROR(result.error ?: "Sync failed")
            }
        }
    }

    /**
     * Restore all data from Cloudflare D1 to local
     */
    fun restoreFromCloud() {
        viewModelScope.launch {
            _restoreState.value = RestoreState.RESTORING
            val result = syncRepository.restoreFromCloud()
            _restoreState.value = if (result.success) {
                RestoreState.SUCCESS(result.restoredHeartRates, result.restoredTimerSessions)
            } else {
                RestoreState.ERROR(result.error ?: "Restore failed")
            }
        }
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
}
