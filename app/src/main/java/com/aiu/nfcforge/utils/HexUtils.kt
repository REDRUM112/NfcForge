package com.aiu.nfcforge.utils

/**
 * Small byte/hex helpers used across NFC parsing + display.
 */
object HexUtils {

    /**
     * Converts a ByteArray into an uppercase hex string without separators.
     * Example: [0x04, 0xA2] -> "04A2"
     */
    fun toHex(bytes: ByteArray): String =
        buildString(bytes.size * 2) {
            bytes.forEach { b ->
                append(((b.toInt() shr 4) and 0xF).toString(16))
                append((b.toInt() and 0xF).toString(16))
            }
        }.uppercase()

    /**
     * Converts a ByteArray into a compact hex preview (first [maxBytes] bytes).
     */
    fun toHexPreview(bytes: ByteArray, maxBytes: Int = 48): String {
        if (bytes.isEmpty()) return ""
        val slice = if (bytes.size <= maxBytes) bytes else bytes.copyOfRange(0, maxBytes)
        val hex = toHex(slice)
        return if (bytes.size <= maxBytes) hex else "$hex…"
    }

    /**
     * Converts a hex string into a ByteArray.
     * Example: "04A2" -> [0x04, 0xA2]
     */
    fun fromHex(hex: String): ByteArray {
        val cleanHex = hex.replace(" ", "").replace("-", "").uppercase()
        require(cleanHex.length % 2 == 0) { "Hex string must have even length" }
        return ByteArray(cleanHex.length / 2) { i ->
            cleanHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}



