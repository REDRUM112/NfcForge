package com.aiu.nfcforge.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * TransactionDirection
 *
 * Whether an interaction was a tag read or a write attempt.
 */
enum class TransactionDirection {
    READ,
    WRITE,
}

/**
 * SavedCardType
 *
 * A simplified record type we support for "saved NFC cards" and replay:
 * - TEXT: NFC Forum RTD_TEXT
 * - URL:  NFC Forum RTD_URI
 */
enum class SavedCardType {
    TEXT,
    URL,
    /**
     * Stores an entire raw NDEF message (all records) for replay.
     * This allows saving tags even when record types are not Text/URL.
     */
    NDEF_MESSAGE,
}

/**
 * NfcTransactionEntity
 *
 * Stored for every NFC interaction:
 * - reads
 * - write attempts (success/failure)
 */
@Entity(tableName = "nfc_transactions")
data class NfcTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestampMs: Long,
    val direction: TransactionDirection,
    val tagUidHex: String,
    val techListCsv: String,
    val ndefRecordCount: Int,
    /**
     * Raw NDEF message bytes (Base64) for READ transactions (if present).
     * This makes it possible to show full details later and to save/replay the whole message.
     */
    val ndefMessageBase64: String? = null,
    /**
     * EMV card data as JSON (for bank cards).
     * Contains all card details: PAN, expiry, service code, cardholder name, etc.
     */
    val emvCardJson: String? = null,
    // For writes, this stores what we attempted to write.
    val writeType: SavedCardType? = null,
    val writePayload: String? = null,
    /**
     * Raw bytes (Base64) of the NDEF message we attempted to write (if any).
     * For Text/URL we can reconstruct it, but storing it is useful for transparency/debugging.
     */
    val writeMessageBase64: String? = null,
    /**
     * A short, user-friendly summary shown in the History list.
     * For READ: first record text/url (truncated)
     * For WRITE: the payload (truncated)
     */
    val summary: String? = null,
    // Human-friendly status (e.g. "Read OK", "Write OK", "Write failed: read-only")
    val status: String,
)

/**
 * NfcRecordEntity
 *
 * One NDEF record associated with a transaction.
 * For reads: parsed records from the scanned tag.
 * For writes: the record(s) we attempted to write (if applicable).
 */
@Entity(
    tableName = "nfc_records",
    foreignKeys = [
        ForeignKey(
            entity = NfcTransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["transactionId"]),
    ],
)
data class NfcRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val transactionId: Long,
    val indexInMessage: Int,
    val kind: String,
    val tnf: Short,
    val typeDescription: String,
    val text: String? = null,
    val url: String? = null,
    // Preview used in list UI; full payload is stored separately.
    val payloadHexPreview: String,
    /**
     * Full record payload bytes (Base64) so we can show complete content later.
     * This matters for large JSON/text payloads and for non-text record types.
     */
    val payloadBase64: String,
)

/**
 * SavedCardEntity
 *
 * A "saved NFC card" is a reusable template for future writes.
 * It is typically created from a READ transaction ("save as card") but can be extended later.
 */
@Entity(tableName = "saved_cards")
data class SavedCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val label: String,
    val type: SavedCardType,
    /**
     * For TEXT/URL cards: the human-readable payload.
     * For NDEF_MESSAGE cards: may be null.
     */
    val payload: String? = null,
    /**
     * For NDEF_MESSAGE cards: raw NDEF bytes (Base64).
     * For TEXT/URL cards: may be null.
     */
    val messageBase64: String? = null,
    val createdAtMs: Long,
    val sourceTransactionId: Long? = null,
)


