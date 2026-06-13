package com.heartratemonitor.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.heartratemonitor.data.entity.AlarmRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alarm: AlarmRecordEntity): Long

    @Query("SELECT * FROM alarm_records WHERE status = 'scheduled' ORDER BY created_at DESC LIMIT 1")
    fun getActiveAlarm(): Flow<AlarmRecordEntity?>

    @Query("UPDATE alarm_records SET status = :status, completed_at = :completedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, completedAt: Long?)

    @Query("UPDATE alarm_records SET status = :status, completed_at = :completedAt WHERE status = 'scheduled'")
    suspend fun updateScheduledStatus(status: String, completedAt: Long?)

    @Query("DELETE FROM alarm_records WHERE created_at = :createdAt")
    suspend fun deleteByCreatedAt(createdAt: Long)

    @Query("SELECT * FROM alarm_records WHERE created_at >= :after ORDER BY created_at DESC")
    fun getAfter(after: Long): Flow<List<AlarmRecordEntity>>

    // ---- P2 sync helpers (suspend, snapshot reads) ----

    @Query("SELECT * FROM alarm_records")
    suspend fun getAllSync(): List<AlarmRecordEntity>

    @Query("SELECT created_at FROM alarm_records")
    suspend fun getAllTimestamps(): List<Long>

    @Query("SELECT * FROM alarm_records WHERE created_at > :after")
    suspend fun getAfterSync(after: Long): List<AlarmRecordEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(alarms: List<AlarmRecordEntity>)
}
