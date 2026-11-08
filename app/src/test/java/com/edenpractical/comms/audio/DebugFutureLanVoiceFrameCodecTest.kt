package com.edenpractical.comms.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugFutureLanVoiceFrameCodecTest {
    private val codec = DebugFutureLanVoiceFrameCodec

    private fun malformed(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Malformed synthetic frame unexpectedly accepted")
        } catch (_: IllegalArgumentException) {
            // Expected; no decoded audio must escape an invalid envelope.
        }
    }

    @Test fun fixedTwentyMillisecondFrameAndMaximumUtterance() {
        assertEquals(20, DebugLocalPttFrames.FRAME_MILLIS)
        assertEquals(640, codec.PCM_BYTES)
        assertEquals(250, codec.MAX_FRAMES)
        assertEquals(18, codec.HEADER_BYTES)
        assertEquals(658, codec.PACKET_BYTES)
        assertEquals(5_000,
            codec.MAX_FRAMES * DebugLocalPttFrames.FRAME_MILLIS)
    }

    @Test fun exactFrameRoundTripDoesNotAliasCallerInput() {
        val original = ByteArray(codec.PCM_BYTES) { (it % 127).toByte() }
        val expected = original.copyOf()
        val packet = codec.encode(249, original)
        original.fill(0)
        assertEquals(codec.PACKET_BYTES, packet.size)
        val decoded = codec.decode(packet)
        assertEquals(249L, decoded.sequence)
        assertArrayEquals(expected, decoded.pcm)
        packet.fill(0)
        assertArrayEquals(expected, decoded.pcm)
        decoded.close()
        assertTrue(decoded.pcm.all { it == 0.toByte() })
        expected.fill(0)
    }

    @Test fun malformedSizesSequenceAndHeaderFieldsAreRejected() {
        malformed { codec.encode(-1L, ByteArray(640)) }
        malformed { codec.encode(250L, ByteArray(640)) }
        malformed { codec.encode(Long.MAX_VALUE, ByteArray(640)) }
        malformed { codec.encode(0, ByteArray(639)) }
        malformed { codec.encode(0, ByteArray(641)) }
        val good = codec.encode(0, ByteArray(640))
        malformed { codec.decode(good.copyOf(good.size - 1)) }
        malformed { codec.decode(good.copyOf(good.size + 1)) }
        for (index in listOf(0, 4, 5, 6, 7, 16, 17)) {
            val modified = good.copyOf()
            modified[index] = (modified[index].toInt() xor 0x40).toByte()
            malformed { codec.decode(modified) }
            modified.fill(0)
        }
        val negative = good.copyOf()
        ByteBuffer.wrap(negative).order(ByteOrder.BIG_ENDIAN).putLong(8, -1L)
        malformed { codec.decode(negative) }
        val overflow = good.copyOf()
        ByteBuffer.wrap(overflow).order(ByteOrder.BIG_ENDIAN).putLong(8, 250L)
        malformed { codec.decode(overflow) }
        negative.fill(0)
        overflow.fill(0)
        good.fill(0)
    }

    @Test fun independentSyntheticSourceAndReceiverExchangeOrderedLocalPcm() {
        // No devices, TLS, socket, radio, live voice, or user content involved.
        val clip = ByteArray(1_920) { (it % 61).toByte() }
        val frames = requireNotNull(DebugLocalPttFrames.fromClip(clip))
        val receiver = DebugFutureLanVoiceSequenceRehearsal()
        repeat(3) { ordinal ->
            val source = requireNotNull(frames.poll())
            val bytes = codec.encode(ordinal.toLong(), source.pcm)
            val received = requireNotNull(receiver.accept(bytes))
            assertEquals(ordinal.toLong(), received.sequence)
            assertArrayEquals(source.pcm, received.pcm)
            received.close()
            bytes.fill(0)
            source.pcm.fill(0)
        }
        assertEquals(3L, receiver.framesAccepted)
        assertNull(frames.poll())
        assertFalse(receiver.isClosed)
        receiver.close()
        assertTrue(receiver.isClosed)
        frames.close()
        clip.fill(0)
    }

    @Test fun gapReplayAndMalformedDataCloseTheSyntheticReceiver() {
        val source = ByteArray(640) { 4 }
        val zero = codec.encode(0L, source)
        val one = codec.encode(1L, source)
        val skip = DebugFutureLanVoiceSequenceRehearsal()
        assertNull(skip.accept(one))
        assertTrue(skip.isClosed)
        assertNull(skip.accept(zero))
        val duplicate = DebugFutureLanVoiceSequenceRehearsal()
        val first = duplicate.accept(zero)
        assertNotNull(first)
        first!!.close()
        assertNull(duplicate.accept(zero))
        assertTrue(duplicate.isClosed)
        assertNull(duplicate.accept(one))
        val corrupt = zero.copyOf()
        corrupt[4] = 99
        val invalid = DebugFutureLanVoiceSequenceRehearsal()
        assertNull(invalid.accept(corrupt))
        assertTrue(invalid.isClosed)
        zero.fill(0)
        one.fill(0)
        corrupt.fill(0)
        source.fill(0)
    }

    @Test fun arbitraryOldFrameCanEnterFreshSyntheticReceiverSoNoSessionTrustIsClaimed() {
        // Deliberately demonstrate the missing cryptographic binding. Future
        // review must reject this cross-session replay before any real audio.
        val stale = codec.encode(0, ByteArray(640) { 9 })
        val newTurn = DebugFutureLanVoiceSequenceRehearsal()
        val decoded = newTurn.accept(stale)
        assertNotNull(decoded)
        decoded!!.close()
        newTurn.close()
        stale.fill(0)
    }
}
