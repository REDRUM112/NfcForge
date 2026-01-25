package com.aiu.nfcforge.nfc

/**
 * Typed write failures for user-friendly error messages.
 */
sealed class WriteFailure(
    val userMessage: String,
    cause: Throwable? = null,
) : Exception(userMessage, cause) {

    class UnsupportedTag : WriteFailure("Unsupported tag type (no NDEF support).")

    class ReadOnlyTag : WriteFailure("Tag is read-only.")

    class TagTooSmall(maxSizeBytes: Int, neededBytes: Int) :
        WriteFailure("Tag capacity too small. Max $maxSizeBytes bytes, need $neededBytes bytes.")

    class IoFailure(cause: Throwable) : WriteFailure("I/O error while writing tag.", cause)

    class FormatFailure(cause: Throwable) : WriteFailure("Tag format error.", cause)
}



