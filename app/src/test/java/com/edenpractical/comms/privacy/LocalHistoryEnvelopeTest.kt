package com.edenpractical.comms.privacy

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.spec.SecretKeySpec

class LocalHistoryEnvelopeTest {
    private fun key(seed: Int): SecretKeySpec =
        SecretKeySpec(ByteArray(32) { (seed + it).toByte() }, "AES")
    private val aad = "eden-comms/debug-history-v1/com.edenpractical.comms"
        .toByteArray(Charsets.US_ASCII)

    @Test fun authenticCiphertextRoundTripsWithUniqueNonce() {
        val plain = "short verified test message, not production history".toByteArray()
        val first = LocalHistoryEnvelope.seal(key(1), plain, aad)
        val second = LocalHistoryEnvelope.seal(key(1), plain, aad)
        assertFalse(first.contentEquals(second))
        assertArrayEquals(plain, LocalHistoryEnvelope.open(key(1), first, aad))
        assertArrayEquals(plain, LocalHistoryEnvelope.open(key(1), second, aad))
        assertFalse(first.toString(Charsets.ISO_8859_1).contains("short verified"))
    }

    @Test fun tamperedCiphertextWrongKeyOrAadFailsClosed() {
        val encoded = LocalHistoryEnvelope.seal(
            key(1), "Hello".toByteArray(), aad
        )
        val tampered = encoded.copyOf().also {
            it[it.lastIndex - 3] = (it[it.lastIndex - 3].toInt() xor 1).toByte()
        }
        assertThrowsAny { LocalHistoryEnvelope.open(key(1), tampered, aad) }
        assertThrowsAny { LocalHistoryEnvelope.open(key(2), encoded, aad) }
        assertThrowsAny {
            LocalHistoryEnvelope.open(key(1), encoded,
                "another package or wrong chat domain".toByteArray())
        }
        assertThrowsAny {
            LocalHistoryEnvelope.open(key(1),
                encoded.copyOf(encoded.size - 1), aad)
        }
    }

    @Test fun invalidLengthHeaderVersionOrOversizedInputFailsClosed() {
        val encoded = LocalHistoryEnvelope.seal(
            key(1), byteArrayOf(1, 2, 3), aad
        )
        val wrongHeader = encoded.copyOf().also { it[0] = 0 }
        val wrongVersion = encoded.copyOf().also { it[4] = 9 }
        val wrongIvSize = encoded.copyOf().also { it[5] = 0 }
        for (bad in listOf(wrongHeader, wrongVersion, wrongIvSize,
                           byteArrayOf(1), ByteArray(300_000))) {
            assertThrowsAny { LocalHistoryEnvelope.open(key(1), bad, aad) }
        }
        assertThrowsAny {
            LocalHistoryEnvelope.seal(key(1), ByteArray(250_000), aad)
        }
        assertThrowsAny {
            LocalHistoryEnvelope.seal(key(1), byteArrayOf(1), byteArrayOf())
        }
    }

    private inline fun assertThrowsAny(block: () -> Unit) {
        try {
            block()
            fail("Expected fail-closed encryption or decoding rejection")
        } catch (_: java.lang.AssertionError) {
            throw java.lang.AssertionError("Expected rejection")
        } catch (_: Exception) {
            // Any rejection is correct; the caller never displays data.
        }
    }
}
