package com.heartratemonitor.data.sync

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.heartratemonitor.data.entity.HeartRateEntity
import com.heartratemonitor.data.entity.TimerSessionEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地备份管理器
 * 将心率数据和计时记录序列化为 JSON 文件存储在 App 私有目录
 */
@Singleton
class LocalBackupManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()
    private val backupDir: File
        get() = File(context.filesDir, "backup").also { it.mkdirs() }

    private val heartRateBackupFile: File
        get() = File(backupDir, "heart_rates.json")

    private val timerSessionBackupFile: File
        get() = File(backupDir, "timer_sessions.json")

    private val backupMetaFile: File
        get() = File(backupDir, "meta.json")

    data class BackupMeta(val timestamp: Long = 0)

    /**
     * 本地恢复结果（包含实体数据，直接用于数据库插入）
     */
    data class LocalBackupData(
        val heartRates: List<HeartRateEntity>,
        val timerSessions: List<TimerSessionEntity>,
        val backupTime: Long
    )

    /**
     * 创建本地备份
     */
    suspend fun createBackup(
        heartRates: List<HeartRateEntity>,
        timerSessions: List<TimerSessionEntity>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            gson.toJson(heartRates).also { heartRateBackupFile.writeText(it) }
            gson.toJson(timerSessions).also { timerSessionBackupFile.writeText(it) }
            gson.toJson(BackupMeta(System.currentTimeMillis())).also { backupMetaFile.writeText(it) }
            Log.d(TAG, "Local backup created: ${heartRates.size} HR, ${timerSessions.size} sessions")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Local backup failed", e)
            false
        }
    }

    /**
     * 检查本地备份是否存在
     */
    fun hasLocalBackup(): Boolean {
        return heartRateBackupFile.exists() && timerSessionBackupFile.exists()
    }

    /**
     * 获取本地备份时间（格式化字符串）
     */
    fun getBackupTimeString(): String? {
        val time = getBackupTimestamp() ?: return null
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA)
        return sdf.format(java.util.Date(time))
    }

    /**
     * 获取本地备份时间戳
     */
    private fun getBackupTimestamp(): Long? {
        return try {
            if (backupMetaFile.exists()) {
                gson.fromJson(backupMetaFile.readText(), BackupMeta::class.java).timestamp
            } else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 从本地备份读取数据
     */
    suspend fun readLocalBackup(): LocalBackupData? = withContext(Dispatchers.IO) {
        try {
            if (!hasLocalBackup()) return@withContext null

            val hrType = object : TypeToken<List<HeartRateEntity>>() {}.type
            val tsType = object : TypeToken<List<TimerSessionEntity>>() {}.type

            val heartRates: List<HeartRateEntity> = gson.fromJson(heartRateBackupFile.readText(), hrType) ?: emptyList()
            val timerSessions: List<TimerSessionEntity> = gson.fromJson(timerSessionBackupFile.readText(), tsType) ?: emptyList()
            val backupTime = getBackupTimestamp() ?: 0L

            LocalBackupData(heartRates, timerSessions, backupTime)
        } catch (e: Exception) {
            Log.e(TAG, "Read local backup failed", e)
            null
        }
    }

    companion object {
        private const val TAG = "LocalBackupManager"
    }
}
