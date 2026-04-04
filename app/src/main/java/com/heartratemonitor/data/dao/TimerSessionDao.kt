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

    @Query("SELECT * FROM timer_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<TimerSessionEntity>>

    @Query("""
        SELECT DATE(timestamp / 1000, 'unixepoch', 'localtime') as date, COUNT(*) as count
        FROM timer_sessions
        GROUP BY date
        ORDER BY date
    """)
    fun getCountByDate(): Flow<List<DateCountPair>>

    @Query("DELETE FROM timer_sessions")
    suspend fun deleteAll()
}

data class DateCountPair(
    val date: String,
    val count: Int
)
