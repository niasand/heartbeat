package com.heartratemonitor.data.pref

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * 用户设置管理器
 * 使用DataStore保存用户配置
 */
@Singleton
class PreferencesManager @Inject constructor(@ApplicationContext context: Context) {

    private val dataStore = context.dataStore
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var _cachedHighThreshold = DEFAULT_HIGH_THRESHOLD
    val cachedHighThreshold: Int
        get() = _cachedHighThreshold

    @Volatile
    private var _cachedLowThreshold = DEFAULT_LOW_THRESHOLD
    val cachedLowThreshold: Int
        get() = _cachedLowThreshold

    companion object {
        // 高心率阈值
        private val HIGH_THRESHOLD_KEY = intPreferencesKey("high_threshold")
        // 低心率阈值
        private val LOW_THRESHOLD_KEY = intPreferencesKey("low_threshold")
        // 主题颜色（以RGB格式存储）
        private val THEME_COLOR_KEY = stringPreferencesKey("theme_color")
        // 上次使用的设备地址
        private val LAST_DEVICE_ADDRESS_KEY = stringPreferencesKey("last_device_address")
        // 倒计时铃声 URI
        private val TIMER_SOUND_URI_KEY = stringPreferencesKey("timer_sound_uri")
        // 上次同步时间
        private val LAST_SYNC_TIME_KEY = longPreferencesKey("last_sync_time")

        // 默认值
        const val DEFAULT_HIGH_THRESHOLD = 180
        const val DEFAULT_LOW_THRESHOLD = 60
        const val DEFAULT_THEME_COLOR = "#FF6200EE"
    }

    /**
     * 获取高心率阈值
     */
    val highThresholdFlow: Flow<Int> = dataStore.data.map { preferences ->
        preferences[HIGH_THRESHOLD_KEY] ?: DEFAULT_HIGH_THRESHOLD
    }

    /**
     * 获取低心率阈值
     */
    val lowThresholdFlow: Flow<Int> = dataStore.data.map { preferences ->
        preferences[LOW_THRESHOLD_KEY] ?: DEFAULT_LOW_THRESHOLD
    }

    /**
     * 获取主题颜色
     */
    val themeColorFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[THEME_COLOR_KEY] ?: DEFAULT_THEME_COLOR
    }

    /**
     * 获取上次使用的设备地址
     */
    val lastDeviceAddressFlow: Flow<String?> = dataStore.data.map { preferences ->
        preferences[LAST_DEVICE_ADDRESS_KEY]
    }

    /**
     * 获取倒计时铃声 URI
     */
    val timerSoundUriFlow: Flow<String?> = dataStore.data.map { preferences ->
        preferences[TIMER_SOUND_URI_KEY]
    }

    /**
     * 获取上次同步时间
     */
    val lastSyncTimeFlow: Flow<Long> = dataStore.data.map { preferences ->
        preferences[LAST_SYNC_TIME_KEY] ?: 0L
    }

    init {
        scope.launch {
            highThresholdFlow.collect {
                _cachedHighThreshold = it
            }
        }
        scope.launch {
            lowThresholdFlow.collect {
                _cachedLowThreshold = it
            }
        }
    }

    /**
     * 保存高心率阈值
     */
    suspend fun saveHighThreshold(value: Int) {
        dataStore.edit { preferences ->
            preferences[HIGH_THRESHOLD_KEY] = value
        }
    }

    /**
     * 保存低心率阈值
     */
    suspend fun saveLowThreshold(value: Int) {
        dataStore.edit { preferences ->
            preferences[LOW_THRESHOLD_KEY] = value
        }
    }

    /**
     * 保存主题颜色
     */
    suspend fun saveThemeColor(value: String) {
        dataStore.edit { preferences ->
            preferences[THEME_COLOR_KEY] = value
        }
    }

    /**
     * 保存上次使用的设备地址
     */
    suspend fun saveLastDeviceAddress(value: String) {
        dataStore.edit { preferences ->
            preferences[LAST_DEVICE_ADDRESS_KEY] = value
        }
    }

    /**
     * 保存倒计时铃声 URI
     */
    suspend fun saveTimerSoundUri(value: String) {
        dataStore.edit { preferences ->
            preferences[TIMER_SOUND_URI_KEY] = value
        }
    }

    /**
     * 保存上次同步时间
     */
    suspend fun saveLastSyncTime(value: Long) {
        dataStore.edit { preferences ->
            preferences[LAST_SYNC_TIME_KEY] = value
        }
    }
}
