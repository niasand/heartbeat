package com.heartratemonitor.data.sync

import com.heartratemonitor.data.dao.HeartRateDao
import com.heartratemonitor.data.dao.TimerSessionDao
import com.heartratemonitor.data.pref.PreferencesManager
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for syncing local Room data to Cloudflare D1
 */
@Singleton
class SyncRepository @Inject constructor(
    private val heartRateDao: HeartRateDao,
    private val timerSessionDao: TimerSessionDao,
    private val syncApiClient: SyncApiClient,
    private val preferencesManager: PreferencesManager
) {
    /**
     * Sync all data after lastSyncTime to the cloud.
     * If lastSyncTime is 0, syncs everything.
     */
    suspend fun syncToCloud(): SyncResult {
        val lastSyncTime = preferencesManager.lastSyncTimeFlow.first()

        val heartRates = heartRateDao.getAfter(lastSyncTime)
        val timerSessions = timerSessionDao.getAfter(lastSyncTime)

        if (heartRates.isEmpty() && timerSessions.isEmpty()) {
            return SyncResult(
                success = true,
                syncedHeartRates = 0,
                syncedTimerSessions = 0
            )
        }

        val request = SyncRequest(
            heartRates = heartRates.map {
                HeartRateRecord(timestamp = it.timestamp, heartRate = it.heartRate)
            },
            timerSessions = timerSessions.map {
                TimerSessionRecord(timestamp = it.timestamp, durationSeconds = it.durationSeconds)
            }
        )

        val response = syncApiClient.syncData(request)

        if (response.success) {
            preferencesManager.saveLastSyncTime(System.currentTimeMillis())
        }

        return SyncResult(
            success = response.success,
            syncedHeartRates = response.syncedHeartRates,
            syncedTimerSessions = response.syncedTimerSessions,
            error = response.message
        )
    }
}
