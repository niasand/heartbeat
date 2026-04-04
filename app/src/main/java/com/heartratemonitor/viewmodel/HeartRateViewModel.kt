package com.heartratemonitor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heartratemonitor.data.entity.HeartRateEntity
import com.heartratemonitor.data.pref.PreferencesManager
import com.heartratemonitor.data.repository.HeartRateRepository
import com.heartratemonitor.data.repository.TimerSessionRepository
import com.heartratemonitor.data.entity.TimerSessionEntity
import com.heartratemonitor.data.dao.DateCountPair
import com.heartratemonitor.ble.BleConnectionManager
import com.heartratemonitor.ble.BleScanner
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
     * Save timer session when countdown finishes
     */
    fun saveTimerSession(durationSeconds: Int) {
        viewModelScope.launch {
            timerSessionRepository.saveSession(durationSeconds)
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
        val sdf = SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.CHINA)
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
