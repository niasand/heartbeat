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
    val durationSeconds: Int,
    val tag: String? = null
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

/**
 * Response from GET / fetch endpoint for restore
 */
data class FetchResponse(
    val success: Boolean,
    val heartRates: List<FetchedHeartRate> = emptyList(),
    val timerSessions: List<FetchedTimerSession> = emptyList(),
    val message: String? = null
)

data class FetchedHeartRate(
    val timestamp: Long,
    val heart_rate: Int
)

data class FetchedTimerSession(
    val timestamp: Long,
    val duration_seconds: Int,
    val tag: String? = null
)

/**
 * Restore result for UI layer
 */
data class RestoreResult(
    val success: Boolean,
    val restoredHeartRates: Int = 0,
    val restoredTimerSessions: Int = 0,
    val error: String? = null
)

/**
 * Delete request body for removing records from cloud
 */
data class DeleteRequest(
    val timestamps: List<Long>
)

data class DeleteResponse(
    val success: Boolean,
    val deletedCount: Int = 0,
    val message: String? = null
)
