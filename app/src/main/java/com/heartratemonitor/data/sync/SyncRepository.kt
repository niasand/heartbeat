package com.heartratemonitor.data.sync

import android.util.Log
import com.heartratemonitor.data.dao.AlarmRecordDao
import com.heartratemonitor.data.dao.HeartRateDao
import com.heartratemonitor.data.dao.TimerSessionDao
import com.heartratemonitor.data.entity.AlarmRecordEntity
import com.heartratemonitor.data.entity.HeartRateEntity
import com.heartratemonitor.data.entity.TimerSessionEntity
import com.heartratemonitor.data.pref.PreferencesManager
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for syncing local Room data to Cloudflare D1
 * 同时支持本地 JSON 文件备份
 */
@Singleton
class SyncRepository @Inject constructor(
    private val heartRateDao: HeartRateDao,
    private val timerSessionDao: TimerSessionDao,
    private val alarmRecordDao: AlarmRecordDao,
    private val syncApiClient: SyncApiClient,
    private val preferencesManager: PreferencesManager,
    private val localBackupManager: LocalBackupManager
) {

    /**
     * 同步到云端 + 本地备份
     * 两个操作独立执行，互不影响
     */
    suspend fun syncToCloud(): SyncResult {
        val lastSyncTime = preferencesManager.lastSyncTimeFlow.first()

        // 读取需要同步的数据
        val heartRates = heartRateDao.getAfter(lastSyncTime)
        val timerSessions = timerSessionDao.getAfter(lastSyncTime)
        val alarmRecords = alarmRecordDao.getAfterSync(lastSyncTime)

        if (heartRates.isEmpty() && timerSessions.isEmpty() && alarmRecords.isEmpty()) {
            // 即使没有增量数据，也创建一次完整本地备份
            createLocalBackup()
            return SyncResult(
                success = true,
                syncedHeartRates = 0,
                syncedTimerSessions = 0,
                syncedAlarmRecords = 0
            )
        }

        // 同步到云端
        val request = SyncRequest(
            heartRates = heartRates.map {
                HeartRateRecord(timestamp = it.timestamp, heartRate = it.heartRate)
            },
            timerSessions = timerSessions.map {
                TimerSessionRecord(timestamp = it.timestamp, durationSeconds = it.durationSeconds, tag = it.tag)
            },
            alarmRecords = alarmRecords.map {
                AlarmRecordRecord(
                    createdAt = it.createdAt,
                    targetTimeMillis = it.targetTimeMillis,
                    durationSeconds = it.durationSeconds,
                    label = it.label,
                    status = it.status,
                    completedAt = it.completedAt
                )
            }
        )

        val response = syncApiClient.syncData(request)

        // 创建本地备份（无论云端同步成功与否）
        createLocalBackup()

        if (response.success) {
            preferencesManager.saveLastSyncTime(System.currentTimeMillis())
        }

        return SyncResult(
            success = response.success,
            syncedHeartRates = response.syncedHeartRates,
            syncedTimerSessions = response.syncedTimerSessions,
            syncedAlarmRecords = response.syncedAlarmRecords,
            error = response.message
        )
    }

    /**
     * 恢复数据：优先从本地备份恢复，本地无备份则从云端恢复
     * 按 timestamp 去重合并，已有数据不会被覆盖
     */
    suspend fun restoreFromBackup(): RestoreResult {
        // 优先尝试本地备份
        if (localBackupManager.hasLocalBackup()) {
            Log.d(TAG, "Restoring from local backup")
            val data = localBackupManager.readLocalBackup()
            if (data != null) {
                return mergeAndInsert(data.heartRates, data.timerSessions, data.alarms)
            }
        }

        // 本地无备份，从云端恢复
        Log.d(TAG, "No local backup, restoring from cloud")
        return restoreFromCloud()
    }

    /**
     * 从云端恢复
     */
    private suspend fun restoreFromCloud(): RestoreResult {
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
        val tsEntities = response.timerSessions.map {
            TimerSessionEntity(timestamp = it.timestamp, durationSeconds = it.duration_seconds, tag = it.tag)
        }
        val alarmEntities = response.alarmRecords.map {
            AlarmRecordEntity(
                createdAt = it.created_at,
                targetTimeMillis = it.target_time_millis,
                durationSeconds = it.duration_seconds,
                label = it.label,
                status = it.status,
                completedAt = it.completed_at
            )
        }

        val result = mergeAndInsert(hrEntities, tsEntities, alarmEntities)

        // 云端恢复后，同时创建本地备份
        createLocalBackup()

        return result
    }

    /**
     * 按 timestamp 去重合并插入：只插入本地不存在的记录
     */
    private suspend fun mergeAndInsert(
        backupHeartRates: List<HeartRateEntity>,
        backupTimerSessions: List<TimerSessionEntity>,
        backupAlarmRecords: List<AlarmRecordEntity>
    ): RestoreResult {
        // 查询本地已有的 timestamp / created_at
        val existingHrTimestamps = heartRateDao.getAllTimestamps().toSet()
        val existingTsTimestamps = timerSessionDao.getAllTimestamps().toSet()
        val existingAlarmCreatedAts = alarmRecordDao.getAllTimestamps().toSet()

        // 过滤掉已存在的记录
        val newHeartRates = backupHeartRates.filter { it.timestamp !in existingHrTimestamps }
        val newTimerSessions = backupTimerSessions.filter { it.timestamp !in existingTsTimestamps }
        val newAlarmRecords = backupAlarmRecords.filter { it.createdAt !in existingAlarmCreatedAts }

        if (newHeartRates.isNotEmpty()) {
            heartRateDao.insertAll(newHeartRates)
        }
        if (newTimerSessions.isNotEmpty()) {
            timerSessionDao.insertAll(newTimerSessions)
        }
        if (newAlarmRecords.isNotEmpty()) {
            alarmRecordDao.insertAll(newAlarmRecords)
        }

        return RestoreResult(
            success = true,
            restoredHeartRates = newHeartRates.size,
            restoredTimerSessions = newTimerSessions.size,
            restoredAlarmRecords = newAlarmRecords.size
        )
    }

    /**
     * 检查是否有本地备份
     */
    fun hasLocalBackup(): Boolean = localBackupManager.hasLocalBackup()

    /**
     * 获取本地备份时间字符串
     */
    fun getLocalBackupTime(): String? = localBackupManager.getBackupTimeString()

    /**
     * 创建本地备份（读取全部数据写入 JSON）
     */
    private suspend fun createLocalBackup() {
        try {
            val allHeartRates = heartRateDao.getAllSync()
            val allTimerSessions = timerSessionDao.getAllSync()
            val allAlarmRecords = alarmRecordDao.getAllSync()
            localBackupManager.createBackup(allHeartRates, allTimerSessions, allAlarmRecords)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create local backup", e)
        }
    }

    /**
     * Delete records from cloud (D1).
     * timestamps target timer_sessions; alarmCreatedAts target alarm_records.
     */
    suspend fun deleteFromCloud(
        timestamps: List<Long> = emptyList(),
        alarmCreatedAts: List<Long> = emptyList()
    ): DeleteResponse {
        if (timestamps.isEmpty() && alarmCreatedAts.isEmpty()) {
            return DeleteResponse(success = true, deletedCount = 0)
        }
        return syncApiClient.deleteData(DeleteRequest(timestamps, alarmCreatedAts))
    }

    companion object {
        private const val TAG = "SyncRepository"
    }
}
