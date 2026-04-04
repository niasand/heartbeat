package com.heartratemonitor.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

import android.bluetooth.BluetoothProfile

/**
 * BLE扫描器
 * 负责扫描附近的Coros心率带设备
 */
class BleScanner(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager.adapter
        
    private val bluetoothLeScanner: BluetoothLeScanner?
        get() = bluetoothAdapter?.bluetoothLeScanner

    private var isScanning = false
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val deviceName = device.name
            val deviceAddress = device.address
            
            // 打印所有扫描到的设备，便于调试
            Log.d(TAG, "Found BLE device: name='$deviceName', address=$deviceAddress")
            
            val deviceInfo = DeviceInfo(
                name = deviceName ?: "未知设备",
                address = deviceAddress
            )

            // 检查是否是心率设备
            if (deviceInfo.isCorosDevice()) {
                Log.d(TAG, "Found heart rate device: ${deviceInfo.name} - ${deviceInfo.address}")
                _devices.value = _devices.value.toMutableSet().apply {
                    add(deviceInfo)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error: $errorCode")
            isScanning = false
            _scanState.value = ScanState.ERROR("扫描失败: $errorCode")
        }
    }

    private val _scanState = MutableStateFlow<ScanState>(ScanState.IDLE)
    val scanState: StateFlow<ScanState> = _scanState

    private val _devices = MutableStateFlow<Set<DeviceInfo>>(emptySet())
    val devices: StateFlow<Set<DeviceInfo>> = _devices

    sealed class ScanState {
        data object IDLE : ScanState()
        data object SCANNING : ScanState()
        data class ERROR(val message: String) : ScanState()
    }

    /**
     * 开始扫描Coros心率带设备
     */
    @SuppressLint("MissingPermission")
    fun startScan() {
        if (isScanning) {
            Log.d(TAG, "Already scanning")
            return
        }

        bluetoothLeScanner?.let { scanner ->
            Log.d(TAG, "Starting BLE scan...")
            
            // 清空之前的设备列表
            _devices.value = emptySet()

            // 创建扫描设置 - 不使用过滤器，扫描所有设备
            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                .build()

            try {
                // 不使用过滤器，扫描所有设备
                scanner.startScan(null, scanSettings, scanCallback)
                isScanning = true
                _scanState.value = ScanState.SCANNING
                Log.d(TAG, "Started scanning for all BLE devices")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start scan", e)
                _scanState.value = ScanState.ERROR("启动扫描失败: ${e.message}")
            }
        } ?: run {
            Log.e(TAG, "BluetoothLeScanner is null")
            _scanState.value = ScanState.ERROR("蓝牙未启用或不支持BLE")
        }
    }

    /**
     * 停止扫描
     */
    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!isScanning) {
            return
        }

        bluetoothLeScanner?.stopScan(scanCallback)
        isScanning = false
        _scanState.value = ScanState.IDLE
        Log.d(TAG, "Stopped scanning")
    }

    /**
     * 清空设备列表
     */
    fun clearDevices() {
        _devices.value = emptySet()
    }

    companion object {
        private const val TAG = "BleScanner"
    }
}
