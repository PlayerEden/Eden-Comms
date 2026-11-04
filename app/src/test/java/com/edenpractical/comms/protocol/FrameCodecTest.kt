package com.edenpractical.comms.protocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.random.Random

class FrameCodecTest {
    @Test fun roundTripHandlesBinaryBytes() {
        val payload = byteArrayOf(0, 1, 0x7F, -1, 0, 0x40)
        assertArrayEquals(payload, FrameCodec.readFrom(ByteArrayInputStream(FrameCodec.encode(payload))))
    }

    @Test fun frameHeaderHasNoPeerIdentity() {
        val encoded = FrameCodec.encode(byteArrayOf(9))
        assertEquals(11, encoded.size)
        assertArrayEquals("ECOM".toByteArray(Charsets.US_ASCII), encoded.copyOfRange(0, 4))
        assertEquals(1, encoded[4].toInt())
        assertEquals(0, encoded[5].toInt())
        assertArrayEquals(byteArrayOf(0, 0, 0, 1), encoded.copyOfRange(6, 10))
    }

    @Test fun twoSequentialFramesRemainDistinct() {
        val stream = ByteArrayOutputStream()
        stream.write(FrameCodec.encode(byteArrayOf(10, 11)))
        stream.write(FrameCodec.encode(byteArrayOf(12)))
        val input = ByteArrayInputStream(stream.toByteArray())
        assertArrayEquals(byteArrayOf(10, 11), FrameCodec.readFrom(input))
        assertArrayEquals(byteArrayOf(12), FrameCodec.readFrom(input))
        assertEquals(0, input.available())
    }

    @Test fun maxPayloadBoundaryAccepted() {
        val payload = ByteArray(FrameCodec.MAX_PAYLOAD_BYTES) { i -> i.toByte() }
        assertArrayEquals(payload, FrameCodec.readFrom(ByteArrayInputStream(FrameCodec.encode(payload))))
    }

    @Test fun zeroLengthPayloadRefusedBeforeWriting() {
        assertThrows(IllegalArgumentException::class.java) { FrameCodec.encode(byteArrayOf()) }
    }

    @Test fun oversizedPayloadRefusedBeforeWriting() {
        assertThrows(IllegalArgumentException::class.java) {
            FrameCodec.encode(ByteArray(FrameCodec.MAX_PAYLOAD_BYTES + 1))
        }
    }

    @Test fun wrongMagicRejected() {
        val data = FrameCodec.encode(byteArrayOf(5))
        data[0] = 0
        assertThrows(FrameFormatException::class.java) {
            FrameCodec.readFrom(ByteArrayInputStream(data))
        }
    }

    @Test fun unknownVersionRejected() {
        val data = FrameCodec.encode(byteArrayOf(5))
        data[4] = 2
        assertThrows(FrameFormatException::class.java) {
            FrameCodec.readFrom(ByteArrayInputStream(data))
        }
    }

    @Test fun unsupportedFlagsRejected() {
        val data = FrameCodec.encode(byteArrayOf(5))
        data[5] = 1
        assertThrows(FrameFormatException::class.java) {
            FrameCodec.readFrom(ByteArrayInputStream(data))
        }
    }

    @Test fun invalidLengthRejectedBeforeAllocation() {
        val data = FrameCodec.encode(byteArrayOf(5))
        // Large untrusted integer: never try to allocate this size.
        data[6] = 0x7F
        data[7] = -1
        data[8] = -1
        data[9] = -1
        assertThrows(FrameFormatException::class.java) {
            FrameCodec.readFrom(ByteArrayInputStream(data))
        }
    }

    @Test fun negativeLengthRejectedBeforeAllocation() {
        val data = FrameCodec.encode(byteArrayOf(5))
        for (i in 6..9) data[i] = -1
        assertThrows(FrameFormatException::class.java) {
            FrameCodec.readFrom(ByteArrayInputStream(data))
        }
    }

    @Test fun emptyBodyLengthRejectedBeforeAllocation() {
        val data = FrameCodec.encode(byteArrayOf(5))
        for (i in 6..9) data[i] = 0
        assertThrows(FrameFormatException::class.java) {
            FrameCodec.readFrom(ByteArrayInputStream(data))
        }
    }

    @Test fun truncatedHeaderRejected() {
        for (length in 0 until FrameCodec.HEADER_BYTES) {
            val truncated = FrameCodec.encode(byteArrayOf(1)).copyOf(length)
            assertThrows(EOFException::class.java) {
                FrameCodec.readFrom(ByteArrayInputStream(truncated))
            }
        }
    }

    @Test fun truncatedPayloadRejected() {
        val frame = FrameCodec.encode(byteArrayOf(1, 2, 3, 4))
        assertThrows(EOFException::class.java) {
            FrameCodec.readFrom(ByteArrayInputStream(frame.copyOf(frame.size - 1)))
        }
    }

    @Test fun deterministicMalformedInputFuzzNeverAllocatesUnboundedMemory() {
        val rng = Random(0xEC0A)
        repeat(1_000) {
            val raw = ByteArray(rng.nextInt(0, 48)).also { rng.nextBytes(it) }
            try {
                val decoded = FrameCodec.readFrom(ByteArrayInputStream(raw))
                assertTrue(decoded.size in 1..FrameCodec.MAX_PAYLOAD_BYTES)
            } catch (_: IOException) {
                // Expected: random bytes usually do not represent a valid frame.
            }
        }
    }
}
