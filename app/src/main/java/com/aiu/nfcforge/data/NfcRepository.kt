package com.aiu.nfcforge.data

import com.aiu.nfcforge.data.db.NfcDao
import com.aiu.nfcforge.data.db.NfcRecordEntity
import com.aiu.nfcforge.data.db.NfcTransactionEntity
import com.aiu.nfcforge.data.db.SavedCardEntity
import com.aiu.nfcforge.data.db.SavedCardType
import com.aiu.nfcforge.data.db.TransactionDirection
import com.aiu.nfcforge.data.db.TransactionWithRecords
import com.aiu.nfcforge.viewmodel.NdefMessageInfo
import com.aiu.nfcforge.viewmodel.NdefRecordInfo
import com.aiu.nfcforge.viewmodel.TagInfo
import kotlinx.coroutines.flow.Flow

/**
 * NfcRepository
 *
 * A small data layer wrapper around Room (NfcDao).
 * Keeps ViewModel free of SQL/Room knowledge and centralizes mapping logic.
 */
class NfcRepository(
    private val dao: NfcDao,
) {

    fun observeHistory(): Flow<List<NfcTransactionEntity>> = dao.observeTransactions()

    fun observeTransactionDetails(id: Long): Flow<TransactionWithRecords?> = dao.observeTransactionWithRecords(id)

    fun observeSavedCards(): Flow<List<SavedCardEntity>> = dao.observeSavedCards()

    suspend fun deleteSavedCard(card: SavedCardEntity) = dao.deleteSavedCard(card)

    suspend fun deleteTransaction(id: Long) = dao.deleteTransactionWithRecords(id)

    suspend fun saveReadTransaction(
        tag: TagInfo,
        ndef: NdefMessageInfo,
        ndefMessageBytes: ByteArray?,
        emvCardJson: String? = null,
    ): Long {
        val now = System.currentTimeMillis()
        val summary = buildSummaryFromNdef(ndef)

        // If we have raw bytes, parse them to extract the real record payload bytes for storage.
        val rawPayloads: List<ByteArray> = ndefMessageBytes?.let { bytes ->
            runCatching { android.nfc.NdefMessage(bytes) }
                .getOrNull()
                ?.records
                ?.map { it.payload }
        } ?: emptyList()

        val tx = NfcTransactionEntity(
            timestampMs = now,
            direction = TransactionDirection.READ,
            tagUidHex = tag.uidHex,
            techListCsv = tag.techList.joinToString(","),
            ndefRecordCount = ndef.records.size,
            ndefMessageBase64 = ndefMessageBytes?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) },
            emvCardJson = emvCardJson,
            summary = summary ?: (if (emvCardJson != null) "Bank Card" else null),
            status = "Read OK",
        )
        val records = ndef.records.mapIndexed { idx, r ->
            r.toEntity(
                transactionId = 0L,
                index = idx,
                payloadBytes = rawPayloads.getOrNull(idx),
            )
        }
        return dao.insertTransactionWithRecords(tx, records)
    }

    suspend fun saveWriteTransaction(
        tag: TagInfo,
        type: SavedCardType,
        payload: String,
        status: String,
        writtenMessage: NdefMessageInfo?,
        writtenMessageBytes: ByteArray?,
    ): Long {
        val now = System.currentTimeMillis()
        val summary = payload.take(120)

        val rawPayloads: List<ByteArray> = writtenMessageBytes?.let { bytes ->
            runCatching { android.nfc.NdefMessage(bytes) }
                .getOrNull()
                ?.records
                ?.map { it.payload }
        } ?: emptyList()

        val tx = NfcTransactionEntity(
            timestampMs = now,
            direction = TransactionDirection.WRITE,
            tagUidHex = tag.uidHex,
            techListCsv = tag.techList.joinToString(","),
            ndefRecordCount = writtenMessage?.records?.size ?: 0,
            writeType = type,
            writePayload = payload,
            writeMessageBase64 = writtenMessageBytes?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) },
            summary = summary,
            status = status,
        )
        val records = (writtenMessage?.records ?: emptyList()).mapIndexed { idx, r ->
            r.toEntity(
                transactionId = 0L,
                index = idx,
                payloadBytes = rawPayloads.getOrNull(idx),
            )
        }
        return dao.insertTransactionWithRecords(tx, records)
    }

    suspend fun saveCard(
        label: String,
        type: SavedCardType,
        payload: String?,
        messageBase64: String?,
        sourceTransactionId: Long?,
    ): Long {
        val card = SavedCardEntity(
            label = label,
            type = type,
            payload = payload,
            messageBase64 = messageBase64,
            createdAtMs = System.currentTimeMillis(),
            sourceTransactionId = sourceTransactionId,
        )
        return dao.insertSavedCard(card)
    }

    private fun NdefRecordInfo.toEntity(transactionId: Long, index: Int, payloadBytes: ByteArray?): NfcRecordEntity =
        NfcRecordEntity(
            transactionId = transactionId,
            indexInMessage = index,
            kind = kind,
            tnf = tnf,
            typeDescription = typeDescription,
            text = text,
            url = url,
            payloadHexPreview = payloadHexPreview,
            payloadBase64 = android.util.Base64.encodeToString(payloadBytes ?: ByteArray(0), android.util.Base64.NO_WRAP),
        )

    private fun buildSummaryFromNdef(ndef: NdefMessageInfo): String? {
        val first = ndef.records.firstOrNull() ?: return null
        val s = when {
            !first.text.isNullOrBlank() -> first.text
            !first.url.isNullOrBlank() -> first.url
            else -> null
        } ?: return null
        return s.replace("\n", " ").take(120)
    }
}


