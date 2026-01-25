package com.aiu.nfcforge.nfc

import android.nfc.Tag
import android.nfc.tech.Ndef
import com.aiu.nfcforge.utils.HexUtils
import com.aiu.nfcforge.viewmodel.NdefMessageInfo
import com.aiu.nfcforge.viewmodel.TagInfo
import com.aiu.nfcforge.viewmodel.TagScanResult

/**
 * NfcReader
 *
 * Reads:
 * - Tag UID
 * - Tech list
 * - NDEF message (if available)
 * - EMV card data (if it's a bank card)
 *
 * Uses the required Android NFC tech class: [Ndef] and [IsoDep] for EMV cards.
 */
object NfcReader {

    /**
     * Detailed read result: includes parsed UI models + raw NDEF bytes (if present) + EMV card data (if bank card) + other card data + advanced card data.
     */
    data class ReadOutcome(
        val scan: TagScanResult,
        val ndefMessageBytes: ByteArray?,
        val emvCardData: EmvCardParser.EmvCardData? = null,
        val cardData: MifareReader.CardData? = null,
        val advancedCardData: AdvancedNfcReader.AdvancedCardData? = null,
    )

    /**
     * Reads a tag in a safe, display-friendly way.
     */
    fun read(tag: Tag): TagScanResult = readDetailed(tag).scan

    /**
     * Reads a tag and returns raw NDEF bytes when available, plus EMV card data for bank cards.
     */
    fun readDetailed(tag: Tag): ReadOutcome {
        val tagInfo = TagInfo(
            uidHex = HexUtils.toHex(tag.id ?: ByteArray(0)),
            techList = tag.techList.orEmpty().toList(),
        )

        val (ndefInfo, rawBytes) = readNdefIfPossible(tag)
        
        // Use advanced reader for comprehensive card reading
        val advancedData = AdvancedNfcReader.readAdvanced(tag)
        
        // Try to read EMV card data if NDEF isn't available or if it's a bank card
        val emvData = advancedData.emvData ?: if (rawBytes == null || tag.techList.contains("android.nfc.tech.IsoDep")) {
            EmvCardParser.readEmvCard(tag)
        } else {
            null
        }

        // Try to read other card types (MifareClassic, NfcA, NfcB, NfcF, NfcV) if NDEF and EMV didn't work
        val cardData = if (rawBytes == null && emvData == null) {
            MifareReader.readCardData(tag)
        } else {
            null
        }

        val scan = TagScanResult(
            tag = tagInfo,
            ndef = ndefInfo,
        )
        return ReadOutcome(scan = scan, ndefMessageBytes = rawBytes, emvCardData = emvData, cardData = cardData, advancedCardData = advancedData)
    }

    private fun readNdefIfPossible(tag: Tag): Pair<NdefMessageInfo, ByteArray?> {
        val ndef = Ndef.get(tag) ?: return NdefMessageInfo(records = emptyList()) to null

        try {
            ndef.connect()
            val message = ndef.ndefMessage ?: return NdefMessageInfo(records = emptyList()) to null
            return NdefCodec.parse(message) to message.toByteArray()
        } finally {
            runCatching { ndef.close() }
        }
    }
}



