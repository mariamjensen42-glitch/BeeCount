package com.cycling.beecount.data.local

import androidx.room.TypeConverter
import java.time.LocalDate

/**
 * Room 类型转换：LocalDate 以 ISO-8601 字符串存储（yyyy-MM-dd）
 */
class LocalDateConverter {

    @TypeConverter
    fun fromLocalDate(date: LocalDate): String = date.toString()

    @TypeConverter
    fun toLocalDate(value: String): LocalDate = LocalDate.parse(value)
}
