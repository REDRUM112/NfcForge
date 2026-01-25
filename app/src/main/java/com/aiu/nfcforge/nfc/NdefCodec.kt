package com.aiu.nfcforge.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import com.aiu.nfcforge.utils.HexUtils
import com.aiu.nfcforge.viewmodel.NdefMessageInfo
import com.aiu.nfcforge.viewmodel.NdefRecordInfo
import java.nio.charset.Charset
import java.util.Locale

/**
 * NdefCodec
 *
 * - Parses NDEF messages into display-friendly models.
 * - Builds NDEF messages for Text and URL records.
 *
 * Focused on required record types:
 * - NFC Forum "Text Record Type Definition" (RTD_TEXT)
 * - NFC Forum "URI Record Type Definition" (RTD_URI)
 */
object NdefCodec {

    /**
     * Parse a raw [NdefMessage] into a [NdefMessageInfo] for UI rendering.
     */
    fun parse(message: NdefMessage): NdefMessageInfo {
        val records = message.records.orEmpty().map { record -> parseRecord(record) }
        return NdefMessageInfo(records = records)
    }

    private fun parseRecord(record: NdefRecord): NdefRecordInfo {
        val typeDesc = runCatching { String(record.type, Charsets.US_ASCII) }.getOrDefault(record.type.contentToString())
        val payloadPreview = HexUtils.toHexPreview(record.payload)

        // Text record
        if (record.tnf == NdefRecord.TNF_WELL_KNOWN.toShort() && record.type.contentEquals(NdefRecord.RTD_TEXT)) {
            val text = parseTextPayload(record.payload)
            return NdefRecordInfo(
                kind = "Text",
                tnf = record.tnf,
                typeDescription = typeDesc,
                text = text,
                payloadHexPreview = payloadPreview,
            )
        }

        // URI record
        if (record.tnf == NdefRecord.TNF_WELL_KNOWN.toShort() && record.type.contentEquals(NdefRecord.RTD_URI)) {
            val url = parseUriPayload(record.payload)
            return NdefRecordInfo(
                kind = "URL",
                tnf = record.tnf,
                typeDescription = typeDesc,
                url = url,
                payloadHexPreview = payloadPreview,
            )
        }

        // Fallback
        return NdefRecordInfo(
            kind = "Unknown",
            tnf = record.tnf,
            typeDescription = typeDesc,
            payloadHexPreview = payloadPreview,
        )
    }

    /**
     * NFC Forum Text RTD payload format:
     * - [0] status byte: b7 indicates encoding (0=UTF-8, 1=UTF-16), b5..b0 language code length
     * - [1..] language code bytes
     * - remaining bytes are the actual text
     */
    private fun parseTextPayload(payload: ByteArray): String {
        if (payload.isEmpty()) return ""
        val status = payload[0].toInt()
        val isUtf16 = (status and 0x80) != 0
        val langLen = status and 0x3F
        val textStart = 1 + langLen
        if (textStart > payload.size) return ""
        val textBytes = payload.copyOfRange(textStart, payload.size)
        val charset = if (isUtf16) Charsets.UTF_16 else Charsets.UTF_8
        return runCatching { String(textBytes, charset) }.getOrDefault("")
    }

    /**
     * NFC Forum URI RTD payload format:
     * - [0] prefix index (0 = no prefix, 1 = "http://www.", 2 = "https://www.", ...)
     * - remaining bytes are the rest of the URI
     */
    private fun parseUriPayload(payload: ByteArray): String {
        if (payload.isEmpty()) return ""
        val prefix = uriPrefix(payload[0].toInt() and 0xFF)
        val rest = runCatching { String(payload.copyOfRange(1, payload.size), Charsets.UTF_8) }.getOrDefault("")
        return prefix + rest
    }

    // Minimal NFC Forum URI prefix map (covers common prefixes).
    private fun uriPrefix(code: Int): String = when (code) {
        0x00 -> ""
        0x01 -> "http://www."
        0x02 -> "https://www."
        0x03 -> "http://"
        0x04 -> "https://"
        0x05 -> "tel:"
        0x06 -> "mailto:"
        0x07 -> "ftp://anonymous:anonymous@"
        0x08 -> "ftp://ftp."
        0x09 -> "ftps://"
        0x0A -> "sftp://"
        0x0B -> "smb://"
        0x0C -> "nfs://"
        0x0D -> "ftp://"
        0x0E -> "dav://"
        0x0F -> "news:"
        0x10 -> "telnet://"
        0x11 -> "imap:"
        0x12 -> "rtsp://"
        0x13 -> "urn:"
        0x14 -> "pop:"
        0x15 -> "sip:"
        0x16 -> "sips:"
        0x17 -> "tftp:"
        0x18 -> "btspp://"
        0x19 -> "btl2cap://"
        0x1A -> "btgoep://"
        0x1B -> "tcpobex://"
        0x1C -> "irdaobex://"
        0x1D -> "file://"
        0x1E -> "urn:epc:id:"
        0x1F -> "urn:epc:tag:"
        0x20 -> "urn:epc:pat:"
        0x21 -> "urn:epc:raw:"
        0x22 -> "urn:epc:"
        0x23 -> "urn:nfc:"
        else -> ""
    }

    /**
     * Builds a single-record NDEF message containing a Text record.
     *
     * We create the record manually (no deprecated APIs) using NFC Forum RTD_TEXT encoding.
     */
    fun buildTextMessage(text: String, locale: Locale = Locale.ENGLISH, useUtf8: Boolean = true): NdefMessage {
        val langBytes = locale.language.toByteArray(Charsets.US_ASCII)
        val textBytes = text.toByteArray(if (useUtf8) Charsets.UTF_8 else Charsets.UTF_16)
        val status = ((if (useUtf8) 0 else 0x80) or (langBytes.size and 0x3F)).toByte()

        val payload = ByteArray(1 + langBytes.size + textBytes.size)
        payload[0] = status
        System.arraycopy(langBytes, 0, payload, 1, langBytes.size)
        System.arraycopy(textBytes, 0, payload, 1 + langBytes.size, textBytes.size)

        val record = NdefRecord(
            NdefRecord.TNF_WELL_KNOWN,
            NdefRecord.RTD_TEXT,
            ByteArray(0),
            payload,
        )
        return NdefMessage(arrayOf(record))
    }

    /**
     * Builds a single-record NDEF message containing a URL record.
     */
    fun buildUrlMessage(url: String): NdefMessage {
        val record = NdefRecord.createUri(url)
        return NdefMessage(arrayOf(record))
    }
}



