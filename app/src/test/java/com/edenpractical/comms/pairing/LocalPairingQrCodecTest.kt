package com.edenpractical.comms.pairing

import java.security.SecureRandom
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class LocalPairingQrCodecTest {
    private val now = 1_800_000_000L
    private val a = ByteArray(32) { (it + 5).toByte() }
    private val b = ByteArray(32) { (it + 40).toByte() }

    private fun host() = LocalPairingQrCodec.newHostInvite(
        "192.168.1.18", a, now, SecureRandom(byteArrayOf(1, 2, 3, 4))
    )

    @Test fun hostPayloadRoundTripsWithoutExposingAnyPersonalData() {
        val invite = host()
        val payload = LocalPairingQrCodec.encodeHost(invite)
        val parsed = LocalPairingQrCodec.decodeHost(payload, now)
        assertEquals("192.168.1.18", parsed.wifiIp)
        assertEquals(now, parsed.issuedAtSeconds)
        assertArrayEquals(invite.nonce, parsed.nonce)
        assertArrayEquals(a, parsed.certificateSha256)
        assertFalse(payload.contains("@"))
        assertFalse(payload.contains("http"))
        assertFalse(payload.contains("/"))
    }

    @Test fun joinReplyEchoesHostNonceAndPreservesPeerPin() {
        val invite = host()
        val reply = LocalPairingQrCodec.newJoinReply(invite, b, now)
        val parsed = LocalPairingQrCodec.decodeJoin(
            LocalPairingQrCodec.encodeJoin(reply), invite, now
        )
        assertArrayEquals(invite.nonce, parsed.hostNonce)
        assertArrayEquals(b, parsed.certificateSha256)
    }

    @Test fun wrongHostNonceIsRejected() {
        val host = host()
        val other = host().copy(nonce = ByteArray(16) { 9 })
        val reply = LocalPairingQrCodec.newJoinReply(other, b, now)
        assertThrows(IllegalArgumentException::class.java) {
            LocalPairingQrCodec.decodeJoin(LocalPairingQrCodec.encodeJoin(reply), host, now)
        }
    }

    @Test fun expiredOrFutureQrIsRejected() {
        val text = LocalPairingQrCodec.encodeHost(host())
        assertThrows(IllegalArgumentException::class.java) {
            LocalPairingQrCodec.decodeHost(text, now + 301L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LocalPairingQrCodec.decodeHost(text, now - 46L)
        }
    }

    @Test fun otherNetworksAndNamesAreRejected() {
        for (ip in listOf("8.8.8.8", "example.com", "127.0.0.1", "192.168.01.4")) {
            assertThrows(IllegalArgumentException::class.java) {
                LocalPairingQrCodec.newHostInvite(ip, a, now)
            }
        }
    }

    @Test fun malformedAndNoncanonicalPayloadsAreRejected() {
        val h = host()
        val text = LocalPairingQrCodec.encodeHost(h)
        for (candidate in listOf(
            " " + text, text + "|extra", text.lowercase(), text.replace("EC2H", "EC1H"),
            text.replace("192.168.1.18", "10.0.0.256"),
            text.replace("192.168.1.18", "192.168.1.018")
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                LocalPairingQrCodec.decodeHost(candidate, now)
            }
        }
    }

    @Test fun selfPairingIsNotAllowed() {
        assertThrows(IllegalArgumentException::class.java) {
            LocalPairingQrCodec.newJoinReply(host(), a, now)
        }
    }

    @Test fun byteLengthErrorsDoNotBecomeTrustedPins() {
        assertThrows(IllegalArgumentException::class.java) {
            LocalPairingQrCodec.newHostInvite("192.168.1.8", ByteArray(30), now)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LocalPairingQrCodec.encodeJoin(
                LocalPairingQrCodec.JoinReply(ByteArray(16), b, ByteArray(15))
            )
        }
    }
}
