package com.heartratemonitor.di

import android.content.Context
import com.heartratemonitor.ble.BleConnectionManager
import com.heartratemonitor.ble.BleScanner
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideBleScanner(@ApplicationContext context: Context): BleScanner {
        return BleScanner(context)
    }

    @Provides
    @Singleton
    fun provideBleConnectionManager(@ApplicationContext context: Context): BleConnectionManager {
        return BleConnectionManager(context)
    }
}
