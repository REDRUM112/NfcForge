package com.aiu.nfcforge.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * NfcDao
 *
 * Database access for:
 * - transaction history
 * - transaction records
 * - saved cards
 */
@Dao
interface NfcDao {

    // -----------------------------
    // Transactions + records
    // -----------------------------

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransaction(tx: NfcTransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRecords(records: List<NfcRecordEntity>)

    @Transaction
    suspend fun insertTransactionWithRecords(
        tx: NfcTransactionEntity,
        records: List<NfcRecordEntity>,
    ): Long {
        val txId = insertTransaction(tx)
        if (records.isNotEmpty()) {
            insertRecords(records.map { it.copy(transactionId = txId) })
        }
        return txId
    }

    @Query("SELECT * FROM nfc_transactions ORDER BY timestampMs DESC")
    fun observeTransactions(): Flow<List<NfcTransactionEntity>>

    @Transaction
    @Query("SELECT * FROM nfc_transactions WHERE id = :id")
    fun observeTransactionWithRecords(id: Long): Flow<TransactionWithRecords?>

    // -----------------------------
    // Saved cards
    // -----------------------------

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSavedCard(card: SavedCardEntity): Long

    @Query("SELECT * FROM saved_cards ORDER BY createdAtMs DESC")
    fun observeSavedCards(): Flow<List<SavedCardEntity>>

    @Delete
    suspend fun deleteSavedCard(card: SavedCardEntity)

    @Query("DELETE FROM nfc_transactions WHERE id = :id")
    suspend fun deleteTransaction(id: Long)

    @Query("DELETE FROM nfc_records WHERE transactionId = :transactionId")
    suspend fun deleteRecordsForTransaction(transactionId: Long)

    @Transaction
    suspend fun deleteTransactionWithRecords(id: Long) {
        deleteRecordsForTransaction(id)
        deleteTransaction(id)
    }
}


