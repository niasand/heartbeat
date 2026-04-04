package com.heartratemonitor.data.database

import androidx.room.TypeConverter

/**
 * Room数据库类型转换器
 */
class Converters {
    @TypeConverter
    fun fromLongList(value: List<Long>?): String? {
        return value?.joinToString(",")
    }

    @TypeConverter
    fun toLongList(value: String?): List<Long>? {
        return value?.split(",")?.mapNotNull { it.toLongOrNull() }
    }
}
