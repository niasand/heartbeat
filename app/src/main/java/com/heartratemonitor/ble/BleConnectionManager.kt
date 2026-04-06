package com.heartratemonitor.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * BLE连接管理器
 * 负责连接Coros心率带并监听心率数据
 */
class BleConnectionManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private var bluetoothGatt: BluetoothGatt? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // 自动重连相关
    private var lastConnectedDeviceAddress: String? = null
    private var autoReconnectEnabled = true
    private var isAutoReconnecting = false
    private val maxReconnectAttempts = 100
    private var reconnectAttempts = 0

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _heartRateFlow = MutableStateFlow<HeartRateData?>(null)
    val heartRateFlow: StateFlow<HeartRateData?> = _heartRateFlow

    private val _sessionStartTime = MutableStateFlow<Long?>(null)
    val sessionStartTime: StateFlow<Long?> = _sessionStartTime

    // 心跳超时检测：连接状态下超过此时间未收到心率数据则判定断开
    private var lastHeartRateTimestamp = 0L
    private val heartbeatTimeoutMs = 20_000L // 20秒无数据判定断开
    private var heartbeatWatchJob: kotlinx.coroutines.Job? = null
    private var hasReceivedData = false // 本次连接是否收到过心率数据
    private var disconnectedByTimeout = false // 标记是否由心跳超时触发的断开，防止 GATT 回调再次重连

    // 心率滴滴声相关
    private var toneGenerator: ToneGenerator? = null
    private var beepJob: kotlinx.coroutines.Job? = null
    @Volatile private var isBeepEnabled = true
    @Volatile private var currentBeepInterval: Long = 1200L
    
    // 自动重连状态
    private val _autoReconnectState = MutableStateFlow<AutoReconnectState>(AutoReconnectState.IDLE)
    val autoReconnectState: StateFlow<AutoReconnectState> = _autoReconnectState

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected to device")
                    _connectionState.value = ConnectionState.CONNECTED
                    _sessionStartTime.value = System.currentTimeMillis()
                    // 保存连接的设备地址
                    lastConnectedDeviceAddress = gatt.device.address
                    reconnectAttempts = 0
                    isAutoReconnecting = false
                    _autoReconnectState.value = AutoReconnectState.IDLE
                    hasReceivedData = false
                    startHeartbeatWatch()
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected from device")
                    _connectionState.value = ConnectionState.DISCONNECTED
                    _sessionStartTime.value = null
                    stopHeartbeatWatch()
                    stopBeepLoop()
                    cleanup()
                    // 心跳超时断开不触发自动重连，避免反复连接-超时循环
                    if (!disconnectedByTimeout && autoReconnectEnabled && lastConnectedDeviceAddress != null && !isAutoReconnecting) {
                        startAutoReconnect()
                    }
                    disconnectedByTimeout = false
                }
                else -> {
                    Log.d(TAG, "Connection state changed: $newState, status: $status")
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered")
                enableHeartRateNotifications(gatt)
            } else {
                Log.e(TAG, "Service discovery failed: $status")
                _connectionState.value = ConnectionState.ERROR("服务发现失败")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == HeartRateData.HEART_RATE_MEASUREMENT_UUID) {
                try {
                    val heartRate = HeartRateData.parseFromBLEData(characteristic.value)
                    _heartRateFlow.value = HeartRateData(heartRate)
                    lastHeartRateTimestamp = System.currentTimeMillis()
                    hasReceivedData = true
                    // 更新 beep 间隔（不重启 loop）
                    if (isBeepEnabled) {
                        ensureBeepLoopRunning()
                        currentBeepInterval = calculateBeepInterval(heartRate)
                    }
                    Log.d(TAG, "Heart rate updated: $heartRate BPM")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse heart rate", e)
                }
            }
        }
    }

    /**
     * 设置上次连接的设备地址（用于自动重连）
     */
    fun setLastDeviceAddress(address: String?) {
        lastConnectedDeviceAddress = address
    }
    
    /**
     * 获取上次连接的设备地址
     */
    fun getLastDeviceAddress(): String? = lastConnectedDeviceAddress
    
    /**
     * 启用/禁用自动重连
     */
    fun setAutoReconnectEnabled(enabled: Boolean) {
        autoReconnectEnabled = enabled
        if (!enabled) {
            isAutoReconnecting = false
            _autoReconnectState.value = AutoReconnectState.IDLE
        }
    }
    
    /**
     * 开始自动重连
     */
    private fun startAutoReconnect() {
        if (lastConnectedDeviceAddress == null || isAutoReconnecting) {
            return
        }
        
        isAutoReconnecting = true
        reconnectAttempts = 0
        scope.launch {
            attemptReconnect()
        }
    }
    
    /**
     * 尝试重连
     */
    private suspend fun attemptReconnect() {
        while (reconnectAttempts < maxReconnectAttempts && isAutoReconnecting && autoReconnectEnabled) {
            // 检查是否已连接
            if (_connectionState.value == ConnectionState.CONNECTED) {
                return
            }

            reconnectAttempts++
            _autoReconnectState.value = AutoReconnectState.RECONNECTING(reconnectAttempts, maxReconnectAttempts)
            Log.d(TAG, "Auto reconnect attempt $reconnectAttempts/$maxReconnectAttempts")

            // 等待一段时间后重连
            delay(2000L * reconnectAttempts)

            // 再次检查是否已连接（可能在上次连接尝试中成功了）
            if (_connectionState.value == ConnectionState.CONNECTED) {
                return
            }

            // 尝试连接设备
            lastConnectedDeviceAddress?.let { address ->
                // 如果当前状态不是CONNECTING，才发起新的连接请求
                if (_connectionState.value != ConnectionState.CONNECTING) {
                    connectToDeviceInternal(address)
                }
            }

            // 等待一小段时间让连接尝试完成
            delay(1000L)
        }

        if (reconnectAttempts >= maxReconnectAttempts && _connectionState.value != ConnectionState.CONNECTED) {
            Log.d(TAG, "Auto reconnect failed after $maxReconnectAttempts attempts")
            _autoReconnectState.value = AutoReconnectState.FAILED
            isAutoReconnecting = false
        }
    }
    
    /**
     * 停止自动重连
     */
    fun stopAutoReconnect() {
        isAutoReconnecting = false
        _autoReconnectState.value = AutoReconnectState.IDLE
    }

    /**
     * 连接到Coros心率带设备
     */
    @SuppressLint("MissingPermission")
    fun connectToDevice(deviceAddress: String): Boolean {
        lastConnectedDeviceAddress = deviceAddress
        return connectToDeviceInternal(deviceAddress)
    }
    
    /**
     * 内部连接方法
     */
    @SuppressLint("MissingPermission")
    private fun connectToDeviceInternal(deviceAddress: String): Boolean {
        bluetoothAdapter?.getRemoteDevice(deviceAddress)?.let { device ->
            try {
                _connectionState.value = ConnectionState.CONNECTING
                bluetoothGatt = device.connectGatt(context, false, gattCallback)
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to device", e)
                _connectionState.value = ConnectionState.ERROR("连接失败: ${e.message}")
                return false
            }
        }
        _connectionState.value = ConnectionState.ERROR("设备不存在")
        return false
    }

    /**
     * 断开当前连接
     */
    fun disconnect() {
        autoReconnectEnabled = false
        isAutoReconnecting = false
        _autoReconnectState.value = AutoReconnectState.IDLE
        stopHeartbeatWatch()
        stopBeepLoop()
        bluetoothGatt?.disconnect()
        cleanup()
    }
    
    /**
     * 断开连接但保持自动重连能力
     */
    fun disconnectWithAutoReconnect() {
        bluetoothGatt?.disconnect()
        cleanup()
    }

    /**
     * 启用心率通知
     */
    private fun enableHeartRateNotifications(gatt: BluetoothGatt) {
        val service = gatt.getService(HeartRateData.HEART_RATE_SERVICE_UUID)
        if (service == null) {
            Log.e(TAG, "Heart Rate Service not found")
            _connectionState.value = ConnectionState.ERROR("心率服务未找到")
            return
        }

        val characteristic = service.getCharacteristic(HeartRateData.HEART_RATE_MEASUREMENT_UUID)
        if (characteristic == null) {
            Log.e(TAG, "Heart Rate Measurement characteristic not found")
            _connectionState.value = ConnectionState.ERROR("心率特征未找到")
            return
        }

        // 设置通知
        gatt.setCharacteristicNotification(characteristic, true)

        // 写入描述符以启用通知
        val descriptor = characteristic.getDescriptor(
            java.util.UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
        )
        descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        gatt.writeDescriptor(descriptor)

        Log.d(TAG, "Heart rate notifications enabled")
    }

    /**
     * 清理资源
     */
    private fun cleanup() {
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    /**
     * 启动心跳超时检测
     * 仅在收到过至少一次心率数据后才开始检测超时，避免误判初次连接等待期
     */
    private fun startHeartbeatWatch() {
        stopHeartbeatWatch()
        lastHeartRateTimestamp = System.currentTimeMillis()
        heartbeatWatchJob = scope.launch {
            while (true) {
                delay(5000L) // 每5秒检查一次
                if (_connectionState.value != ConnectionState.CONNECTED) {
                    return@launch
                }
                // 只有收到过数据后才开始检测超时
                if (!hasReceivedData) {
                    continue
                }
                val elapsed = System.currentTimeMillis() - lastHeartRateTimestamp
                if (elapsed > heartbeatTimeoutMs) {
                    Log.w(TAG, "Heartbeat timeout: no data for ${elapsed}ms, forcing disconnect")
                    disconnectedByTimeout = true
                    _connectionState.value = ConnectionState.DISCONNECTED
                    _sessionStartTime.value = null
                    stopHeartbeatWatch()
                    cleanup()
                    // 心跳超时断开不触发自动重连，避免反复连接-超时循环
                    return@launch
                }
            }
        }
    }

    /**
     * 停止心跳超时检测
     */
    private fun stopHeartbeatWatch() {
        heartbeatWatchJob?.cancel()
        heartbeatWatchJob = null
    }

    /**
     * 设置心跳声音开关
     */
    fun setBeepEnabled(enabled: Boolean) {
        isBeepEnabled = enabled
        if (!enabled) stopBeepLoop()
    }

    /**
     * 确保 beep loop 正在运行（幂等，不会重复创建）
     */
    private fun ensureBeepLoopRunning() {
        if (beepJob?.isActive == true) return
        try {
            if (toneGenerator == null) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            }
        } catch (e: Exception) {
            Log.w(TAG, "ToneGenerator init failed", e)
            return
        }
        beepJob = scope.launch {
            while (true) {
                val interval = currentBeepInterval
                try {
                    toneGenerator?.stopTone()
                    toneGenerator?.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
                } catch (e: Exception) {
                    Log.w(TAG, "Beep failed", e)
                }
                val nextTime = System.currentTimeMillis() + interval
                delay(nextTime - System.currentTimeMillis())
            }
        }
    }

    /**
     * 停止 beep loop 并释放 ToneGenerator
     */
    private fun stopBeepLoop() {
        beepJob?.cancel()
        beepJob = null
        currentBeepInterval = 1200L
    }

    /**
     * 根据心率区间计算滴声间隔
     * ≤120: 1200ms  >120: 快30%  >130: 快50%  >150: 快80%  >165: 快100%
     */
    private fun calculateBeepInterval(heartRate: Int): Long {
        val baseInterval = 1200L
        val speedMultiplier = when {
            heartRate > 165 -> 2.0
            heartRate > 150 -> 1.8
            heartRate > 130 -> 1.5
            heartRate > 120 -> 1.3
            else -> 1.0
        }
        return (baseInterval / speedMultiplier).toLong()
    }

    companion object {
        private const val TAG = "BleConnectionManager"
    }
}

/**
 * BLE连接状态
 */
sealed class ConnectionState {
    data object DISCONNECTED : ConnectionState()
    data object CONNECTING : ConnectionState()
    data object CONNECTED : ConnectionState()
    data class ERROR(val message: String) : ConnectionState()
}

/**
 * 自动重连状态
 */
sealed class AutoReconnectState {
    data object IDLE : AutoReconnectState()
    data class RECONNECTING(val attempt: Int, val maxAttempts: Int) : AutoReconnectState()
    data object FAILED : AutoReconnectState()
}
