package com.aiu.nfcforge.nfc

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import androidx.core.content.IntentCompat

/**
 * NfcIntentParser
 *
 * Extracts NFC objects from an [Intent] without using deprecated APIs.
 * Foreground dispatch delivers intents containing [NfcAdapter.EXTRA_TAG].
 */
object NfcIntentParser {

    /**
     * Returns the NFC [Tag] embedded in the intent, or null if none exists.
     */
    fun extractTag(intent: Intent): Tag? =
        IntentCompat.getParcelableExtra(intent, NfcAdapter.EXTRA_TAG, Tag::class.java)
}



