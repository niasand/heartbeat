package com.heartratemonitor.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.heartratemonitor.data.dao.HeartRateDao
import com.heartratemonitor.data.dao.TimerSessionDao
import com.heartratemonitor.data.entity.HeartRateEntity
import com.heartratemonitor.data.entity.TimerSessionEntity

/**
 * 心率数据库
 */
@Database(
    entities = [HeartRateEntity::class, TimerSessionEntity::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class HeartRateDatabase : RoomDatabase() {

    abstract fun heartRateDao(): HeartRateDao
    abstract fun timerSessionDao(): TimerSessionDao

    companion object {
        @Volatile
        private var INSTANCE: HeartRateDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE timer_sessions ADD COLUMN tag TEXT DEFAULT NULL")
            }
        }

        fun getDatabase(context: Context): HeartRateDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HeartRateDatabase::class.java,
                    "heart_rate_database"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
