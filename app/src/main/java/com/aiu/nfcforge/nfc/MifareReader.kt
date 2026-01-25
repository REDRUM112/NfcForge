package com.aiu.nfcforge.nfc

import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import com.aiu.nfcforge.utils.HexUtils
import org.json.JSONObject

/**
 * MifareReader
 *
 * Reads data from various NFC card types:
 * - MifareClassic (Mifare 1K, 4K)
 * - NfcA (ISO 14443 Type A)
 * - NfcB (ISO 14443 Type B)
 * - NfcF (FeliCa)
 * - NfcV (ISO 15693)
 */
object MifareReader {

    /**
     * Card data structure for non-NDEF cards.
     */
    data class CardData(
        val cardType: String,
        val uid: String,
        val atqa: String? = null, // Answer To Request (Type A)
        val sak: String? = null, // Select Acknowledge (Type A)
        val atqb: String? = null, // Answer To Request (Type B)
        val appData: String? = null, // Application Data (Type B)
        val systemCode: String? = null, // System Code (FeliCa)
        val dsfId: String? = null, // Data Storage Format ID (FeliCa)
        val mifareType: String? = null, // Mifare Classic type
        val mifareSize: Int? = null, // Mifare Classic size in bytes
        val sectors: List<SectorData> = emptyList(),
        val blocks: List<BlockData> = emptyList(),
        val rawData: Map<String, String> = emptyMap(),
    ) {
        fun toJson(): JSONObject {
            val json = JSONObject()
            json.put("cardType", cardType)
            json.put("uid", uid)
            atqa?.let { json.put("atqa", it) }
            sak?.let { json.put("sak", it) }
            atqb?.let { json.put("atqb", it) }
            appData?.let { json.put("appData", it) }
            systemCode?.let { json.put("systemCode", it) }
            dsfId?.let { json.put("dsfId", it) }
            mifareType?.let { json.put("mifareType", it) }
            mifareSize?.let { json.put("mifareSize", it) }
            if (sectors.isNotEmpty()) {
                val sectorsArray = org.json.JSONArray()
                sectors.forEach { sector ->
                    val sectorJson = JSONObject()
                    sectorJson.put("sector", sector.sector)
                    sectorJson.put("data", sector.data)
                    sectorsArray.put(sectorJson)
                }
                json.put("sectors", sectorsArray)
            }
            if (blocks.isNotEmpty()) {
                val blocksArray = org.json.JSONArray()
                blocks.forEach { block ->
                    val blockJson = JSONObject()
                    blockJson.put("block", block.block)
                    blockJson.put("data", block.data)
                    blocksArray.put(blockJson)
                }
                json.put("blocks", blocksArray)
            }
            if (rawData.isNotEmpty()) {
                val rawJson = JSONObject()
                rawData.forEach { rawJson.put(it.key, it.value) }
                json.put("rawData", rawJson)
            }
            return json
        }
    }

    data class SectorData(
        val sector: Int,
        val data: String, // Hex string
    )

    data class BlockData(
        val block: Int,
        val data: String, // Hex string
    )

    /**
     * Attempts to read all available card data from various NFC technologies.
     */
    fun readCardData(tag: Tag): CardData? {
        // Try MifareClassic first
        val mifare = MifareClassic.get(tag)
        if (mifare != null) {
            return readMifareClassic(mifare, tag)
        }

        // Try NfcA
        val nfcA = NfcA.get(tag)
        if (nfcA != null) {
            return readNfcA(nfcA, tag)
        }

        // Try NfcB
        val nfcB = NfcB.get(tag)
        if (nfcB != null) {
            return readNfcB(nfcB, tag)
        }

        // Try NfcF (FeliCa)
        val nfcF = NfcF.get(tag)
        if (nfcF != null) {
            return readNfcF(nfcF, tag)
        }

        // Try NfcV (ISO 15693)
        val nfcV = NfcV.get(tag)
        if (nfcV != null) {
            return readNfcV(nfcV, tag)
        }

        return null
    }

    private fun readMifareClassic(mifare: MifareClassic, tag: Tag): CardData {
        val uid = HexUtils.toHex(tag.id ?: ByteArray(0))
        val type = when (mifare.type) {
            MifareClassic.TYPE_CLASSIC -> "Mifare Classic"
            MifareClassic.TYPE_PLUS -> "Mifare Plus"
            MifareClassic.TYPE_PRO -> "Mifare Pro"
            else -> "Mifare Unknown"
        }
        val size = mifare.size

        val sectors = mutableListOf<SectorData>()
        val blocks = mutableListOf<BlockData>()

        return try {
            mifare.connect()
            mifare.timeout = 5000

            // Try to read first few sectors (requires authentication, may fail)
            for (sector in 0 until minOf(mifare.sectorCount, 16)) {
                try {
                    val blockIndex = mifare.sectorToBlock(sector)
                    val block = mifare.readBlock(blockIndex)
                    sectors.add(SectorData(sector, HexUtils.toHex(block)))
                    blocks.add(BlockData(blockIndex, HexUtils.toHex(block)))
                } catch (e: Exception) {
                    // Authentication failed or read failed, skip
                    break
                }
            }
            CardData(
                cardType = type,
                uid = uid,
                mifareType = type,
                mifareSize = size,
                sectors = sectors,
                blocks = blocks,
            )
        } catch (e: Exception) {
            CardData(
                cardType = type,
                uid = uid,
                mifareType = type,
                mifareSize = size,
            )
        } finally {
            runCatching { mifare.close() }
        }
    }

    private fun readNfcA(nfcA: NfcA, tag: Tag): CardData {
        val uid = HexUtils.toHex(tag.id ?: ByteArray(0))
        val atqa = try {
            nfcA.connect()
            nfcA.timeout = 5000
            HexUtils.toHex(nfcA.atqa)
        } catch (e: Exception) {
            null
        } finally {
            runCatching { nfcA.close() }
        }

        val sak = try {
            nfcA.connect()
            HexUtils.toHex(byteArrayOf(nfcA.sak.toByte()))
        } catch (e: Exception) {
            null
        } finally {
            runCatching { nfcA.close() }
        }

        return CardData(
            cardType = "NFC-A (ISO 14443 Type A)",
            uid = uid,
            atqa = atqa,
            sak = sak,
        )
    }

    private fun readNfcB(nfcB: NfcB, tag: Tag): CardData {
        val uid = HexUtils.toHex(tag.id ?: ByteArray(0))
        // NfcB API doesn't expose atqb/appData directly in older Android versions
        // We can only read basic tag info
        return CardData(
            cardType = "NFC-B (ISO 14443 Type B)",
            uid = uid,
        )
    }

    private fun readNfcF(nfcF: NfcF, tag: Tag): CardData {
        val uid = HexUtils.toHex(tag.id ?: ByteArray(0))
        val systemCode = try {
            nfcF.connect()
            val systemCodeBytes = nfcF.systemCode
            HexUtils.toHex(systemCodeBytes)
        } catch (e: Exception) {
            null
        } finally {
            runCatching { nfcF.close() }
        }

        // dsfId is not available in all Android versions
        return CardData(
            cardType = "NFC-F (FeliCa)",
            uid = uid,
            systemCode = systemCode,
        )
    }

    private fun readNfcV(nfcV: NfcV, tag: Tag): CardData {
        val uid = HexUtils.toHex(tag.id ?: ByteArray(0))
        val rawData = mutableMapOf<String, String>()

        try {
            nfcV.connect()
            // NfcV has limited read capabilities without authentication
            // We can only read basic info
        } catch (e: Exception) {
            // Ignore
        } finally {
            runCatching { nfcV.close() }
        }

        return CardData(
            cardType = "NFC-V (ISO 15693)",
            uid = uid,
            rawData = rawData,
        )
    }
}

