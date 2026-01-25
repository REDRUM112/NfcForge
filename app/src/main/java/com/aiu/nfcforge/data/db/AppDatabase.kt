package com.aiu.nfcforge.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * AppDatabase
 *
 * Room database used for:
 * - NFC transaction history
 * - Saved NFC cards (templates)
 */
@Database(
    entities = [
        NfcTransactionEntity::class,
        NfcRecordEntity::class,
        SavedCardEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
@TypeConverters(NfcTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun nfcDao(): NfcDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "nfcforge.db",
                )
                    // This sample app evolves its schema frequently. For simplicity we reset DB on changes.
                    // If you need to preserve user data, add proper Room migrations here.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}


