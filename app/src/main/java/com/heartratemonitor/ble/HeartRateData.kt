package com.heartratemonitor.ble

/**
 * 心率数据模型
 */
data class HeartRateData(
    val value: Int,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        // BLE Heart Rate Service UUID
        val HEART_RATE_SERVICE_UUID = java.util.UUID.fromString("0000180D-0000-1000-8000-00805F9B34FB")
        // Heart Rate Measurement Characteristic UUID
        val HEART_RATE_MEASUREMENT_UUID = java.util.UUID.fromString("00002A37-0000-1000-8000-00805F9B34FB")

        /**
         * 从BLE数据解析心率值
         * 遵循Bluetooth Heart Rate Profile标准
         */
        fun parseFromBLEData(data: ByteArray): Int {
            // Heart Rate Measurement format
            // byte[0]: flags (bit 0 = 16-bit heart rate, bit 1 = sensor contact, bit 2 = sensor contact supported)
            // byte[1]: 8-bit heart rate value (0-255)
            // byte[2-3]: 16-bit heart rate value (if flag bit 0 = 1)

            return if (data.size >= 2) {
                val flags = data[0].toInt() and 0xFF
                val is16Bit = (flags and 0x01) != 0

                if (is16Bit && data.size >= 3) {
                    // 16-bit heart rate
                    (data[2].toInt() and 0xFF shl 8) or (data[1].toInt() and 0xFF)
                } else {
                    // 8-bit heart rate
                    data[1].toInt() and 0xFF
                }
            } else {
                throw IllegalArgumentException("Invalid heart rate data size: ${data.size}")
            }
        }
    }
}
