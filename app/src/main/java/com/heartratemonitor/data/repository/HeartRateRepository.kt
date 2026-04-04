package com.heartratemonitor.data.repository

import com.heartratemonitor.data.dao.HeartRateDao
import com.heartratemonitor.data.dao.HeartRateStats
import com.heartratemonitor.data.dao.DailyHeartRateStats
import com.heartratemonitor.data.entity.HeartRateEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 心率数据仓库
 */
@Singleton
class HeartRateRepository @Inject constructor(
    private val heartRateDao: HeartRateDao
) {
    /**
     * 保存心率数据
     */
    suspend fun saveHeartRate(heartRate: Int) {
        val entity = HeartRateEntity(
            timestamp = System.currentTimeMillis(),
            heartRate = heartRate
        )
        heartRateDao.insert(entity)
    }

    /**
     * 获取所有心率数据
     */
    fun getAllHeartRates(): Flow<List<HeartRateEntity>> {
        return heartRateDao.getAllHeartRates()
    }

    /**
     * 获取指定时间范围的心率数据
     */
    fun getHeartRatesBetween(startTime: Long, endTime: Long): Flow<List<HeartRateEntity>> {
        return heartRateDao.getHeartRatesBetween(startTime, endTime)
    }

    /**
     * 获取最近N条心率数据
     */
    fun getRecentHeartRates(limit: Int = 100): Flow<List<HeartRateEntity>> {
        return heartRateDao.getRecentHeartRates(limit)
    }

    /**
     * 获取心率统计数据
     */
    suspend fun getHeartRateStats(startTime: Long, endTime: Long): HeartRateStats {
        return heartRateDao.getHeartRateStats(startTime, endTime)
    }

    /**
     * 删除指定时间之前的数据
     */
    suspend fun deleteBefore(beforeTime: Long) {
        heartRateDao.deleteBefore(beforeTime)
    }

    /**
     * 获取数据总数
     */
    suspend fun getCount(): Int {
        return heartRateDao.getCount()
    }

    /**
     * 清空所有数据
     */
    suspend fun deleteAll() {
        heartRateDao.deleteAll()
    }

    /**
     * 获取过去N天的每日统计
     */
    suspend fun getDailyStats(sinceTimestamp: Long): List<DailyHeartRateStats> {
        return heartRateDao.getDailyStats(sinceTimestamp)
    }
}
