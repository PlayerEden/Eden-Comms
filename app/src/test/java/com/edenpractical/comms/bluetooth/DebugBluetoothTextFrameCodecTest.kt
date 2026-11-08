package com.edenpractical.comms.bluetooth

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM test pairs only: NOT real Android RFCOMM, TLS, identity or consent.
 */
class DebugBluetoothTextFrameCodecTest {
    private val codec = DebugBluetoothTextFrameCodec

    @Test fun textAndAckRoundTripUnicodeAndExactlyBoundedLengths() {
        val msg = "Hello 👋 — Swedish: hej!\nGood company."
        val bytes = codec.text(17, msg)
        assertEquals(codec.HEADER_BYTES + msg.toByteArray(Charsets.UTF_8).size, bytes.size)
        val parsed = codec.decode(bytes)
        assertEquals(DebugBluetoothTextFrameCodec.Kind.TEXT, parsed.kind)
        assertEquals(17L, parsed.messageId)
        assertEquals(msg, parsed.text)

        val ack = codec.acknowledgment(17)
        assertEquals(codec.HEADER_BYTES, ack.size)
        assertEquals(DebugBluetoothTextFrameCodec.Kind.ACK, codec.decode(ack).kind)
        assertEquals(17L, codec.decode(ack).messageId)
        assertEquals(null, codec.decode(ack).text)

        val full = codec.text(99, "x".repeat(codec.MAX_TEXT_BYTES))
        assertEquals(codec.MAX_FRAME_BYTES, full.size)
        assertEquals("x".repeat(codec.MAX_TEXT_BYTES), codec.decode(full).text)
    }

    @Test fun rejectMalformedTextBeforeAnyMessageIdIsReserved() {
        for (bad in listOf("", "   ", "no\u0000nul", "x".repeat(codec.MAX_TEXT_BYTES + 1))) {
            assertThrows(IllegalArgumentException::class.java) { codec.text(1, bad) }
        }
        assertThrows(IllegalArgumentException::class.java) { codec.text(0, "ok") }
        assertThrows(IllegalArgumentException::class.java) { codec.acknowledgment(0) }
        assertThrows(CharacterCodingException::class.java) { codec.text(1, "\uD800") }

        val ledger = DebugBluetoothTextLedger()
        assertThrows(IllegalArgumentException::class.java) { ledger.prepareText("") }
        assertEquals(0, ledger.outstanding())
        assertEquals(1L, codec.decode(ledger.prepareText("first")).messageId)
    }

    @Test fun rejectTruncationPrefixOverrunHeaderMutationsAndInvalidUtf8() {
        val valid = codec.text(5, "hello")
        val bad = mutableListOf(
            byteArrayOf(), ByteArray(codec.HEADER_BYTES - 1),
            valid.copyOf(valid.size - 1),
            valid + 0,
            valid.copyOf().also { it[0] = 0 },
            valid.copyOf().also { it[4] = 2 }, // unsupported version
            valid.copyOf().also { it[5] = 7 }, // unknown kind
            valid.copyOf().also { it[13] = 0 }, // zero identifier
            valid.copyOf().also { it[14] = 0; it[15] = 0 },
            valid.copyOf().also { it[14] = 2; it[15] = 0 },
            valid.copyOf().also { it[codec.HEADER_BYTES] = 0xC3.toByte() },
            ByteArray(codec.MAX_FRAME_BYTES + 1),
        )
        val ack = codec.acknowledgment(4)
        bad.add(ack + 1)
        bad.add(ack.copyOf().also { it[15] = 1 })
        for (input in bad) {
            assertThrows(Exception::class.java) { codec.decode(input) }
        }
    }

    @Test fun twoIndependentLedgersSupportBidirectionalTextAndTruePeerAppAckRehearsal() {
        val host = DebugBluetoothTextLedger()
        val join = DebugBluetoothTextLedger()
        val h1 = host.prepareText("Can you hear me?")
        assertEquals(1, host.outstanding())
        val received = join.acceptText(h1)
        assertEquals("Can you hear me?", received.text)
        assertEquals(1L, received.messageId)
        assertEquals(1L, host.acceptAck(received.ack))
        assertEquals(0, host.outstanding())

        val j1 = join.prepareText("Yes, on the simulated protocol!")
        val onHost = host.acceptText(j1)
        assertEquals(1L, join.acceptAck(onHost.ack))
        assertEquals(0, join.outstanding())
        host.close()
        join.close()
    }

    @Test fun repeatedSkippedOrOutOfOrderInboundFailsClosed() {
        val ledger = DebugBluetoothTextLedger()
        assertThrows(IllegalArgumentException::class.java) {
            ledger.acceptText(codec.text(2, "skipped"))
        }
        ledger.acceptText(codec.text(1, "first"))
        assertThrows(IllegalArgumentException::class.java) {
            ledger.acceptText(codec.text(1, "replayed"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ledger.acceptText(codec.acknowledgment(2))
        }
        assertEquals(2L, ledger.acceptText(codec.text(2, "second")).messageId)
        ledger.close()
        assertThrows(IllegalArgumentException::class.java) {
            ledger.acceptText(codec.text(3, "too late"))
        }
    }

    @Test fun ackRequiresLiveLocalPendingMessageAndOneUseId() {
        val ledger = DebugBluetoothTextLedger()
        assertThrows(IllegalArgumentException::class.java) {
            ledger.acceptAck(codec.acknowledgment(1))
        }
        ledger.prepareText("a")
        ledger.prepareText("b")
        assertThrows(IllegalArgumentException::class.java) {
            ledger.acceptAck(codec.text(1, "fake"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ledger.acceptAck(codec.acknowledgment(3))
        }
        assertEquals(2L, ledger.acceptAck(codec.acknowledgment(2)))
        assertEquals(1L, ledger.acceptAck(codec.acknowledgment(1)))
        assertThrows(IllegalArgumentException::class.java) {
            ledger.acceptAck(codec.acknowledgment(2))
        }
        assertEquals(0, ledger.outstanding())
    }

    @Test fun pendingAndDirectionBudgetsAreFiniteAndCannotBeRearmed() {
        val ledger = DebugBluetoothTextLedger()
        repeat(DebugBluetoothTextLedger.MAX_PENDING) { ledger.prepareText("x") }
        assertEquals(DebugBluetoothTextLedger.MAX_PENDING, ledger.outstanding())
        assertThrows(IllegalArgumentException::class.java) { ledger.prepareText("x") }
        assertEquals(1L, ledger.acceptAck(codec.acknowledgment(1)))
        assertEquals(9L, codec.decode(ledger.prepareText("new")).messageId)

        val many = DebugBluetoothTextLedger()
        repeat(DebugBluetoothTextLedger.MAX_MESSAGES_PER_DIRECTION) { i ->
            val id = codec.decode(many.prepareText("x")).messageId
            assertEquals(i + 1L, id)
            many.acceptAck(codec.acknowledgment(id))
        }
        assertThrows(IllegalArgumentException::class.java) { many.prepareText("over") }
        many.close()
        assertEquals(0, many.outstanding())
        assertThrows(IllegalArgumentException::class.java) { many.prepareText("after close") }
        assertThrows(IllegalArgumentException::class.java) {
            many.acceptAck(codec.acknowledgment(128))
        }
    }

    @Test fun zeroIdAckAndOversizedInputAreNeverAccepted() {
        val invalidAck = codec.acknowledgment(1).copyOf().also {
            ByteBuffer.wrap(it, 6, 8).putLong(0L)
        }
        assertThrows(IllegalArgumentException::class.java) { codec.decode(invalidAck) }
        assertThrows(IllegalArgumentException::class.java) {
            codec.decode(ByteArray(codec.MAX_FRAME_BYTES + 1))
        }
    }
}
