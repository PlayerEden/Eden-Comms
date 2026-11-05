package com.edenpractical.comms.protocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TestPacketCodecTest {
    private val codec = TestPacketCodec
    private val binding = ByteArray(32) { (it + 1).toByte() }

    @Test fun allPresetPhrasesRoundTripAcrossBoundedFrames() {
        codec.phrases.forEach { phrase ->
            val stream = ByteArrayOutputStream()
            codec.write(stream, codec.preset(phrase.id))
            assertEquals(codec.preset(phrase.id), codec.read(
                ByteArrayInputStream(stream.toByteArray())))
            assertEquals(phrase.label, codec.phrase(phrase.id))
        }
    }

    @Test fun readyHeartbeatAndCloseRoundTrip() {
        listOf(codec.ready(), codec.heartbeat(), codec.close(),
            codec.approve(binding), codec.decline(binding)).forEach { packet ->
            assertEquals(packet, codec.decode(codec.encode(packet)))
        }
    }

    @Test fun multipleFramesAreReadInOrder() {
        val stream = ByteArrayOutputStream()
        codec.write(stream, codec.ready())
        codec.write(stream, codec.preset(2))
        codec.write(stream, codec.heartbeat())
        val input = ByteArrayInputStream(stream.toByteArray())
        assertEquals(codec.ready(), codec.read(input))
        assertEquals(codec.preset(2), codec.read(input))
        assertEquals(codec.heartbeat(), codec.read(input))
    }

    @Test fun malformedVersionAndUnknownMessagesAreRejected() {
        listOf(
            byteArrayOf(),
            byteArrayOf(2, 2, 1),
            byteArrayOf(1, 9, 0),
            byteArrayOf(1, 2, 0),
            byteArrayOf(1, 2, 99),
            byteArrayOf(1, 3, 1),
            byteArrayOf(1, 1, 0, 0)
        ).forEach { invalid ->
            assertThrows(TestPacketException::class.java) { codec.decode(invalid) }
        }
    }

    @Test fun everyUnsignedByteValueOutsideFourPresetIdsFailsClosed() {
        // Exhaust the entire 8-bit phrase field rather than sampling errors.
        for (id in 0..255) {
            val raw = byteArrayOf(1, 2, id.toByte())
            if (id in 1..4) {
                assertEquals(codec.preset(id), codec.decode(raw))
            } else {
                assertThrows(TestPacketException::class.java) {
                    codec.decode(raw)
                }
            }
        }
    }

    @Test fun everyNonzeroControlPayloadIsRejected() {
        for (kind in listOf(1, 3, 4, 5, 6)) {
            for (id in 1..255) {
                val raw = byteArrayOf(1, kind.toByte(), id.toByte())
                assertThrows(TestPacketException::class.java) {
                    codec.decode(raw)
                }
            }
        }
    }

    @Test fun approveDeclineRequireExactSessionBound35ByteFixtures() {
        val approve = codec.encode(codec.approve(binding))
        val decline = codec.encode(codec.decline(binding))
        assertArrayEquals(byteArrayOf(1, 5, 0) + binding, approve)
        assertArrayEquals(byteArrayOf(1, 6, 0) + binding, decline)
        assertEquals(35, approve.size)
        assertEquals(codec.approve(binding), codec.decode(approve))
        assertEquals(codec.decline(binding), codec.decode(decline))
        assertThrows(TestPacketException::class.java) {
            codec.decode(byteArrayOf(1, 5, 0)) // old generic approval forbidden
        }
        assertThrows(TestPacketException::class.java) {
            codec.decode(byteArrayOf(1, 6, 0)) // old generic decline forbidden
        }
        assertThrows(IllegalArgumentException::class.java) {
            codec.approve(ByteArray(32))
        }
        assertThrows(IllegalArgumentException::class.java) {
            codec.decline(ByteArray(31))
        }
        assertThrows(IllegalArgumentException::class.java) {
            codec.encode(TestPacketCodec.Packet(TestPacketCodec.Type.APPROVE, 1,
                binding.toList()))
        }
        assertThrows(IllegalArgumentException::class.java) {
            codec.encode(TestPacketCodec.Packet(TestPacketCodec.Type.READY, 0,
                binding.toList()))
        }
    }

    @Test fun wrongBoundedApprovalLengthsAndCorruptionFailClosed() {
        val approved = codec.encode(codec.approve(binding))
        for (n in listOf(0, 1, 2, 3, 4, 33, 34, 36, 37)) {
            assertThrows(TestPacketException::class.java) {
                codec.decode(approved.copyOf(n))
            }
        }
        val invalid = listOf(
            approved.copyOf().also { it[2] = 1 },
            approved.copyOf().also { it[0] = 2 },
            approved.copyOf().also { it[1] = 9 },
            approved.copyOf().also { for (i in 3..34) it[i] = 0 }
        )
        invalid.forEach { bytes ->
            assertThrows(TestPacketException::class.java) { codec.decode(bytes) }
        }
        val modified = approved.copyOf().also { it[19] = (it[19].toInt() xor 1).toByte() }
        // The packet remains parseable; live session binding must reject it.
        assertEquals(35, codec.encode(codec.decode(modified)).size)
        assertThrows(TestPacketException::class.java) {
            codec.decode(byteArrayOf(1, 5, 0) + binding + byteArrayOf(1))
        }
    }

    @Test fun userSuppliedOrUnknownTextCannotBeEncoded() {
        assertThrows(IllegalArgumentException::class.java) { codec.preset(99) }
        assertThrows(IllegalArgumentException::class.java) {
            codec.encode(TestPacketCodec.Packet(TestPacketCodec.Type.READY, 7))
        }
    }

    @Test fun truncatedFrameFailsClosed() {
        val stream = ByteArrayOutputStream()
        codec.write(stream, codec.preset(1))
        val raw = stream.toByteArray()
        assertThrows(Exception::class.java) {
            codec.read(ByteArrayInputStream(raw.copyOf(raw.size - 1)))
        }
    }
}
