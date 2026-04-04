package com.heartratemonitor.ble

/**
 * BLE设备信息
 */
data class DeviceInfo(
    val name: String,
    val address: String
) {
    // 判断是否是心率设备
    // 放宽匹配条件，支持多种心率带品牌和型号
    fun isCorosDevice(): Boolean {
        val upperName = name.uppercase()
        
        // 如果是未知设备，不匹配
        if (upperName == "未知设备" || upperName.isBlank()) {
            return false
        }

        // 支持的品牌和关键词
        val supportedKeywords = listOf(
            "COROS",        // 高驰
            "POLAR",        // 博能
            "EPIC",         // Epic
            "HEART",        // Heart
            "HRM",          // Heart Rate Monitor
            "心率带",        // 中文关键词
            "HR-",          // HR- 前缀
            "HR ",          // HR 开头
            "PULSE",        // Pulse
            "CHEST",        // Chest strap
            "STRAP",        // Strap
            "MONITOR",      // Monitor
            "BAND",         // Band
            "SENSOR",       // Sensor
            "Wahoo",        // Wahoo
            "GARMIN",       // Garmin
            "SUUNTO"        // Suunto
        )

        val result = supportedKeywords.any { upperName.contains(it) }
        return result
    }
}
