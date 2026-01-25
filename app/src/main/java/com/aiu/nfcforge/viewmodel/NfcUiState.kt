package com.aiu.nfcforge.viewmodel

/**
 * NfcUiState
 *
 * Single source of truth for the UI (MVVM).
 * The UI reads this state via StateFlow and renders screens accordingly.
 */
data class NfcUiState(
    val isNfcAvailable: Boolean = false,
    val isNfcEnabled: Boolean = false,
    val lastResult: TagScanResult? = null,
    val isWriteArmed: Boolean = false,
    val armedWriteType: NdefWriteType? = null,
    val armedWritePayload: String? = null,
    /**
     * When set, the next tag tap writes the raw NDEF message exactly as stored (Base64).
     * This is used for "saved cards" and "save whole tag as card".
     */
    val armedWriteMessageBase64: String? = null,
    val lastWriteStatus: String? = null,
    val isContinuousReplay: Boolean = false,
) {
    /**
     * The scan indicator is considered "ready" when NFC exists + is enabled.
     */
    val isReadyToScan: Boolean
        get() = isNfcAvailable && isNfcEnabled

    /**
     * Primary text shown on the home scan card.
     */
    val scanHeadline: String
        get() = when {
            !isNfcAvailable -> "NFC not available"
            !isNfcEnabled -> "Enable NFC to scan"
            isWriteArmed -> "Write armed"
            else -> "Ready to scan"
        }

    /**
     * Supporting text shown on the home scan card.
     */
    val scanSubtext: String
        get() = when {
            !isNfcAvailable -> "This device does not support NFC."
            !isNfcEnabled -> "Turn on NFC in system settings, then tap a tag."
            isWriteArmed -> "Tap a tag to write your prepared NDEF record."
            else -> "Tap an NFC tag to read UID, tech list, and NDEF payload."
        }
}

/**
 * User-selected type for writing an NDEF record.
 */
enum class NdefWriteType {
    Text,
    Url,
}

/**
 * Parsed NFC tag info for display.
 */
data class TagInfo(
    val uidHex: String,
    val techList: List<String>,
)

/**
 * One parsed NDEF record (we focus on Text + URL per requirements).
 */
data class NdefRecordInfo(
    val kind: String,
    val tnf: Short,
    val typeDescription: String,
    val text: String? = null,
    val url: String? = null,
    val payloadHexPreview: String,
)

/**
 * Parsed NDEF message.
 */
data class NdefMessageInfo(
    val records: List<NdefRecordInfo>,
)

/**
 * Result of scanning a tag:
 * - Always contains Tag UID + tech list.
 * - May include an NDEF message (or empty records if none).
 */
data class TagScanResult(
    val tag: TagInfo,
    val ndef: NdefMessageInfo,
) {
    /**
     * Compact NDEF summary for the Home screen.
     */
    val ndefSummary: String
        get() = when {
            ndef.records.isEmpty() -> "None"
            ndef.records.size == 1 -> ndef.records.first().kind
            else -> "${ndef.records.size} records"
        }
}



