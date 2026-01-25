package com.aiu.nfcforge.data.db

import androidx.room.TypeConverter

/**
 * Room type converters for enums.
 *
 * We store enums as strings to keep the DB human-readable and stable.
 */
class NfcTypeConverters {

    @TypeConverter
    fun directionToString(value: TransactionDirection?): String? = value?.name

    @TypeConverter
    fun stringToDirection(value: String?): TransactionDirection? =
        value?.let { TransactionDirection.valueOf(it) }

    @TypeConverter
    fun cardTypeToString(value: SavedCardType?): String? = value?.name

    @TypeConverter
    fun stringToCardType(value: String?): SavedCardType? =
        value?.let { SavedCardType.valueOf(it) }
}


