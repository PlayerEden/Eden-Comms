package com.edenpractical.comms.protocol

import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProposedChatWireCodecTest {
    private val codec = ProposedChatWireCodec

    @Test fun textWireHasStableReviewableBigEndianFixture() {
        val encoded = codec.encode(ProposedChatWireCodec.Packet.Text(1L, "Hi"))
        assertEquals("45435458010100000000000000000100024869", hex(encoded))
        assertEquals(ProposedChatWireCodec.Packet.Text(1L, "Hi"), codec.decode(encoded))
        assertEquals(codec.HEADER_BYTES + 2, encoded.size)
    }

    @Test fun ackFixtureHasNoPayloadAndDoesNotProveReceipt() {
        val ack = ProposedChatWireCodec.Packet.Ack(42L)
        val encoded = codec.encode(ack)
        assertEquals("45435458010200000000000000002a0000", hex(encoded))
        assertEquals(codec.HEADER_BYTES, encoded.size)
        assertEquals(ack, codec.decode(encoded))
        assertFalse(com.edenpractical.comms.model.ReleaseCapabilities.CAN_SEND_MESSAGES)
    }

    @Test fun unicodeAndMultilineAreLosslessWithinLimits() {
        val value = "Hello 👋\nHej världen • مرحبا • 你好"
        val p = ProposedChatWireCodec.Packet.Text(2147483648L, value)
        assertEquals(p, codec.decode(codec.encode(p)))
        assertEquals(value, (codec.decode(codec.encode(p)) as ProposedChatWireCodec.Packet.Text).content)
        val cap = "🦊".repeat(codec.MAX_CODE_POINTS)
        val encoded = codec.encode(ProposedChatWireCodec.Packet.Text(9L, cap))
        assertEquals(codec.HEADER_BYTES + codec.MAX_UTF8_BYTES, encoded.size)
        assertEquals(cap, (codec.decode(encoded) as ProposedChatWireCodec.Packet.Text).content)
    }

    @Test fun rejectsBlankTooLongControlsAndInvalidSurrogatesOnEncode() {
        val bad = listOf("", " ", "\n\t", "x".repeat(281), "\u0000",
            "ok\u0001", "ok\u007f", "ok\u0085", "\uD800", "\uDC00")
        for (candidate in bad) {
            assertThrows("Rejected input length=${candidate.length}",
                ProposedChatWireException::class.java
            ) { codec.encode(ProposedChatWireCodec.Packet.Text(1L, candidate)) }
        }
        assertEquals("x".repeat(280), (codec.decode(codec.encode(
            ProposedChatWireCodec.Packet.Text(2L, "x".repeat(280))
        )) as ProposedChatWireCodec.Packet.Text).content)
    }

    @Test fun invalidMessageIdsAndUnknownVersionTypeOrFlagsFailClosed() {
        for (id in listOf(0L, -1L, Long.MIN_VALUE)) {
            assertThrows(ProposedChatWireException::class.java) {
                codec.encode(ProposedChatWireCodec.Packet.Text(id, "test"))
            }
            assertThrows(ProposedChatWireException::class.java) {
                codec.encode(ProposedChatWireCodec.Packet.Ack(id))
            }
        }
        val valid = codec.encode(ProposedChatWireCodec.Packet.Text(1, "ok"))
        val mutations = listOf(
            0 to 0, 4 to 2, 5 to 3, 5 to 255, 6 to 1, 7 to 128,
            15 to 255, 16 to 0, 16 to 3
        )
        for ((index, replacement) in mutations) {
            val altered = valid.copyOf()
            altered[index] = replacement.toByte()
            assertThrows("index=$index", ProposedChatWireException::class.java) {
                codec.decode(altered)
            }
        }
        val zeroId = valid.copyOf()
        for (i in 7..14) zeroId[i] = 0
        assertThrows(ProposedChatWireException::class.java) { codec.decode(zeroId) }
    }

    @Test fun byteLengthIsExactNoTruncationTrailingOrExtraAckPayload() {
        val text = codec.encode(ProposedChatWireCodec.Packet.Text(15L, "Hi"))
        val ack = codec.encode(ProposedChatWireCodec.Packet.Ack(15L))
        val invalid = listOf(
            byteArrayOf(), byteArrayOf(1), text.copyOf(codec.HEADER_BYTES - 1),
            text.copyOf(text.size - 1), text + byteArrayOf(0),
            ack + byteArrayOf(1),
            text.copyOf().also { it[15] = 0; it[16] = 0 },
            text.copyOf().also { it[15] = 0x7f; it[16] = 0x7f },
            ack.copyOf().also { it[16] = 1 },
            ByteArray(codec.HEADER_BYTES + codec.MAX_UTF8_BYTES + 1)
        )
        for (raw in invalid) {
            assertThrows(ProposedChatWireException::class.java) { codec.decode(raw) }
        }
    }

    @Test fun malformedUtf8AndControlBytesAreRejectedRatherThanReplaced() {
        val valid = codec.encode(ProposedChatWireCodec.Packet.Text(1L, "OK"))
        val invalidPayloads = listOf(
            byteArrayOf(0xC0.toByte(), 0xAF.toByte()), // overlong
            byteArrayOf(0xED.toByte(), 0xA0.toByte(), 0x80.toByte()), // surrogate
            byteArrayOf(0xF4.toByte(), 0x90.toByte(), 0x80.toByte(), 0x80.toByte()),
            byteArrayOf(0), byteArrayOf(0x7f), byteArrayOf(0xC2.toByte(), 0x85.toByte())
        )
        for (bytes in invalidPayloads) {
            val candidate = valid.copyOfRange(0, codec.HEADER_BYTES) + bytes
            candidate[15] = (bytes.size ushr 8).toByte()
            candidate[16] = bytes.size.toByte()
            assertThrows(ProposedChatWireException::class.java) { codec.decode(candidate) }
        }
    }

    @Test fun bothPacketTypesFitExistingOpaqueFrameWithoutSendingAnything() {
        val candidate = codec.encode(ProposedChatWireCodec.Packet.Text(19L, "test only"))
        val framed = FrameCodec.encode(candidate)
        assertArrayEquals(candidate,
            FrameCodec.readFrom(ByteArrayInputStream(framed)))
        assertEquals(ProposedChatWireCodec.Packet.Text(19L, "test only"),
            codec.decode(FrameCodec.readFrom(ByteArrayInputStream(framed))))
        assertFalse(com.edenpractical.comms.model.ReleaseCapabilities.CAN_CONNECT)
        assertFalse(com.edenpractical.comms.model.ReleaseCapabilities.CAN_SEND_MESSAGES)
        assertFalse(com.edenpractical.comms.model.ReleaseCapabilities.CAN_TRANSMIT_VOICE)
    }

    @Test fun noPeerIdentityOrAuthenticationProofIsEncodedInMessageShape() {
        val e = codec.encode(ProposedChatWireCodec.Packet.Text(Long.MAX_VALUE, "safe"))
        assertTrue(e.size <= codec.HEADER_BYTES + codec.MAX_UTF8_BYTES)
        assertEquals(ProposedChatWireCodec.Packet.Text(Long.MAX_VALUE, "safe"), codec.decode(e))
        // No ACK validation can be inferred from the fact the bytes parse.
        val fakeAck = codec.encode(ProposedChatWireCodec.Packet.Ack(Long.MAX_VALUE))
        assertEquals(ProposedChatWireCodec.Packet.Ack(Long.MAX_VALUE), codec.decode(fakeAck))
    }

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
