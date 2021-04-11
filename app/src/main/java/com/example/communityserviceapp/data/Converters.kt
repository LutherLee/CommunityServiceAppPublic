package com.example.communityserviceapp.data

import androidx.room.TypeConverter
import java.util.*

/**
 * Type converters to allow Room to reference complex data types.
 */
object Converters {
    @TypeConverter
    fun timestampToDate(value: Long?): Date? = value?.let { Date(it) }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? = date?.time
}
