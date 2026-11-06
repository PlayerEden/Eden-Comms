package com.edenpractical.comms.pairing

/**
 * Draft input rules for the development chat composer. Mirrors the accepted
 * character class and point-count of the strictly bounded debug text wire.
 * Transport still validates independently; this is not an auth or send gate.
 */
internal object DebugTextDraftPolicy {
    const val MAX_CODE_POINTS = 280

    fun codePoints(value: String): Int = value.codePointCount(0, value.length)

    fun accepts(value: String): Boolean {
        if (codePoints(value) > MAX_CODE_POINTS) return false
        var offset = 0
        while (offset < value.length) {
            val cp = Character.codePointAt(value, offset)
            if (cp in 0xD800..0xDFFF ||
                (cp < 0x20 && cp != 0x09 && cp != 0x0A) ||
                cp in 0x7F..0x9F
            ) return false
            offset += Character.charCount(cp)
        }
        return true
    }

    fun maySend(value: String): Boolean = value.isNotBlank() && accepts(value)
}
