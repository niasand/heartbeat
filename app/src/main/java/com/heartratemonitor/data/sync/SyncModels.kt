package com.heartratemonitor.data.sync

/**
 * Sync request body sent to Cloudflare Workers
 */
data class SyncRequest(
    val heartRates: List<HeartRateRecord>,
    val timerSessions: List<TimerSessionRecord>
)

data class HeartRateRecord(
    val timestamp: Long,
    val heartRate: Int
)

data class TimerSessionRecord(
    val timestamp: Long,
    val durationSeconds: Int
)

/**
 * Sync response from Cloudflare Workers
 */
data class SyncResponse(
    val success: Boolean,
    val message: String? = null,
    val syncedHeartRates: Int = 0,
    val syncedTimerSessions: Int = 0
)

/**
 * Sync result for UI layer
 */
data class SyncResult(
    val success: Boolean,
    val syncedHeartRates: Int = 0,
    val syncedTimerSessions: Int = 0,
    val error: String? = null
)
