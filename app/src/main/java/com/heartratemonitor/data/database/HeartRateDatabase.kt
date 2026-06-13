package com.heartratemonitor.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.heartratemonitor.data.dao.AlarmRecordDao
import com.heartratemonitor.data.dao.HeartRateDao
import com.heartratemonitor.data.dao.TimerSessionDao
import com.heartratemonitor.data.entity.AlarmRecordEntity
import com.heartratemonitor.data.entity.HeartRateEntity
import com.heartratemonitor.data.entity.TimerSessionEntity

/**
 * 心率数据库
 */
@Database(
    entities = [HeartRateEntity::class, TimerSessionEntity::class, AlarmRecordEntity::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class HeartRateDatabase : RoomDatabase() {

    abstract fun heartRateDao(): HeartRateDao
    abstract fun timerSessionDao(): TimerSessionDao
    abstract fun alarmRecordDao(): AlarmRecordDao

    companion object {
        @Volatile
        private var INSTANCE: HeartRateDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE timer_sessions ADD COLUMN tag TEXT DEFAULT NULL")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `alarm_records` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `target_time_millis` INTEGER NOT NULL,
                        `duration_seconds` INTEGER NOT NULL,
                        `label` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `completed_at` INTEGER
                    )
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): HeartRateDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HeartRateDatabase::class.java,
                    "heart_rate_database"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
