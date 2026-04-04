package com.heartratemonitor.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.heartratemonitor.data.dao.HeartRateDao
import com.heartratemonitor.data.dao.TimerSessionDao
import com.heartratemonitor.data.entity.HeartRateEntity
import com.heartratemonitor.data.entity.TimerSessionEntity

/**
 * 心率数据库
 */
@Database(
    entities = [HeartRateEntity::class, TimerSessionEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class HeartRateDatabase : RoomDatabase() {

    abstract fun heartRateDao(): HeartRateDao
    abstract fun timerSessionDao(): TimerSessionDao

    companion object {
        @Volatile
        private var INSTANCE: HeartRateDatabase? = null

        fun getDatabase(context: Context): HeartRateDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HeartRateDatabase::class.java,
                    "heart_rate_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
