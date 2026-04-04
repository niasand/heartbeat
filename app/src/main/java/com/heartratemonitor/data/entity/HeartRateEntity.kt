package com.heartratemonitor.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 心率数据实体
 * 用于Room数据库存储
 */
@Entity(tableName = "heart_rates")
data class HeartRateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "heart_rate")
    val heartRate: Int
)
