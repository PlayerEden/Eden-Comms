package com.edenpractical.comms.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugLocalPttFramesTest {
    @Test fun exactTwentyMillisecondPcmBounds() {
        assertEquals(20, DebugLocalPttFrames.FRAME_MILLIS)
        assertEquals(640, DebugLocalPttFrames.FRAME_BYTES)
        assertEquals(250, DebugLocalPttFrames.MAX_FRAMES)
        assertEquals(160_000, DebugLocalPttFrames.FRAME_BYTES *
            DebugLocalPttFrames.MAX_FRAMES)
    }

    @Test fun localCaptureBecomesOrderedPaddedPacketsAndReassembles() {
        val clip = ByteArray(1_602) { (it % 119).toByte() }
        val frames = requireNotNull(DebugLocalPttFrames.fromClip(clip))
        assertEquals(3, frames.queuedCount)
        val output = ArrayList<Byte>()
        repeat(3) { sequence ->
            val packet = requireNotNull(frames.poll())
            assertEquals(sequence, packet.sequence)
            assertEquals(640, packet.pcm.size)
            packet.pcm.forEach { output.add(it) }
            packet.pcm.fill(0)
        }
        assertNull(frames.poll())
        assertArrayEquals(clip, output.take(clip.size).toByteArray())
        assertTrue(output.drop(clip.size).all { it == 0.toByte() })
        frames.close()
    }

    @Test fun fullFiveSecondsIsExactlyTwoHundredFiftyPackets() {
        val frames = requireNotNull(DebugLocalPttFrames.fromClip(
            ByteArray(DebugLocalPttPolicy.MAX_PCM_BYTES) { 4 }
        ))
        assertEquals(250, frames.queuedCount)
        repeat(250) {
            val frame = requireNotNull(frames.poll())
            assertEquals(it, frame.sequence)
            frame.pcm.fill(0)
        }
        assertNull(frames.poll())
        frames.close()
    }

    @Test fun rejectMalformedSizeOutOfOrderReplayOverflowAndClosed() {
        val frames = requireNotNull(DebugLocalPttFrames.fromClip(
            ByteArray(DebugLocalPttPolicy.MIN_PCM_BYTES)
        ))
        assertFalse(frames.offer(5, ByteArray(640)))
        assertFalse(frames.offer(3, ByteArray(638)))
        assertTrue(frames.offer(3, ByteArray(640)))
        assertFalse(frames.offer(3, ByteArray(640)))
        assertFalse(frames.offer(5, ByteArray(640)))
        frames.close()
        assertEquals(0, frames.queuedCount)
        assertNull(frames.poll())
        assertFalse(frames.offer(4, ByteArray(640)))
        for (size in listOf(0, 1, 1_599, 1_601, 160_001, 160_002)) {
            assertNull(DebugLocalPttFrames.fromClip(ByteArray(size)))
        }
        val max = requireNotNull(DebugLocalPttFrames.fromClip(
            ByteArray(DebugLocalPttPolicy.MAX_PCM_BYTES)
        ))
        assertFalse(max.offer(250, ByteArray(640)))
        max.close()
    }

    @Test fun queueCopiesFramesAndWipesPendingDataOnClose() {
        val buffer = requireNotNull(DebugLocalPttFrames.fromClip(ByteArray(1_600)))
        // A record is copied before the original buffer is discarded.
        val first = requireNotNull(buffer.poll())
        assertEquals(640, first.pcm.size)
        first.pcm.fill(0)
        assertTrue(first.pcm.all { it == 0.toByte() })
        buffer.close()
        assertNull(buffer.poll())

        val captured = ByteArray(1_600) { 7 }
        val isolated = requireNotNull(DebugLocalPttFrames.fromClip(captured))
        captured.fill(0)
        val packet = requireNotNull(isolated.poll())
        assertTrue(packet.pcm.all { it == 7.toByte() })
        packet.pcm.fill(0)
        isolated.close()
    }
}
