package com.aiu.nfcforge.nfc

import android.nfc.NdefMessage
import android.nfc.Tag
import android.nfc.FormatException
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import com.aiu.nfcforge.viewmodel.NdefWriteType
import java.io.IOException
import java.util.Locale

/**
 * NfcWriter
 *
 * Writes data to NFC tags supporting:
 * - NDEF (already formatted)
 * - NdefFormatable (can be formatted as NDEF)
 *
 * Handles required error cases:
 * - Read-only tags
 * - Unsupported tag types
 * - Write failures
 */
object NfcWriter {

    /**
     * Builds an [NdefMessage] from the UI write request.
     */
    fun buildNdefMessage(type: NdefWriteType, payload: String): NdefMessage = when (type) {
        NdefWriteType.Text -> NdefCodec.buildTextMessage(text = payload, locale = Locale.ENGLISH, useUtf8 = true)
        NdefWriteType.Url -> NdefCodec.buildUrlMessage(url = payload)
    }

    /**
     * Builds an [NdefMessage] from Base64 encoded raw bytes.
     * Used for "saved cards" that store entire NDEF messages.
     */
    fun buildNdefMessageFromBase64(base64: String): NdefMessage {
        val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
        return NdefMessage(bytes)
    }

    /**
     * Writes the provided NDEF [message] to the [tag].
     *
     * Uses:
     * - [Ndef] when available
     * - [NdefFormatable] to format new tags
     */
    @Throws(WriteFailure::class)
    fun write(tag: Tag, message: NdefMessage) {
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            writeWithNdef(ndef, message)
            return
        }

        val formatable = NdefFormatable.get(tag)
        if (formatable != null) {
            formatAndWrite(formatable, message)
            return
        }

        throw WriteFailure.UnsupportedTag()
    }

    private fun writeWithNdef(ndef: Ndef, message: NdefMessage) {
        try {
            ndef.connect()

            if (!ndef.isWritable) throw WriteFailure.ReadOnlyTag()

            val needed = message.toByteArray().size
            val max = ndef.maxSize
            if (max > 0 && needed > max) throw WriteFailure.TagTooSmall(maxSizeBytes = max, neededBytes = needed)

            ndef.writeNdefMessage(message)
        } catch (e: IOException) {
            throw WriteFailure.IoFailure(e)
        } catch (e: FormatException) {
            throw WriteFailure.FormatFailure(e)
        } finally {
            runCatching { ndef.close() }
        }
    }

    private fun formatAndWrite(formatable: NdefFormatable, message: NdefMessage) {
        try {
            formatable.connect()
            formatable.format(message)
        } catch (e: IOException) {
            throw WriteFailure.IoFailure(e)
        } catch (e: FormatException) {
            throw WriteFailure.FormatFailure(e)
        } finally {
            runCatching { formatable.close() }
        }
    }
}



