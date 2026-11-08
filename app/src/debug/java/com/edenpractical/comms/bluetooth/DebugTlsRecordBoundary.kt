package com.edenpractical.comms.bluetooth

import javax.net.ssl.SSLException

/**
 * F2-B DEBUG ONLY. One complete TLS 1.3 encrypted record for the fixed-width
 * control adapter, not a general-purpose TLS record parser.
 *
 * RFC 8446 section 5.2: TLSCiphertext uses an outer application_data (23)
 * type, 0x0303 legacy version and at most (2^14 + 256) ciphertext bytes.
 * SSLEngine is still responsible for authenticating and decrypting the record.
 *
 * No record aggregation, reassembly, post-handshake KeyUpdate, or application
 * messaging is supported. An unsupported engine/provider must fail closed.
 */
internal object DebugTlsRecordBoundary {
    private const val HEADER_BYTES = 5
    private const val ENCRYPTED_TYPE = 23
    const val MAX_CIPHERTEXT_BODY = 16_384 + 256

    fun requireSingleCiphertext(record: ByteArray) {
        if (record.size < HEADER_BYTES + 1 ||
            (record[0].toInt() and 0xff) != ENCRYPTED_TYPE ||
            (record[1].toInt() and 0xff) != 3 ||
            (record[2].toInt() and 0xff) != 3
        ) throw SSLException("Unsupported TLS encrypted control record header")

        val length = ((record[3].toInt() and 0xff) shl 8) or
            (record[4].toInt() and 0xff)
        if (length !in 1..MAX_CIPHERTEXT_BODY ||
            record.size != HEADER_BYTES + length
        ) throw SSLException("TLS encrypted control record length mismatch")
    }
}
