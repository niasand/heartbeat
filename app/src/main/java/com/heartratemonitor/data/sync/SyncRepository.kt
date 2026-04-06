package com.heartratemonitor.data.sync

import com.heartratemonitor.data.dao.HeartRateDao
import com.heartratemonitor.data.dao.TimerSessionDao
import com.heartratemonitor.data.entity.HeartRateEntity
import com.heartratemonitor.data.entity.TimerSessionEntity
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
                TimerSessionRecord(timestamp = it.timestamp, durationSeconds = it.durationSeconds, tag = it.tag)
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

    /**
     * Fetch all data from cloud and restore to local database.
     * Existing local data is preserved (REPLACE on conflict).
     */
    suspend fun restoreFromCloud(): RestoreResult {
        val response = syncApiClient.fetchData()

        if (!response.success) {
            return RestoreResult(
                success = false,
                error = response.message ?: "Fetch failed"
            )
        }

        val hrEntities = response.heartRates.map {
            HeartRateEntity(timestamp = it.timestamp, heartRate = it.heart_rate)
        }
        if (hrEntities.isNotEmpty()) {
            heartRateDao.insertAll(hrEntities)
        }

        val tsEntities = response.timerSessions.map {
            TimerSessionEntity(timestamp = it.timestamp, durationSeconds = it.duration_seconds, tag = it.tag)
        }
        if (tsEntities.isNotEmpty()) {
            timerSessionDao.insertAll(tsEntities)
        }

        return RestoreResult(
            success = true,
            restoredHeartRates = hrEntities.size,
            restoredTimerSessions = tsEntities.size
        )
    }

    /**
     * Delete timer sessions by timestamps from cloud (D1)
     */
    suspend fun deleteFromCloud(timestamps: List<Long>): DeleteResponse {
        if (timestamps.isEmpty()) {
            return DeleteResponse(success = true, deletedCount = 0)
        }
        return syncApiClient.deleteData(DeleteRequest(timestamps))
    }
}
