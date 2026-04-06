package com.heartratemonitor.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.heartratemonitor.data.entity.TimerSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TimerSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: TimerSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<TimerSessionEntity>)

    @Query("SELECT * FROM timer_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<TimerSessionEntity>>

    @Query("""
        SELECT DATE(timestamp / 1000, 'unixepoch', 'localtime') as date, COUNT(*) as count
        FROM timer_sessions
        GROUP BY date
        ORDER BY date
    """)
    fun getCountByDate(): Flow<List<DateCountPair>>

    @Query("""
        SELECT DATE(timestamp / 1000, 'unixepoch', 'localtime') as date, COUNT(*) as count
        FROM timer_sessions
        WHERE timestamp > :afterTimestamp
        GROUP BY date
        ORDER BY date
    """)
    fun getCountByDateAfter(afterTimestamp: Long): Flow<List<DateCountPair>>

    @Query("""
        SELECT * FROM timer_sessions
        WHERE timestamp > :afterTimestamp
        ORDER BY timestamp DESC
    """)
    fun getSessionsAfter(afterTimestamp: Long): Flow<List<TimerSessionEntity>>

    @Query("DELETE FROM timer_sessions WHERE timestamp = :timestamp")
    suspend fun deleteByTimestamp(timestamp: Long)

    @Query("DELETE FROM timer_sessions")
    suspend fun deleteAll()

    /**
     * 获取所有计时记录（同步，用于备份）
     */
    @Query("SELECT * FROM timer_sessions ORDER BY timestamp ASC")
    suspend fun getAllSync(): List<TimerSessionEntity>

    /**
     * 获取所有已存在的计时记录时间戳（用于恢复去重）
     */
    @Query("SELECT timestamp FROM timer_sessions")
    suspend fun getAllTimestamps(): List<Long>

    @Query("SELECT * FROM timer_sessions WHERE timestamp > :afterTimestamp ORDER BY timestamp ASC")
    suspend fun getAfter(afterTimestamp: Long): List<TimerSessionEntity>
}

data class DateCountPair(
    val date: String,
    val count: Int
)
