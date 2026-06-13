package com.heartratemonitor.data.repository

import com.heartratemonitor.data.dao.AlarmRecordDao
import com.heartratemonitor.data.entity.AlarmRecordEntity
import com.heartratemonitor.data.entity.AlarmRecordStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmRecordRepository @Inject constructor(
    private val alarmRecordDao: AlarmRecordDao
) {
    fun getActiveAlarm(): Flow<AlarmRecordEntity?> {
        return alarmRecordDao.getActiveAlarm()
    }

    suspend fun createAlarm(label: String, targetTimeMillis: Long, durationSeconds: Int): Long {
        alarmRecordDao.updateScheduledStatus(AlarmRecordStatus.CANCELED, System.currentTimeMillis())
        return alarmRecordDao.insert(
            AlarmRecordEntity(
                createdAt = System.currentTimeMillis(),
                targetTimeMillis = targetTimeMillis,
                durationSeconds = durationSeconds,
                label = label.ifBlank { "智能闹钟" }
            )
        )
    }

    suspend fun markFired(id: Long) {
        alarmRecordDao.updateStatus(id, AlarmRecordStatus.FIRED, System.currentTimeMillis())
    }

    suspend fun markCanceled(id: Long) {
        alarmRecordDao.updateStatus(id, AlarmRecordStatus.CANCELED, System.currentTimeMillis())
    }
}
