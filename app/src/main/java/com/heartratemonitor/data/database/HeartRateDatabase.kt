package com.heartratemonitor.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.heartratemonitor.data.dao.HeartRateDao
import com.heartratemonitor.data.entity.HeartRateEntity

/**
 * 心率数据库
 */
@Database(
    entities = [HeartRateEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class HeartRateDatabase : RoomDatabase() {

    abstract fun heartRateDao(): HeartRateDao

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
