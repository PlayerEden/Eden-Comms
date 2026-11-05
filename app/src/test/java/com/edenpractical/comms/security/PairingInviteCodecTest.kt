package com.edenpractical.comms.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class PairingInviteCodecTest {
    private val sample = ByteArray(32) { i -> (i * 7).toByte() }

    @Test fun roundTripPreservesAllFingerprintBytes() {
        assertArrayEquals(sample, PairingInviteCodec.decode(PairingInviteCodec.encode(sample)))
    }

    @Test fun qrContainsOnlyVersionAndFullPublicFingerprint() {
        val code = PairingInviteCodec.encode(sample)
        assertEquals(68, code.length)
        assertEquals("EC1:", code.take(4))
        assertFalse(code.contains('@'))
        assertFalse(code.contains('.'))
        assertFalse(code.contains('/'))
    }

    @Test fun malformedFingerprintSizeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            PairingInviteCodec.encode(ByteArray(31))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PairingInviteCodec.decode("EC1:")
        }
    }

    @Test fun alternateVersionsAndUnexpectedFieldsAreRejected() {
        val code = PairingInviteCodec.encode(sample)
        for (candidate in listOf("EC2:" + code.drop(4), code + ":name=bob", " " + code, code + " ")) {
            assertThrows(IllegalArgumentException::class.java) {
                PairingInviteCodec.decode(candidate)
            }
        }
    }

    @Test fun noncanonicalCaseAndNonhexCharactersAreRejected() {
        val code = PairingInviteCodec.encode(sample)
        val lowercase = code.lowercase()
        assertThrows(IllegalArgumentException::class.java) {
            PairingInviteCodec.decode(lowercase)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PairingInviteCodec.decode(code.substring(0, 10) + "Z" + code.substring(11))
        }
    }
}
