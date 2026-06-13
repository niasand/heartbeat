package com.heartratemonitor.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarm_records")
data class AlarmRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "target_time_millis")
    val targetTimeMillis: Long,

    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Int,

    @ColumnInfo(name = "label")
    val label: String,

    @ColumnInfo(name = "status")
    val status: String = AlarmRecordStatus.SCHEDULED,

    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null
)

object AlarmRecordStatus {
    const val SCHEDULED = "scheduled"
    const val FIRED = "fired"
    const val CANCELED = "canceled"
}
