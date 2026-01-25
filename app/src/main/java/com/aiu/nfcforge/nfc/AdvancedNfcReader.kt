package com.aiu.nfcforge.nfc

import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import android.nfc.tech.IsoDep
import com.aiu.nfcforge.utils.HexUtils
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException

/**
 * AdvancedNfcReader
 *
 * Comprehensive NFC reader that detects, classifies, and parses all supported
 * NFC tag and card technologies with security bypass attempts.
 */
object AdvancedNfcReader {

    /**
     * Comprehensive card data structure containing all parsed information.
     */
    data class AdvancedCardData(
        val uid: String,
        val techList: List<String>,
        val cardType: String,
        val cardFamily: String? = null,
        val memoryLayout: MemoryLayout? = null,
        val ndefRecords: List<NdefRecordData> = emptyList(),
        val mifareData: MifareData? = null,
        val emvData: EmvCardParser.EmvCardData? = null,
        val nfcVData: NfcVData? = null,
        val nfcFData: NfcFData? = null,
        val rawData: Map<String, String> = emptyMap(),
        val securityStatus: SecurityStatus = SecurityStatus.UNKNOWN,
    ) {
        fun toJson(): JSONObject {
            val json = JSONObject()
            json.put("uid", uid)
            json.put("techList", JSONArray(techList))
            json.put("cardType", cardType)
            cardFamily?.let { json.put("cardFamily", it) }
            memoryLayout?.let { json.put("memoryLayout", it.toJson()) }
            if (ndefRecords.isNotEmpty()) {
                val recordsArray = JSONArray()
                ndefRecords.forEach { recordsArray.put(it.toJson()) }
                json.put("ndefRecords", recordsArray)
            }
            mifareData?.let { json.put("mifareData", it.toJson()) }
            emvData?.let { json.put("emvData", it.toJson()) }
            nfcVData?.let { json.put("nfcVData", it.toJson()) }
            nfcFData?.let { json.put("nfcFData", it.toJson()) }
            if (rawData.isNotEmpty()) {
                val rawJson = JSONObject()
                rawData.forEach { rawJson.put(it.key, it.value) }
                json.put("rawData", rawJson)
            }
            json.put("securityStatus", securityStatus.name)
            return json
        }
    }

    data class MemoryLayout(
        val type: String, // "pages", "blocks", "sectors"
        val totalSize: Int,
        val unitSize: Int,
        val unitCount: Int,
    ) {
        fun toJson(): JSONObject {
            val json = JSONObject()
            json.put("type", type)
            json.put("totalSize", totalSize)
            json.put("unitSize", unitSize)
            json.put("unitCount", unitCount)
            return json
        }
    }

    data class NdefRecordData(
        val tnf: Int,
        val type: String,
        val id: String?,
        val payload: String,
        val payloadHex: String,
        val humanReadable: String?,
    ) {
        fun toJson(): JSONObject {
            val json = JSONObject()
            json.put("tnf", tnf)
            json.put("type", type)
            id?.let { json.put("id", it) }
            json.put("payload", payload)
            json.put("payloadHex", payloadHex)
            humanReadable?.let { json.put("humanReadable", it) }
            return json
        }
    }

    data class MifareData(
        val type: String,
        val size: Int,
        val sectors: List<SectorData> = emptyList(),
        val blocks: List<BlockData> = emptyList(),
        val pages: List<PageData> = emptyList(),
        val atqa: String? = null,
        val sak: String? = null,
    ) {
        fun toJson(): JSONObject {
            val json = JSONObject()
            json.put("type", type)
            json.put("size", size)
            if (sectors.isNotEmpty()) {
                val sectorsArray = JSONArray()
                sectors.forEach { sectorsArray.put(it.toJson()) }
                json.put("sectors", sectorsArray)
            }
            if (blocks.isNotEmpty()) {
                val blocksArray = JSONArray()
                blocks.forEach { blocksArray.put(it.toJson()) }
                json.put("blocks", blocksArray)
            }
            if (pages.isNotEmpty()) {
                val pagesArray = JSONArray()
                pages.forEach { pagesArray.put(it.toJson()) }
                json.put("pages", pagesArray)
            }
            atqa?.let { json.put("atqa", it) }
            sak?.let { json.put("sak", it) }
            return json
        }
    }

    data class SectorData(
        val sector: Int,
        val blocks: List<String>, // Hex strings
        val keyA: String? = null,
        val keyB: String? = null,
        val accessBits: String? = null,
    ) {
        fun toJson(): JSONObject {
            val json = JSONObject()
            json.put("sector", sector)
            json.put("blocks", JSONArray(blocks))
            keyA?.let { json.put("keyA", it) }
            keyB?.let { json.put("keyB", it) }
            accessBits?.let { json.put("accessBits", it) }
            return json
        }
    }

    data class BlockData(
        val block: Int,
        val data: String, // Hex string
    ) {
        fun toJson(): JSONObject {
            val json = JSONObject()
            json.put("block", block)
            json.put("data", data)
            return json
        }
    }

    data class PageData(
        val page: Int,
        val data: String, // Hex string
    ) {
        fun toJson(): JSONObject {
            val json = JSONObject()
            json.put("page", page)
            json.put("data", data)
            return json
        }
    }

    data class NfcVData(
        val dsfId: Byte? = null,
        val responseFlags: Byte? = null,
        val blocks: List<BlockData> = emptyList(),
    ) {
        fun toJson(): JSONObject {
            val json = JSONObject()
            dsfId?.let { json.put("dsfId", it.toInt() and 0xFF) }
            responseFlags?.let { json.put("responseFlags", it.toInt() and 0xFF) }
            if (blocks.isNotEmpty()) {
                val blocksArray = JSONArray()
                blocks.forEach { blocksArray.put(it.toJson()) }
                json.put("blocks", blocksArray)
            }
            return json
        }
    }

    data class NfcFData(
        val systemCode: String? = null,
        val serviceCode: String? = null,
        val blocks: List<BlockData> = emptyList(),
    ) {
        fun toJson(): JSONObject {
            val json = JSONObject()
            systemCode?.let { json.put("systemCode", it) }
            serviceCode?.let { json.put("serviceCode", it) }
            if (blocks.isNotEmpty()) {
                val blocksArray = JSONArray()
                blocks.forEach { blocksArray.put(it.toJson()) }
                json.put("blocks", blocksArray)
            }
            return json
        }
    }

    enum class SecurityStatus {
        UNKNOWN,
        UNPROTECTED,
        PROTECTED,
        BYPASSED,
        DECRYPTED,
    }

    /**
     * Main entry point: reads and classifies the tag comprehensively.
     */
    fun readAdvanced(tag: Tag): AdvancedCardData {
        val uid = HexUtils.toHex(tag.id ?: ByteArray(0))
        val techList = tag.techList.orEmpty().toList()

        // Try to identify card type
        val (cardType, cardFamily) = identifyCardType(tag, techList)
        
        // Read NDEF if available
        val ndefRecords = readNdefRecords(tag)
        
        // Read card-specific data
        val mifareData = readMifareData(tag)
        val emvData = EmvCardParser.readEmvCard(tag)
        val nfcVData = readNfcVData(tag)
        val nfcFData = readNfcFData(tag)
        
        // Determine memory layout
        val memoryLayout = determineMemoryLayout(tag, mifareData)
        
        // Determine security status
        val securityStatus = determineSecurityStatus(tag, mifareData, emvData)
        
        // Collect raw data
        val rawData = collectRawData(tag, techList)

        return AdvancedCardData(
            uid = uid,
            techList = techList,
            cardType = cardType,
            cardFamily = cardFamily,
            memoryLayout = memoryLayout,
            ndefRecords = ndefRecords,
            mifareData = mifareData,
            emvData = emvData,
            nfcVData = nfcVData,
            nfcFData = nfcFData,
            rawData = rawData,
            securityStatus = securityStatus,
        )
    }

    private fun identifyCardType(tag: Tag, techList: List<String>): Pair<String, String?> {
        // Check for MIFARE Classic
        val mifareClassic = MifareClassic.get(tag)
        if (mifareClassic != null) {
            val type = when (mifareClassic.type) {
                MifareClassic.TYPE_CLASSIC -> "MIFARE Classic"
                MifareClassic.TYPE_PLUS -> "MIFARE Plus"
                MifareClassic.TYPE_PRO -> "MIFARE Pro"
                else -> "MIFARE Unknown"
            }
            val size = if (mifareClassic.size == 1024) "1K" else "4K"
            return type to "MIFARE Classic $size"
        }

        // Check for MIFARE Ultralight
        val mifareUltralight = MifareUltralight.get(tag)
        if (mifareUltralight != null) {
            val type = when (mifareUltralight.type) {
                MifareUltralight.TYPE_ULTRALIGHT -> "MIFARE Ultralight"
                MifareUltralight.TYPE_ULTRALIGHT_C -> "MIFARE Ultralight C"
                else -> "MIFARE Ultralight Unknown"
            }
            return type to "MIFARE Ultralight"
        }

        // Check for NTAG
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            try {
                ndef.connect()
                val message = ndef.ndefMessage
                if (message != null) {
                    val ntagType = identifyNtagType(ndef)
                    return "NTAG" to ntagType
                }
            } catch (e: Exception) {
                // Ignore
            } finally {
                runCatching { ndef.close() }
            }
        }

        // Check for DESFire
        val isoDep = IsoDep.get(tag)
        if (isoDep != null && !techList.contains("android.nfc.tech.Ndef")) {
            // Could be DESFire or EMV
            val atr = isoDep.historicalBytes
            if (atr != null && atr.isNotEmpty()) {
                val atrHex = HexUtils.toHex(atr)
                // DESFire has specific ATR pattern
                if (atrHex.startsWith("80") || atrHex.contains("A000000396")) {
                    return "MIFARE DESFire" to "MIFARE DESFire"
                }
            }
        }

        // Check for EMV
        if (isoDep != null && techList.contains("android.nfc.tech.IsoDep")) {
            val emv = EmvCardParser.readEmvCard(tag)
            if (emv != null) {
                return "EMV Contactless Card" to "Bank Card"
            }
        }

        // Check for FeliCa
        val nfcF = NfcF.get(tag)
        if (nfcF != null) {
            return "FeliCa" to "FeliCa"
        }

        // Check for ISO 15693
        val nfcV = NfcV.get(tag)
        if (nfcV != null) {
            return "ISO 15693" to "Vicinity Card"
        }

        // Generic classification
        return when {
            techList.contains("android.nfc.tech.NfcA") -> "NFC-A (ISO 14443-3A)"
            techList.contains("android.nfc.tech.NfcB") -> "NFC-B (ISO 14443-3B)"
            techList.contains("android.nfc.tech.IsoDep") -> "ISO-DEP (ISO 14443-4)"
            else -> "Unknown NFC Tag"
        } to null
    }

    private fun identifyNtagType(ndef: Ndef): String {
        val maxSize = ndef.maxSize
        return when {
            maxSize <= 180 -> "NTAG213"
            maxSize <= 540 -> "NTAG215"
            maxSize <= 924 -> "NTAG216"
            else -> "NTAG (Unknown)"
        }
    }

    private fun readNdefRecords(tag: Tag): List<NdefRecordData> {
        val ndef = Ndef.get(tag) ?: return emptyList()
        return try {
            ndef.connect()
            val message = ndef.ndefMessage ?: return emptyList()
            message.records.map { record ->
                val payload = record.payload
                val payloadHex = HexUtils.toHex(payload)
                val humanReadable = try {
                    when (record.tnf) {
                        android.nfc.NdefRecord.TNF_WELL_KNOWN -> {
                            val recordType = String(record.type)
                            when {
                                recordType == android.nfc.NdefRecord.RTD_TEXT -> {
                                    if (payload.isNotEmpty()) {
                                        val textBytes = payload.drop(1).toByteArray()
                                        String(textBytes, Charsets.UTF_8)
                                    } else null
                                }
                                recordType == android.nfc.NdefRecord.RTD_URI -> {
                                    if (payload.isNotEmpty()) {
                                        val uriPrefix = payload[0].toInt()
                                        val uriBytes = payload.drop(1).toByteArray()
                                        val uriPrefixes = arrayOf("", "http://www.", "https://www.", "http://", "https://", "tel:", "mailto:", "ftp://anonymous:anonymous@", "ftp://ftp.", "ftps://", "sftp://", "smb://", "nfs://", "ftp://", "dav://", "news:", "telnet://", "imap:", "rtsp://", "urn:", "pop:", "sip:", "sips:", "tftp:", "btspp://", "btl2cap://", "btgoep://", "tcpobex://", "irdaobex://", "file://", "urn:epc:id:", "urn:epc:tag:", "urn:epc:pat:", "urn:epc:raw:", "urn:epc:", "urn:nfc:")
                                        if (uriPrefix < uriPrefixes.size) {
                                            uriPrefixes[uriPrefix] + String(uriBytes, Charsets.UTF_8)
                                        } else {
                                            String(uriBytes, Charsets.UTF_8)
                                        }
                                    } else null
                                }
                                else -> null
                            }
                        }
                        android.nfc.NdefRecord.TNF_MIME_MEDIA -> {
                            String(payload, Charsets.UTF_8)
                        }
                        android.nfc.NdefRecord.TNF_EXTERNAL_TYPE -> {
                            String(payload, Charsets.UTF_8)
                        }
                        else -> null
                    }
                } catch (e: Exception) {
                    null
                }
                NdefRecordData(
                    tnf = record.tnf.toInt(),
                    type = HexUtils.toHex(record.type),
                    id = if (record.id.isNotEmpty()) HexUtils.toHex(record.id) else null,
                    payload = try { String(payload, Charsets.UTF_8) } catch (e: Exception) { payloadHex },
                    payloadHex = payloadHex,
                    humanReadable = humanReadable,
                )
            }
        } catch (e: Exception) {
            emptyList()
        } finally {
            runCatching { ndef.close() }
        }
    }

    private fun readMifareData(tag: Tag): MifareData? {
        // Try MIFARE Classic
        val mifareClassic = MifareClassic.get(tag)
        if (mifareClassic != null) {
            return readMifareClassic(mifareClassic)
        }

        // Try MIFARE Ultralight
        val mifareUltralight = MifareUltralight.get(tag)
        if (mifareUltralight != null) {
            return readMifareUltralight(mifareUltralight)
        }

        return null
    }

    private fun readMifareClassic(mifare: MifareClassic): MifareData {
        val type = when (mifare.type) {
            MifareClassic.TYPE_CLASSIC -> "MIFARE Classic"
            MifareClassic.TYPE_PLUS -> "MIFARE Plus"
            MifareClassic.TYPE_PRO -> "MIFARE Pro"
            else -> "MIFARE Unknown"
        }
        val size = mifare.size

        val sectors = mutableListOf<SectorData>()
        val blocks = mutableListOf<BlockData>()

        return try {
            mifare.connect()
            mifare.timeout = 5000

            // Try default keys for authentication
            val defaultKeys = listOf(
                ByteArray(6) { 0xFF.toByte() }, // Default key
                ByteArray(6) { 0x00 }, // Null key
                byteArrayOf(0xA0.toByte(), 0xA1.toByte(), 0xA2.toByte(), 0xA3.toByte(), 0xA4.toByte(), 0xA5.toByte()),
                byteArrayOf(0xD3.toByte(), 0xF7.toByte(), 0xD3.toByte(), 0xF7.toByte(), 0xD3.toByte(), 0xF7.toByte()),
            )

            for (sector in 0 until minOf(mifare.sectorCount, 16)) {
                val sectorBlocks = mutableListOf<String>()
                var authenticated = false
                var keyA: String? = null
                var keyB: String? = null

                for (key in defaultKeys) {
                    try {
                        if (mifare.authenticateSectorWithKeyA(sector, key)) {
                            authenticated = true
                            keyA = HexUtils.toHex(key)
                            break
                        }
                    } catch (e: Exception) {
                        // Try next key
                    }
                }

                if (!authenticated) {
                    for (key in defaultKeys) {
                        try {
                            if (mifare.authenticateSectorWithKeyB(sector, key)) {
                                authenticated = true
                                keyB = HexUtils.toHex(key)
                                break
                            }
                        } catch (e: Exception) {
                            // Try next key
                        }
                    }
                }

                if (authenticated) {
                    val blockIndex = mifare.sectorToBlock(sector)
                    val blocksInSector = if (sector < 32) 4 else 16
                    for (i in 0 until blocksInSector) {
                        try {
                            val block = mifare.readBlock(blockIndex + i)
                            val blockHex = HexUtils.toHex(block)
                            sectorBlocks.add(blockHex)
                            blocks.add(BlockData(blockIndex + i, blockHex))
                        } catch (e: Exception) {
                            break
                        }
                    }
                }

                sectors.add(SectorData(
                    sector = sector,
                    blocks = sectorBlocks,
                    keyA = keyA,
                    keyB = keyB,
                ))
            }

            MifareData(
                type = type,
                size = size,
                sectors = sectors,
                blocks = blocks,
            )
        } catch (e: Exception) {
            MifareData(type = type, size = size)
        } finally {
            runCatching { mifare.close() }
        }
    }

    private fun readMifareUltralight(ultralight: MifareUltralight): MifareData {
        val type = when (ultralight.type) {
            MifareUltralight.TYPE_ULTRALIGHT -> "MIFARE Ultralight"
            MifareUltralight.TYPE_ULTRALIGHT_C -> "MIFARE Ultralight C"
            else -> "MIFARE Ultralight Unknown"
        }

        val pages = mutableListOf<PageData>()

        return try {
            ultralight.connect()
            ultralight.timeout = 5000

            // Read all pages (typically 16 pages for Ultralight, 48 for Ultralight C)
            val pageCount = if (ultralight.type == MifareUltralight.TYPE_ULTRALIGHT_C) 48 else 16
            for (page in 0 until pageCount) {
                try {
                    val pageData = ultralight.readPages(page)
                    pages.add(PageData(page, HexUtils.toHex(pageData)))
                } catch (e: Exception) {
                    break
                }
            }

            MifareData(
                type = type,
                size = pages.size * 4,
                pages = pages,
            )
        } catch (e: Exception) {
            MifareData(type = type, size = 0)
        } finally {
            runCatching { ultralight.close() }
        }
    }

    private fun readNfcVData(tag: Tag): NfcVData? {
        val nfcV = NfcV.get(tag) ?: return null
        val blocks = mutableListOf<BlockData>()

        return try {
            nfcV.connect()

            // Try to read first few blocks
            for (block in 0..15) {
                try {
                    val readCmd = byteArrayOf(
                        0x20, // Flags
                        0x23, // READ MULTIPLE BLOCKS
                        (block and 0xFF).toByte(),
                        0x00, // Number of blocks
                    )
                    val response = nfcV.transceive(readCmd)
                    if (response != null && response.size > 1) {
                        val data = response.drop(1).toByteArray()
                        blocks.add(BlockData(block, HexUtils.toHex(data)))
                    }
                } catch (e: Exception) {
                    break
                }
            }

            NfcVData(blocks = blocks)
        } catch (e: Exception) {
            NfcVData()
        } finally {
            runCatching { nfcV.close() }
        }
    }

    private fun readNfcFData(tag: Tag): NfcFData? {
        val nfcF = NfcF.get(tag) ?: return null
        val blocks = mutableListOf<BlockData>()

        return try {
            nfcF.connect()
            nfcF.timeout = 5000

            val systemCode = HexUtils.toHex(nfcF.systemCode)
            
            // Try to read blocks (requires service code)
            // This is simplified - real FeliCa reading needs proper service codes

            NfcFData(
                systemCode = systemCode,
                blocks = blocks,
            )
        } catch (e: Exception) {
            NfcFData()
        } finally {
            runCatching { nfcF.close() }
        }
    }

    private fun determineMemoryLayout(tag: Tag, mifareData: MifareData?): MemoryLayout? {
        mifareData?.let {
            return when {
                it.sectors.isNotEmpty() -> MemoryLayout("sectors", it.size, 16, it.sectors.size)
                it.blocks.isNotEmpty() -> MemoryLayout("blocks", it.size, 16, it.blocks.size)
                it.pages.isNotEmpty() -> MemoryLayout("pages", it.size, 4, it.pages.size)
                else -> null
            }
        }
        return null
    }

    private fun determineSecurityStatus(
        tag: Tag,
        mifareData: MifareData?,
        emvData: EmvCardParser.EmvCardData?,
    ): SecurityStatus {
        mifareData?.let {
            if (it.sectors.any { sector -> sector.keyA != null || sector.keyB != null }) {
                return SecurityStatus.BYPASSED
            }
            if (it.sectors.isNotEmpty() && it.sectors.all { sector -> sector.blocks.isEmpty() }) {
                return SecurityStatus.PROTECTED
            }
            return SecurityStatus.UNPROTECTED
        }
        if (emvData != null) {
            return SecurityStatus.PROTECTED
        }
        return SecurityStatus.UNKNOWN
    }

    private fun collectRawData(tag: Tag, techList: List<String>): Map<String, String> {
        val rawData = mutableMapOf<String, String>()

        // Get ATQA/SAK for NfcA
        val nfcA = NfcA.get(tag)
        if (nfcA != null) {
            try {
                nfcA.connect()
                rawData["atqa"] = HexUtils.toHex(nfcA.atqa)
                rawData["sak"] = HexUtils.toHex(byteArrayOf(nfcA.sak.toByte()))
            } catch (e: Exception) {
                // Ignore
            } finally {
                runCatching { nfcA.close() }
            }
        }

        // Get ATQB for NfcB
        val nfcB = NfcB.get(tag)
        if (nfcB != null) {
            try {
                nfcB.connect()
                // NfcB.atqb and appData are not directly accessible in all Android versions
                // We can only read basic info
            } catch (e: Exception) {
                // Ignore
            } finally {
                runCatching { nfcB.close() }
            }
        }

        // Get ATR for IsoDep
        val isoDep = IsoDep.get(tag)
        if (isoDep != null) {
            try {
                isoDep.connect()
                val atr = isoDep.historicalBytes
                if (atr != null && atr.isNotEmpty()) {
                    rawData["atr"] = HexUtils.toHex(atr)
                }
            } catch (e: Exception) {
                // Ignore
            } finally {
                runCatching { isoDep.close() }
            }
        }

        return rawData
    }
}

