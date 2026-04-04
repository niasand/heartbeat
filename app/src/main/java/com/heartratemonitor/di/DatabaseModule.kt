package com.heartratemonitor.di

import android.content.Context
import com.heartratemonitor.data.dao.HeartRateDao
import com.heartratemonitor.data.dao.TimerSessionDao
import com.heartratemonitor.data.database.HeartRateDatabase
import com.heartratemonitor.data.repository.HeartRateRepository
import com.heartratemonitor.data.repository.TimerSessionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideHeartRateDatabase(@ApplicationContext context: Context): HeartRateDatabase {
        return HeartRateDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideHeartRateDao(database: HeartRateDatabase): HeartRateDao {
        return database.heartRateDao()
    }

    @Provides
    @Singleton
    fun provideHeartRateRepository(heartRateDao: HeartRateDao): HeartRateRepository {
        return HeartRateRepository(heartRateDao)
    }

    @Provides
    @Singleton
    fun provideTimerSessionDao(database: HeartRateDatabase): TimerSessionDao {
        return database.timerSessionDao()
    }

    @Provides
    @Singleton
    fun provideTimerSessionRepository(timerSessionDao: TimerSessionDao): TimerSessionRepository {
        return TimerSessionRepository(timerSessionDao)
    }
}
