package com.aiu.nfcforge.nfc

import android.nfc.Tag
import android.nfc.tech.IsoDep
import com.aiu.nfcforge.utils.HexUtils
import org.json.JSONObject
import java.io.IOException

/**
 * EmvCardParser
 *
 * Parses EMV (bank card) data from NFC tags using ISO-DEP technology.
 * Extracts card details like PAN, expiry, service code, cardholder name, etc.
 */
object EmvCardParser {

    /**
     * Parsed EMV card data as JSON-serializable structure.
     */
    data class EmvCardData(
        val pan: String? = null, // Primary Account Number (raw hex)
        val panFormatted: String? = null, // Primary Account Number (formatted with spaces)
        val expiryDate: String? = null, // MMYY format
        val expiryDateFormatted: String? = null, // MM/YY format
        val serviceCode: String? = null,
        val cardholderName: String? = null,
        val track1Data: String? = null,
        val track2Data: String? = null,
        val track2Equivalent: String? = null,
        val applicationLabel: String? = null,
        val applicationPreferredName: String? = null,
        val applicationInterchangeProfile: String? = null,
        val applicationExpirationDate: String? = null,
        val applicationPan: String? = null,
        val cardholderNameFromAid: String? = null,
        val cardScheme: String? = null, // Visa, Mastercard, AmEx, etc.
        val aid: String? = null, // Application Identifier
        val issuerCountry: String? = null, // ISO 3166 country code
        val cvv: String? = null, // Card Verification Value
        val cryptograms: Map<String, String> = emptyMap(), // ARQC, ARPC, etc.
        val rawAtr: String? = null, // Answer To Reset
        val rawApduResponses: Map<String, String> = emptyMap(),
        val allTlvData: Map<String, String> = emptyMap(),
    ) {
        fun toJson(): JSONObject {
            val json = JSONObject()
            pan?.let { json.put("pan", it) }
            panFormatted?.let { json.put("panFormatted", it) }
            expiryDate?.let { json.put("expiryDate", it) }
            expiryDateFormatted?.let { json.put("expiryDateFormatted", it) }
            serviceCode?.let { json.put("serviceCode", it) }
            cardholderName?.let { json.put("cardholderName", it) }
            track1Data?.let { json.put("track1Data", it) }
            track2Data?.let { json.put("track2Data", it) }
            track2Equivalent?.let { json.put("track2Equivalent", it) }
            applicationLabel?.let { json.put("applicationLabel", it) }
            applicationPreferredName?.let { json.put("applicationPreferredName", it) }
            applicationInterchangeProfile?.let { json.put("applicationInterchangeProfile", it) }
            applicationExpirationDate?.let { json.put("applicationExpirationDate", it) }
            applicationPan?.let { json.put("applicationPan", it) }
            cardholderNameFromAid?.let { json.put("cardholderNameFromAid", it) }
            cardScheme?.let { json.put("cardScheme", it) }
            aid?.let { json.put("aid", it) }
            issuerCountry?.let { json.put("issuerCountry", it) }
            cvv?.let { json.put("cvv", it) }
            if (cryptograms.isNotEmpty()) {
                val cryptogramsJson = JSONObject()
                cryptograms.forEach { cryptogramsJson.put(it.key, it.value) }
                json.put("cryptograms", cryptogramsJson)
            }
            rawAtr?.let { json.put("rawAtr", it) }
            if (rawApduResponses.isNotEmpty()) {
                val apduJson = JSONObject()
                rawApduResponses.forEach { apduJson.put(it.key, it.value) }
                json.put("rawApduResponses", apduJson)
            }
            if (allTlvData.isNotEmpty()) {
                val tlvJson = JSONObject()
                allTlvData.forEach { tlvJson.put(it.key, it.value) }
                json.put("allTlvData", tlvJson)
            }
            return json
        }
    }

    /**
     * Attempts to read EMV card data from a tag using IsoDep.
     * Returns null if the tag doesn't support ISO-DEP or isn't a bank card.
     */
    fun readEmvCard(tag: Tag): EmvCardData? {
        val isoDep = IsoDep.get(tag) ?: return null

        return try {
            isoDep.connect()
            isoDep.timeout = 5000

            val atr = isoDep.historicalBytes
            val atrHex = if (atr != null && atr.isNotEmpty()) HexUtils.toHex(atr) else null

            val builder = MutableEmvCardData(rawAtr = atrHex)

            // Try to select PPSE (Proximity Payment System Environment)
            val ppseResponse = sendApdu(isoDep, "00A404000E325041592E5359532E4444463031")
            if (ppseResponse != null) {
                builder.rawApduResponses = builder.rawApduResponses + ("PPSE" to ppseResponse)
                parseTlvData(ppseResponse, builder)
            }

            // Try to select PSE (Payment System Environment)
            val pseResponse = sendApdu(isoDep, "00A404000E315041592E5359532E4444463031")
            if (pseResponse != null) {
                builder.rawApduResponses = builder.rawApduResponses + ("PSE" to pseResponse)
                parseTlvData(pseResponse, builder)
            }

            // Try to read application data
            val applications = findApplications(isoDep, builder)
            if (applications.isNotEmpty()) {
                // Select first application
                val firstAid = applications.first()
                val selectResponse = sendApdu(isoDep, "00A40400${String.format("%02X", firstAid.length / 2)}$firstAid")
                if (selectResponse != null) {
                    builder.rawApduResponses = builder.rawApduResponses + ("SELECT_APP" to selectResponse)
                    parseTlvData(selectResponse, builder)

                    // Extract card data from response
                    extractCardDataFromResponse(selectResponse, builder)

                    // Get Processing Options (GPO) - this is crucial for reading card data
                    val gpoResponse = sendApdu(isoDep, "80A8000002830000")
                    if (gpoResponse != null) {
                        builder.rawApduResponses = builder.rawApduResponses + ("GPO" to gpoResponse)
                        parseTlvData(gpoResponse, builder)
                        extractCardDataFromResponse(gpoResponse, builder)
                        
                        // Extract AFL (Application File Locator) from GPO response
                        val afl = extractTlvValue(gpoResponse, "94")
                        if (afl != null) {
                            // Parse AFL and read records
                            parseAflAndReadRecords(isoDep, afl, builder)
                        }
                    }

                    // Try to read records using standard method
                    readApplicationRecords(isoDep, builder)
                    
                    // Try to read additional data using GET DATA commands
                    readAdditionalCardData(isoDep, builder)
                    
                    // Try to read cryptograms and CVV
                    readSecurityData(isoDep, builder)
                }
            } else {
                // If no AID found, try to read basic card data directly
                readBasicCardData(isoDep, builder)
            }

            builder.build()
        } catch (e: Exception) {
            null
        } finally {
            runCatching { isoDep.close() }
        }
    }

    private fun sendApdu(isoDep: IsoDep, command: String): String? {
        return try {
            val cmdBytes = HexUtils.fromHex(command)
            val response = isoDep.transceive(cmdBytes)
            val sw = response.takeLast(2)
            if (sw.size == 2 && sw[0].toInt() == 0x90 && sw[1].toInt() == 0x00) {
                HexUtils.toHex(response.dropLast(2).toByteArray())
            } else {
                // Also accept 61XX (data available) and 6CXX (wrong length) responses
                if (sw.size == 2 && sw[0].toInt() == 0x61) {
                    // Data available, return what we have
                    HexUtils.toHex(response.dropLast(2).toByteArray())
                } else {
                    null
                }
            }
        } catch (e: IOException) {
            null
        } catch (e: Exception) {
            null
        }
    }
    
    private fun parseAflAndReadRecords(isoDep: IsoDep, afl: String, builder: MutableEmvCardData) {
        // AFL format: SFI (5 bits) | Record count (3 bits) | First record | Last record
        val aflBytes = HexUtils.fromHex(afl)
        var offset = 0
        while (offset < aflBytes.size - 3) {
            val sfi = (aflBytes[offset].toInt() and 0xF8) shr 3
            val recordCount = aflBytes[offset].toInt() and 0x07
            val firstRecord = aflBytes[offset + 1].toInt()
            val lastRecord = aflBytes[offset + 2].toInt()
            
            for (record in firstRecord..lastRecord) {
                val p1 = (sfi shl 3) or 0x04
                val p2 = record
                val command = String.format("00B2%02X%02X00", p1, p2)
                val response = sendApdu(isoDep, command)
                if (response != null && response.isNotEmpty()) {
                    builder.rawApduResponses = builder.rawApduResponses + ("AFL_RECORD_SF${sfi}_R$record" to response)
                    parseTlvData(response, builder)
                    extractCardDataFromResponse(response, builder)
                }
            }
            offset += 4
        }
    }
    
    private fun readAdditionalCardData(isoDep: IsoDep, builder: MutableEmvCardData) {
        // Try to read additional data using GET DATA command
        val dataTags = listOf(
            "5A", // PAN
            "5F24", // Expiry Date
            "5F20", // Cardholder Name
            "57", // Track 2 Equivalent
            "50", // Application Label
            "9F12", // Application Preferred Name
            "82", // Application Interchange Profile
            "8E", // CVM List
            "9F07", // Application Usage Control
        )
        
        for (tag in dataTags) {
            val tagBytes = HexUtils.fromHex(tag)
            val command = "80CA${HexUtils.toHex(tagBytes)}00"
            val response = sendApdu(isoDep, command)
            if (response != null && response.isNotEmpty()) {
                builder.rawApduResponses = builder.rawApduResponses + ("GET_DATA_$tag" to response)
                parseTlvData(response, builder)
                extractCardDataFromResponse(response, builder)
            }
        }
    }
    
    private fun readBasicCardData(isoDep: IsoDep, builder: MutableEmvCardData) {
        // Try to read basic card data without selecting application
        readAdditionalCardData(isoDep, builder)
    }
    
    private fun readSecurityData(isoDep: IsoDep, builder: MutableEmvCardData) {
        // Try to read cryptograms (ARQC, ARPC, etc.)
        val cryptogramTags = listOf(
            "9F26", // Application Cryptogram (ARQC)
            "9F27", // Cryptogram Information Data
            "80", // Response Message Template Format 1
        )
        
        val cryptograms = mutableMapOf<String, String>()
        for (tag in cryptogramTags) {
            val tagBytes = HexUtils.fromHex(tag)
            val command = "80CA${HexUtils.toHex(tagBytes)}00"
            val response = sendApdu(isoDep, command)
            if (response != null && response.isNotEmpty()) {
                cryptograms[tag] = response
            }
        }
        builder.cryptograms = cryptograms
        
        // Try to read CVV (Card Verification Value) - Tag 9F0D
        val cvvTag = "9F0D"
        val cvvTagBytes = HexUtils.fromHex(cvvTag)
        val cvvCommand = "80CA${HexUtils.toHex(cvvTagBytes)}00"
        val cvvResponse = sendApdu(isoDep, cvvCommand)
        if (cvvResponse != null && cvvResponse.isNotEmpty()) {
            builder.cvv = cvvResponse
        }
        
        // Try to read issuer country - Tag 5F28
        val countryTag = "5F28"
        val countryTagBytes = HexUtils.fromHex(countryTag)
        val countryCommand = "80CA${HexUtils.toHex(countryTagBytes)}00"
        val countryResponse = sendApdu(isoDep, countryCommand)
        if (countryResponse != null && countryResponse.isNotEmpty()) {
            try {
                val countryBytes = HexUtils.fromHex(countryResponse)
                if (countryBytes.size >= 2) {
                    val countryCode = (countryBytes[0].toInt() shl 8) or (countryBytes[1].toInt() and 0xFF)
                    builder.issuerCountry = String.format("%03d", countryCode)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun findApplications(isoDep: IsoDep, builder: MutableEmvCardData): List<String> {
        val applications = mutableListOf<String>()
        // Common AIDs
        val commonAids = listOf(
            "A0000000041010", // Visa
            "A0000000042203", // Visa Debit
            "A0000000043060", // Visa Electron
            "A0000000050001", // Mastercard
            "A0000000031010", // Amex
            "A0000001523010", // Discover
        )

        for (aid in commonAids) {
            val response = sendApdu(isoDep, "00A40400${String.format("%02X", aid.length / 2)}$aid")
            if (response != null) {
                applications.add(aid)
                builder.rawApduResponses = builder.rawApduResponses + ("AID_$aid" to response)
            }
        }
        return applications
    }

    private fun readApplicationRecords(isoDep: IsoDep, builder: MutableEmvCardData) {
        // Try to read records using READ RECORD command
        for (sfi in 1..31) {
            for (record in 1..10) {
                val p1 = (sfi shl 3) or 0x04
                val p2 = record
                val command = String.format("00B2%02X%02X00", p1, p2)
                val response = sendApdu(isoDep, command)
                if (response != null && response.isNotEmpty()) {
                    builder.rawApduResponses = builder.rawApduResponses + ("RECORD_SF${sfi}_R$record" to response)
                    parseTlvData(response, builder)
                    extractCardDataFromResponse(response, builder)
                } else {
                    break
                }
            }
        }
    }

    private fun extractCardDataFromResponse(response: String, builder: MutableEmvCardData) {
        // Extract PAN (Primary Account Number) - Tag 5A
        val pan = extractTlvValue(response, "5A")
        if (pan != null) {
            builder.pan = pan
            builder.applicationPan = pan
            // Format PAN: PAN in EMV is BCD encoded (each byte is 2 digits)
            try {
                val panBytes = HexUtils.fromHex(pan)
                // PAN is BCD encoded, so each byte represents 2 decimal digits
                val panDecimal = panBytes.joinToString("") { byte ->
                    val high = (byte.toInt() shr 4) and 0x0F
                    val low = byte.toInt() and 0x0F
                    if (high <= 9 && low <= 9) {
                        "$high$low"
                    } else {
                        // If not valid BCD, try as hex
                        "%02X".format(byte.toInt() and 0xFF)
                    }
                }
                // Remove trailing F if present (padding)
                val cleanedPan = panDecimal.trimEnd('F', 'f')
                builder.panFormatted = cleanedPan.chunked(4).joinToString(" ")
            } catch (e: Exception) {
                // If parsing fails, use hex representation
                builder.panFormatted = pan.chunked(4).joinToString(" ")
            }
        }

        // Extract Expiry Date - Tag 5F24
        val expiry = extractTlvValue(response, "5F24")
        if (expiry != null && expiry.length >= 4) {
            val expiryHex = expiry.take(4)
            builder.expiryDate = expiryHex
            builder.applicationExpirationDate = expiryHex
            // Format expiry: MM/YY (BCD encoded)
            try {
                val expiryBytes = HexUtils.fromHex(expiryHex)
                if (expiryBytes.size >= 2) {
                    val monthHigh = (expiryBytes[0].toInt() shr 4) and 0x0F
                    val monthLow = expiryBytes[0].toInt() and 0x0F
                    val yearHigh = (expiryBytes[1].toInt() shr 4) and 0x0F
                    val yearLow = expiryBytes[1].toInt() and 0x0F
                    if (monthHigh <= 9 && monthLow <= 9 && yearHigh <= 9 && yearLow <= 9) {
                        val month = "$monthHigh$monthLow"
                        val year = "$yearHigh$yearLow"
                        builder.expiryDateFormatted = "$month/$year"
                    } else {
                        // Fallback to hex
                        val month = "%02X".format(expiryBytes[0].toInt() and 0xFF)
                        val year = "%02X".format(expiryBytes[1].toInt() and 0xFF)
                        builder.expiryDateFormatted = "$month/$year"
                    }
                }
            } catch (e: Exception) {
                // If parsing fails, use raw hex
                builder.expiryDateFormatted = expiryHex
            }
        }

        // Extract Service Code - Tag 5F30
        val serviceCode = extractTlvValue(response, "5F30")
        if (serviceCode != null) {
            builder.serviceCode = serviceCode
        }

        // Extract Cardholder Name - Tag 5F20
        val cardholderName = extractTlvValue(response, "5F20")
        if (cardholderName != null) {
            builder.cardholderName = String(HexUtils.fromHex(cardholderName).filter { it.toInt() in 32..126 }.toByteArray())
            builder.cardholderNameFromAid = builder.cardholderName
        }

        // Extract Application Label - Tag 50
        val appLabel = extractTlvValue(response, "50")
        if (appLabel != null) {
            builder.applicationLabel = String(HexUtils.fromHex(appLabel).filter { it.toInt() in 32..126 }.toByteArray())
        }

        // Extract Application Preferred Name - Tag 9F12
        val appPreferredName = extractTlvValue(response, "9F12")
        if (appPreferredName != null) {
            builder.applicationPreferredName = String(HexUtils.fromHex(appPreferredName).filter { it.toInt() in 32..126 }.toByteArray())
        }

        // Extract Application Interchange Profile - Tag 82
        val aip = extractTlvValue(response, "82")
        if (aip != null) {
            builder.applicationInterchangeProfile = aip
        }

        // Extract Track 2 Equivalent Data - Tag 57
        val track2 = extractTlvValue(response, "57")
        if (track2 != null) {
            builder.track2Equivalent = track2
            // Parse track 2 format: PAN + separator + expiry + service code + ...
            // Track 2 is in hex, so we need to parse it differently
            try {
                val track2Bytes = HexUtils.fromHex(track2)
                val track2String = String(track2Bytes.filter { it.toInt() in 32..126 }.toByteArray())
                if (track2String.length >= 19) {
                    val panEnd = track2String.indexOf('D').takeIf { idx -> idx > 0 } ?: track2String.indexOf('=').takeIf { idx -> idx > 0 } ?: -1
                    if (panEnd > 0) {
                        val panFromTrack = track2String.substring(0, panEnd)
                        if (builder.pan == null) {
                            builder.pan = HexUtils.toHex(panFromTrack.toByteArray())
                            builder.panFormatted = panFromTrack.chunked(4).joinToString(" ")
                        }
                        if (track2String.length >= panEnd + 5) {
                            val expiryFromTrack = track2String.substring(panEnd + 1, panEnd + 5)
                            if (builder.expiryDate == null) {
                                builder.expiryDate = expiryFromTrack
                                builder.expiryDateFormatted = "${expiryFromTrack.take(2)}/${expiryFromTrack.drop(2)}"
                            }
                            if (track2String.length >= panEnd + 7) {
                                builder.serviceCode = track2String.substring(panEnd + 5, panEnd + 7)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // If parsing fails, just store the raw hex
            }
        }
    }

    private fun parseTlvData(data: String, builder: MutableEmvCardData) {
        var offset = 0
        val hexData = data

        while (offset < hexData.length - 2) {
            val tag = extractTag(hexData, offset)
            if (tag == null) break

            val tagHex = tag.first
            val tagLength = tag.second
            offset = tag.third

            if (offset + tagLength * 2 > hexData.length) break

            val value = hexData.substring(offset, offset + tagLength * 2)
            builder.allTlvData = builder.allTlvData + (tagHex to value)
            offset += tagLength * 2
        }
    }

    private fun extractTag(data: String, offset: Int): Triple<String, Int, Int>? {
        if (offset + 2 > data.length) return null

        val firstByte = data.substring(offset, offset + 2).toInt(16)
        val tagClass = (firstByte and 0xC0) shr 6
        val tagNumber = firstByte and 0x1F

        var tagHex = String.format("%02X", firstByte)
        var currentOffset = offset + 2

        // Handle multi-byte tags
        if (tagNumber == 0x1F) {
            while (currentOffset + 2 <= data.length) {
                val nextByte = data.substring(currentOffset, currentOffset + 2).toInt(16)
                tagHex += String.format("%02X", nextByte)
                currentOffset += 2
                if ((nextByte and 0x80) == 0) break
            }
        }

        // Extract length
        if (currentOffset + 2 > data.length) return null
        val lengthByte = data.substring(currentOffset, currentOffset + 2).toInt(16)
        currentOffset += 2

        val length = if ((lengthByte and 0x80) == 0) {
            lengthByte
        } else {
            val lengthBytes = lengthByte and 0x7F
            if (currentOffset + lengthBytes * 2 > data.length) return null
            var len = 0
            for (i in 0 until lengthBytes) {
                len = (len shl 8) or data.substring(currentOffset, currentOffset + 2).toInt(16)
                currentOffset += 2
            }
            len
        }

        return Triple(tagHex, length, currentOffset)
    }

    private fun extractTlvValue(data: String, tag: String): String? {
        var offset = 0
        while (offset < data.length - 2) {
            val tlv = extractTag(data, offset) ?: break
            if (tlv.first == tag) {
                val valueStart = tlv.third
                val valueLength = tlv.second * 2
                if (valueStart + valueLength <= data.length) {
                    return data.substring(valueStart, valueStart + valueLength)
                }
            }
            // Skip to next tag
            val skipLength = tlv.second * 2
            offset = tlv.third + skipLength
        }
        return null
    }

    private data class MutableEmvCardData(
        var pan: String? = null,
        var panFormatted: String? = null,
        var expiryDate: String? = null,
        var expiryDateFormatted: String? = null,
        var serviceCode: String? = null,
        var cardholderName: String? = null,
        var track1Data: String? = null,
        var track2Data: String? = null,
        var track2Equivalent: String? = null,
        var applicationLabel: String? = null,
        var applicationPreferredName: String? = null,
        var applicationInterchangeProfile: String? = null,
        var applicationExpirationDate: String? = null,
        var applicationPan: String? = null,
        var cardholderNameFromAid: String? = null,
        var cardScheme: String? = null,
        var aid: String? = null,
        var issuerCountry: String? = null,
        var cvv: String? = null,
        var cryptograms: Map<String, String> = emptyMap(),
        var rawAtr: String? = null,
        var rawApduResponses: Map<String, String> = emptyMap(),
        var allTlvData: Map<String, String> = emptyMap(),
    ) {
        fun build() = EmvCardData(
            pan = pan,
            panFormatted = panFormatted,
            expiryDate = expiryDate,
            expiryDateFormatted = expiryDateFormatted,
            serviceCode = serviceCode,
            cardholderName = cardholderName,
            track1Data = track1Data,
            track2Data = track2Data,
            track2Equivalent = track2Equivalent,
            applicationLabel = applicationLabel,
            applicationPreferredName = applicationPreferredName,
            applicationInterchangeProfile = applicationInterchangeProfile,
            applicationExpirationDate = applicationExpirationDate,
            applicationPan = applicationPan,
            cardholderNameFromAid = cardholderNameFromAid,
            cardScheme = cardScheme,
            aid = aid,
            issuerCountry = issuerCountry,
            cvv = cvv,
            cryptograms = cryptograms,
            rawAtr = rawAtr,
            rawApduResponses = rawApduResponses,
            allTlvData = allTlvData,
        )
    }

    private fun EmvCardData.toBuilder() = MutableEmvCardData(
        pan = pan,
        panFormatted = panFormatted,
        expiryDate = expiryDate,
        expiryDateFormatted = expiryDateFormatted,
        serviceCode = serviceCode,
        cardholderName = cardholderName,
        track1Data = track1Data,
        track2Data = track2Data,
        track2Equivalent = track2Equivalent,
        applicationLabel = applicationLabel,
        applicationPreferredName = applicationPreferredName,
        applicationInterchangeProfile = applicationInterchangeProfile,
        applicationExpirationDate = applicationExpirationDate,
        applicationPan = applicationPan,
        cardholderNameFromAid = cardholderNameFromAid,
        cardScheme = cardScheme,
        aid = aid,
        issuerCountry = issuerCountry,
        cvv = cvv,
        cryptograms = cryptograms,
        rawAtr = rawAtr,
        rawApduResponses = rawApduResponses,
        allTlvData = allTlvData,
    )
}

