package com.heartratemonitor.data.repository

import com.heartratemonitor.data.dao.DateCountPair
import com.heartratemonitor.data.dao.TimerSessionDao
import com.heartratemonitor.data.entity.TimerSessionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimerSessionRepository @Inject constructor(
    private val timerSessionDao: TimerSessionDao
) {
    suspend fun saveSession(durationSeconds: Int) {
        timerSessionDao.insert(
            TimerSessionEntity(
                timestamp = System.currentTimeMillis(),
                durationSeconds = durationSeconds
            )
        )
    }

    fun getAllSessions(): Flow<List<TimerSessionEntity>> {
        return timerSessionDao.getAllSessions()
    }

    fun getCountByDate(): Flow<List<DateCountPair>> {
        return timerSessionDao.getCountByDate()
    }

    fun getCountByDateAfter(afterTimestamp: Long): Flow<List<DateCountPair>> {
        return timerSessionDao.getCountByDateAfter(afterTimestamp)
    }

    fun getSessionsAfter(afterTimestamp: Long): Flow<List<TimerSessionEntity>> {
        return timerSessionDao.getSessionsAfter(afterTimestamp)
    }

    suspend fun deleteAll() {
        timerSessionDao.deleteAll()
    }
}
