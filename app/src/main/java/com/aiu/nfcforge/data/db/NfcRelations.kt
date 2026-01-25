package com.aiu.nfcforge.data.db

import androidx.room.Embedded
import androidx.room.Relation

/**
 * TransactionWithRecords
 *
 * Room relation used to display a transaction and its NDEF record list.
 */
data class TransactionWithRecords(
    @Embedded val transaction: NfcTransactionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "transactionId",
    )
    val records: List<NfcRecordEntity>,
)


