package com.edenpractical.comms.wifidirect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DebugWifiDirectQrCodecTest {
    private val pinA = ByteArray(32) { (it + 1).toByte() }
    private val pinB = ByteArray(32) { (it + 41).toByte() }
    private val now = 1_800_000_000L

    private fun reject(block: () -> Unit) {
        try { block(); fail("Should reject invalid or stale QR") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun threeQrTranscriptProducesSameVisualCodeWithoutAnyRadio() {
        val h = DebugWifiDirectQrCodec.newHost(pinA, now)
        val scannedHost = DebugWifiDirectQrCodec.decodeHost(
            DebugWifiDirectQrCodec.encodeHost(h), now + 1
        )
        val r = DebugWifiDirectQrCodec.newReply(scannedHost, pinB, now + 2)
        val scannedReply = DebugWifiDirectQrCodec.decodeReply(
            DebugWifiDirectQrCodec.encodeReply(r), h, now + 2
        )
        val ack = DebugWifiDirectQrCodec.makeAck(h, scannedReply, now + 3)
        val scannedAck = DebugWifiDirectQrCodec.decodeAck(
            DebugWifiDirectQrCodec.encodeAck(ack),
            scannedHost, r, pinB, now + 3
        )
        val left = DebugWifiDirectQrCodec.comparisonCode(h, scannedReply, now + 3)
        val right = DebugWifiDirectQrCodec.comparisonCode(scannedHost, r, now + 3)
        assertEquals(left, right)
        assertTrue(left.matches(Regex("[0-9A-F]{4}-[0-9A-F]{4}")))
        assertEquals(16, scannedAck.transcript.size)
        assertFalse(DebugWifiDirectQrCodec.encodeHost(h).contains("192.168."))
        assertFalse(DebugWifiDirectQrCodec.encodeHost(h).contains("DIRECT-"))
    }

    @Test fun wrongPeerWrongRoleAndWrongNonceAreRejected() {
        val h = DebugWifiDirectQrCodec.newHost(pinA, now)
        val r = DebugWifiDirectQrCodec.newReply(h, pinB, now + 1)
        val encoded = DebugWifiDirectQrCodec.encodeReply(r)
        reject { DebugWifiDirectQrCodec.newReply(h, pinA, now + 1) }
        reject { DebugWifiDirectQrCodec.decodeHost(encoded, now + 1) }
        reject { DebugWifiDirectQrCodec.decodeReply(
            DebugWifiDirectQrCodec.encodeHost(h), h, now + 1
        ) }
        val other = DebugWifiDirectQrCodec.newHost(pinA, now)
        reject { DebugWifiDirectQrCodec.decodeReply(encoded, other, now + 1) }
        val ack = DebugWifiDirectQrCodec.makeAck(h, r, now + 2)
        reject { DebugWifiDirectQrCodec.decodeAck(
            DebugWifiDirectQrCodec.encodeAck(ack), h, r, pinA, now + 2
        ) }
    }

    @Test fun oldAndFutureHostQrsExpireFailClosed() {
        val h = DebugWifiDirectQrCodec.newHost(pinA, now)
        val raw = DebugWifiDirectQrCodec.encodeHost(h)
        DebugWifiDirectQrCodec.decodeHost(raw, now - 45)
        DebugWifiDirectQrCodec.decodeHost(raw, now + 180)
        reject { DebugWifiDirectQrCodec.decodeHost(raw, now - 46) }
        reject { DebugWifiDirectQrCodec.decodeHost(raw, now + 181) }
        reject { DebugWifiDirectQrCodec.newReply(h, pinB, now + 181) }
        val r = DebugWifiDirectQrCodec.newReply(h, pinB, now + 2)
        reject { DebugWifiDirectQrCodec.makeAck(h, r, now + 181) }
        reject { DebugWifiDirectQrCodec.comparisonCode(h, r, now + 181) }
    }

    @Test fun modifiedAckUnrelatedAckAndShortenedFieldsAreRejected() {
        val h = DebugWifiDirectQrCodec.newHost(pinA, now)
        val r = DebugWifiDirectQrCodec.newReply(h, pinB, now)
        val raw = DebugWifiDirectQrCodec.encodeAck(
            DebugWifiDirectQrCodec.makeAck(h, r, now)
        )
        val bad = raw.dropLast(1) + if (raw.last() == 'A') "B" else "A"
        reject { DebugWifiDirectQrCodec.decodeAck(bad, h, r, pinB, now) }
        reject { DebugWifiDirectQrCodec.decodeAck(raw + "A", h, r, pinB, now) }
        reject { DebugWifiDirectQrCodec.decodeAck(raw.lowercase(), h, r, pinB, now) }
        val hOther = DebugWifiDirectQrCodec.newHost(pinA, now)
        reject { DebugWifiDirectQrCodec.decodeAck(raw, hOther, r, pinB, now) }
    }

    @Test fun canonicalTimeWrongVersionAndInputLimitsAreEnforced() {
        val h = DebugWifiDirectQrCodec.newHost(pinA, now)
        val raw = DebugWifiDirectQrCodec.encodeHost(h)
        reject { DebugWifiDirectQrCodec.decodeHost(raw.replace("ECWDH1", "ECWDH2"), now) }
        reject { DebugWifiDirectQrCodec.decodeHost(raw.replace(
            "|$now", "|0$now"
        ), now) }
        reject { DebugWifiDirectQrCodec.decodeHost(raw.replace(
            "|$now", "|-1"
        ), now) }
        reject { DebugWifiDirectQrCodec.decodeHost(raw + "|extra", now) }
        reject { DebugWifiDirectQrCodec.decodeHost("x".repeat(171), now) }
        reject { DebugWifiDirectQrCodec.newHost(ByteArray(32), now) }
        reject { DebugWifiDirectQrCodec.newHost(ByteArray(31), now) }
    }

    @Test fun arraysAreCopiedAndClearedAndDifferentTranscriptsDiffer() {
        val inputPin = pinA.copyOf()
        val h = DebugWifiDirectQrCodec.newHost(inputPin, now)
        inputPin.fill(0)
        assertEquals(pinA.toList(), h.pin.toList())
        val r = DebugWifiDirectQrCodec.newReply(h, pinB, now)
        val oldCode = DebugWifiDirectQrCodec.comparisonCode(h, r, now)
        val another = DebugWifiDirectQrCodec.newReply(h, pinB, now)
        assertNotEquals(oldCode, DebugWifiDirectQrCodec.comparisonCode(h, another, now))
        val a = DebugWifiDirectQrCodec.makeAck(h, r, now)
        h.clear(); r.clear(); a.clear()
        assertTrue(h.pin.all { it == 0.toByte() })
        assertTrue(r.nonce.all { it == 0.toByte() })
        assertTrue(a.transcript.all { it == 0.toByte() })
        reject { DebugWifiDirectQrCodec.comparisonCode(h, r, now) }
    }
}
