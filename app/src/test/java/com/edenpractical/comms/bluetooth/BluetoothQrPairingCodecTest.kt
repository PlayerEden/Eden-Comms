package com.edenpractical.comms.bluetooth

import com.edenpractical.comms.model.ReleaseCapabilities
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class BluetoothQrPairingCodecTest {
    private val clock = 1_799_000_000L
    private val hostPin = ByteArray(32) { 0x11 }
    private val joinPin = ByteArray(32) { 0x22 }

    @Test fun strictReciprocalQrExchangeAgreesOnCanonicalRolesAndNonces() {
        val host = BluetoothQrPairingCodec.freshHost(hostPin, clock)
        val encodedHost = BluetoothQrPairingCodec.encodeHost(host)
        val seenHost = BluetoothQrPairingCodec.decodeHost(encodedHost, clock)
        assertArrayEquals(hostPin, seenHost.pin)
        val reply = BluetoothQrPairingCodec.freshReply(seenHost, joinPin, clock)
        val encodedReply = BluetoothQrPairingCodec.encodeReply(reply)
        val receivedReply = BluetoothQrPairingCodec.decodeReply(encodedReply, host, clock)
        val hostReady = BluetoothQrPairingCodec.preparedHost(host, receivedReply, clock)
        val joinReady = BluetoothQrPairingCodec.preparedJoin(seenHost, reply, joinPin, clock)
        val hb = hostReady.binding(clock)
        val jb = joinReady.binding(clock)
        assertArrayEquals(hb.hostPin, jb.hostPin)
        assertArrayEquals(hb.joinPin, jb.joinPin)
        assertArrayEquals(hb.hostNonce, jb.hostNonce)
        assertArrayEquals(hb.joinNonce, jb.joinNonce)
        assertArrayEquals(joinPin, hostReady.peerFingerprint(clock))
        assertArrayEquals(hostPin, joinReady.peerFingerprint(clock))
        assertTrue(hostReady.hostRole)
        assertFalse(joinReady.hostRole)
        assertTrue(encodedHost.length <= 170)
        assertTrue(encodedReply.length <= 170)
        assertFalse(encodedHost.contains("."))
        assertFalse(encodedReply.contains(":"))
        hostReady.clear()
        joinReady.clear()
        assertThrows(IllegalArgumentException::class.java) { hostReady.binding(clock) }
        assertThrows(IllegalArgumentException::class.java) { joinReady.peerFingerprint(clock) }
        assertFalse(ReleaseCapabilities.CAN_CONNECT)
        assertFalse(ReleaseCapabilities.CAN_SEND_MESSAGES)
        assertFalse(ReleaseCapabilities.CAN_TRANSMIT_VOICE)
    }

    @Test fun invalidOrCanonicalizedQrFormatsAreRejected() {
        val host = BluetoothQrPairingCodec.freshHost(hostPin, clock)
        val original = BluetoothQrPairingCodec.encodeHost(host)
        for (bad in listOf(
            original.lowercase(),
            original + "|extra",
            " $original",
            original + "\n",
            original.replace("ECBH1", "EC2H"),
            original.replace("ECBH1", "ECBH2"),
            original.replace("|", "||"),
            original.substringBeforeLast("|") + "|0" + clock
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                BluetoothQrPairingCodec.decodeHost(bad, clock)
            }
        }
        val reply = BluetoothQrPairingCodec.freshReply(host, joinPin, clock)
        val encoded = BluetoothQrPairingCodec.encodeReply(reply)
        for (bad in listOf(
            encoded.lowercase(), encoded + "x", encoded + "|extra",
            encoded.replace("ECBJ1", "ECBH1"), encoded.replace("ECBJ1", "ECBJ2")
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                BluetoothQrPairingCodec.decodeReply(bad, host, clock)
            }
        }
    }

    @Test fun oldOrFutureDatedQrAndPreparedTranscriptsFailClosed() {
        val host = BluetoothQrPairingCodec.freshHost(hostPin, clock)
        val reply = BluetoothQrPairingCodec.freshReply(host, joinPin, clock)
        val prep = BluetoothQrPairingCodec.preparedHost(host, reply, clock)
        assertEquals(clock + 300L, prep.expiresAtSeconds)
        BluetoothQrPairingCodec.decodeHost(BluetoothQrPairingCodec.encodeHost(host), clock + 300L)
        assertThrows(IllegalArgumentException::class.java) {
            BluetoothQrPairingCodec.decodeHost(BluetoothQrPairingCodec.encodeHost(host), clock + 301L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BluetoothQrPairingCodec.decodeHost(BluetoothQrPairingCodec.encodeHost(host), clock - 46L)
        }
        assertThrows(IllegalArgumentException::class.java) { prep.binding(clock + 301L) }
        assertThrows(IllegalArgumentException::class.java) { prep.peerFingerprint(clock - 46L) }
        assertThrows(IllegalArgumentException::class.java) {
            BluetoothQrPairingCodec.preparedJoin(host, reply, joinPin, clock + 301L)
        }
    }

    @Test fun wrongReplyAndQrReplayDoNotPrepareAnotherSession() {
        val first = BluetoothQrPairingCodec.freshHost(hostPin, clock)
        val other = BluetoothQrPairingCodec.freshHost(hostPin, clock)
        val reply = BluetoothQrPairingCodec.freshReply(first, joinPin, clock)
        val wire = BluetoothQrPairingCodec.encodeReply(reply)
        assertThrows(IllegalArgumentException::class.java) {
            BluetoothQrPairingCodec.decodeReply(wire, other, clock)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BluetoothQrPairingCodec.preparedHost(other, reply, clock)
        }
        val decoded = BluetoothQrPairingCodec.decodeReply(wire, first, clock)
        assertArrayEquals(reply.pin, decoded.pin)
    }

    @Test fun selfPairZeroFieldsAndForgedOwnPinAreRejected() {
        val host = BluetoothQrPairingCodec.freshHost(hostPin, clock)
        assertThrows(IllegalArgumentException::class.java) {
            BluetoothQrPairingCodec.freshReply(host, hostPin, clock)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BluetoothQrPairingCodec.freshHost(ByteArray(32), clock)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BluetoothQrPairingCodec.freshHost(ByteArray(31), clock)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BluetoothQrPairingCodec.encodeHost(
                BluetoothQrPairingCodec.HostInvite(hostPin, ByteArray(16), clock)
            )
        }
        val reply = BluetoothQrPairingCodec.freshReply(host, joinPin, clock)
        assertThrows(IllegalArgumentException::class.java) {
            BluetoothQrPairingCodec.preparedJoin(host, reply, ByteArray(32) { 0x33 }, clock)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BluetoothQrPairingCodec.encodeReply(
                BluetoothQrPairingCodec.JoinReply(host.nonce, joinPin, host.nonce)
            )
        }
    }

    @Test fun decodedQrIsCopyIsolatedAndClearCannotReanimatePreparedState() {
        val source = ByteArray(32) { 0x5A }
        val host = BluetoothQrPairingCodec.freshHost(source, clock)
        source.fill(0)
        assertTrue(host.pin.any { it != 0.toByte() })
        val reply = BluetoothQrPairingCodec.freshReply(host, joinPin, clock)
        val prepared = BluetoothQrPairingCodec.preparedHost(host, reply, clock)
        val peer = prepared.peerFingerprint(clock)
        peer.fill(0)
        assertArrayEquals(joinPin, prepared.peerFingerprint(clock))
        prepared.clear()
        prepared.clear()
        assertThrows(IllegalArgumentException::class.java) {
            prepared.peerFingerprint(clock)
        }
    }

    @Test fun oversizedQrUnknownTagAndMalformedTimeDoNotParse() {
        val host = BluetoothQrPairingCodec.freshHost(hostPin, clock)
        val encoded = BluetoothQrPairingCodec.encodeHost(host)
        for (bad in listOf(
            "ECBH1|" + "A".repeat(300),
            encoded.replaceLast(clock.toString(), Long.MAX_VALUE.toString()),
            encoded.replaceLast(clock.toString(), "1.0"),
            encoded.replaceLast(clock.toString(), "-1"),
            encoded.replaceLast(clock.toString(), "00")
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                BluetoothQrPairingCodec.decodeHost(bad, clock)
            }
        }
    }

    private fun String.replaceLast(old: String, new: String): String =
        substringBeforeLast(old) + new + substringAfterLast(old)
}
