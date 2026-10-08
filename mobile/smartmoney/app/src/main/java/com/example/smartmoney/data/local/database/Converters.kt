package com.example.smartmoney.data.local.database

import androidx.room.TypeConverter
import java.math.BigDecimal

/**
 * Room TypeConverters for storing complex types in SQLite.
 * Storing BigDecimal as a plain string avoids floating point precision loss.
 */
class Converters {

    @TypeConverter
    fun fromBigDecimal(value: BigDecimal?): String? {
        return value?.toPlainString()
    }

    @TypeConverter
    fun toBigDecimal(value: String?): BigDecimal? {
        return value?.let { BigDecimal(it) }
    }
}
