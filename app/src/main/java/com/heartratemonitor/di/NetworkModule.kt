package com.heartratemonitor.di

import com.heartratemonitor.data.sync.SyncApiClient
import com.heartratemonitor.data.sync.SyncRepository
import com.heartratemonitor.data.dao.HeartRateDao
import com.heartratemonitor.data.dao.TimerSessionDao
import com.heartratemonitor.data.pref.PreferencesManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideSyncApiClient(): SyncApiClient {
        return SyncApiClient()
    }

    @Provides
    @Singleton
    fun provideSyncRepository(
        heartRateDao: HeartRateDao,
        timerSessionDao: TimerSessionDao,
        syncApiClient: SyncApiClient,
        preferencesManager: PreferencesManager
    ): SyncRepository {
        return SyncRepository(heartRateDao, timerSessionDao, syncApiClient, preferencesManager)
    }
}
