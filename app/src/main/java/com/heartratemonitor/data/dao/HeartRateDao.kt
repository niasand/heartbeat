package com.heartratemonitor.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.heartratemonitor.data.entity.HeartRateEntity
import kotlinx.coroutines.flow.Flow

/**
 * 心率数据访问对象
 */
@Dao
interface HeartRateDao {

    /**
     * 插入心率数据
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(heartRate: HeartRateEntity)

    /**
     * 批量插入心率数据
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(heartRates: List<HeartRateEntity>)

    /**
     * 获取所有心率数据，按时间降序
     */
    @Query("SELECT * FROM heart_rates ORDER BY timestamp DESC")
    fun getAllHeartRates(): Flow<List<HeartRateEntity>>

    /**
     * 获取指定时间范围内的心率数据，按时间降序
     */
    @Query("SELECT * FROM heart_rates WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    fun getHeartRatesBetween(startTime: Long, endTime: Long): Flow<List<HeartRateEntity>>

    /**
     * 获取最近N条心率数据
     */
    @Query("SELECT * FROM heart_rates ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentHeartRates(limit: Int): Flow<List<HeartRateEntity>>

    /**
     * 获取心率统计数据
     */
    @Query("""
        SELECT
            AVG(heart_rate) as avgHeartRate,
            MAX(heart_rate) as maxHeartRate,
            MIN(heart_rate) as minHeartRate,
            COUNT(*) as count
        FROM heart_rates
        WHERE timestamp >= :startTime AND timestamp <= :endTime
    """)
    suspend fun getHeartRateStats(startTime: Long, endTime: Long): HeartRateStats

    /**
     * 删除指定时间范围之前的数据
     */
    @Query("DELETE FROM heart_rates WHERE timestamp < :beforeTime")
    suspend fun deleteBefore(beforeTime: Long)

    /**
     * 获取数据总数
     */
    @Query("SELECT COUNT(*) FROM heart_rates")
    suspend fun getCount(): Int

    /**
     * 清空所有数据
     */
    @Query("DELETE FROM heart_rates")
    suspend fun deleteAll()
}

/**
 * 心率统计数据
 */
data class HeartRateStats(
    val avgHeartRate: Double?,
    val maxHeartRate: Int?,
    val minHeartRate: Int?,
    val count: Int
)
