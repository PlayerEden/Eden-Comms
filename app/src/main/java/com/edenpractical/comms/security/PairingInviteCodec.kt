package com.edenpractical.comms.security

/**
 * Versioned public QR payload for an in-person, full-fingerprint comparison.
 *
 * Example: EC1: followed by exactly 64 uppercase hexadecimal characters.
 * The QR contains no names, addresses, phone numbers, IPs, or session keys.
 * It is NOT an authorization decision: users must verify the QR was shown on
 * the intended phone and a pinned mutual TLS handshake must still succeed.
 */
internal object PairingInviteCodec {
    private const val PREFIX = "EC1:"
    private const val HASH_HEX_LENGTH = 64
    private val HEX = "0123456789ABCDEF".toCharArray()

    fun encode(certificateSha256: ByteArray): String {
        require(certificateSha256.size == PinnedTlsPeer.FINGERPRINT_SIZE) {
            "Full certificate fingerprint required"
        }
        val chars = CharArray(PREFIX.length + HASH_HEX_LENGTH)
        PREFIX.toCharArray().copyInto(chars)
        certificateSha256.forEachIndexed { index, b ->
            val value = b.toInt() and 0xFF
            chars[PREFIX.length + index * 2] = HEX[value ushr 4]
            chars[PREFIX.length + index * 2 + 1] = HEX[value and 15]
        }
        return String(chars)
    }

    fun decode(qrPayload: String): ByteArray {
        // Exact format rejects whitespace, aliases and unknown future versions.
        require(qrPayload.length == PREFIX.length + HASH_HEX_LENGTH && qrPayload.startsWith(PREFIX)) {
            "Unsupported pairing QR payload"
        }
        val fingerprint = ByteArray(PinnedTlsPeer.FINGERPRINT_SIZE)
        for (index in fingerprint.indices) {
            val upper = decodeUpperHex(qrPayload[PREFIX.length + 2 * index])
            val lower = decodeUpperHex(qrPayload[PREFIX.length + 2 * index + 1])
            fingerprint[index] = ((upper shl 4) or lower).toByte()
        }
        return fingerprint
    }

    private fun decodeUpperHex(ch: Char): Int = when (ch) {
        in '0'..'9' -> ch.code - '0'.code
        in 'A'..'F' -> ch.code - 'A'.code + 10
        else -> throw IllegalArgumentException("Pairing QR is not canonical uppercase hex")
    }
}
