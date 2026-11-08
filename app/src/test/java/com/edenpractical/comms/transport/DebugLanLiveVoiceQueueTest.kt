package com.edenpractical.comms.transport

import com.edenpractical.comms.protocol.DebugLanVoiceWireCodec
import org.junit.Assert.*
import org.junit.Test

class DebugLanLiveVoiceQueueTest {
    private fun pcm(n: Int) = ByteArray(DebugLanVoiceWireCodec.PCM_BYTES) { n.toByte() }

    @Test fun eachFrameCanLeaveWhileMicIsStillCapturing() {
        val queue = DebugLanLiveVoiceQueue()
        val first = pcm(7)
        assertTrue(queue.offer(first))
        first.fill(0)
        val outgoing = queue.poll()!!
        assertEquals(DebugLanVoiceWireCodec.PCM_BYTES, outgoing.size)
        assertEquals(7, outgoing[0].toInt())
        outgoing.fill(0)
        // No release/finish was needed before the writer could transmit.
        assertFalse(queue.isFinishedAndEmpty())
        queue.finish()
        assertTrue(queue.isFinishedAndEmpty())
        assertFalse(queue.offer(pcm(2)))
    }

    @Test fun overflowStopsTurnInsteadOfGrowingOrDroppingUnsequencedAudio() {
        val queue = DebugLanLiveVoiceQueue()
        repeat(DebugLanLiveVoiceQueue.MAX_PENDING_FRAMES) {
            assertTrue(queue.offer(pcm(it)))
        }
        assertFalse(queue.offer(pcm(99)))
        assertTrue(queue.overran())
        repeat(DebugLanLiveVoiceQueue.MAX_PENDING_FRAMES) {
            val frame = queue.poll()!!
            assertEquals(it.toByte(), frame[0])
            frame.fill(0)
        }
        assertTrue(queue.isFinishedAndEmpty())
        assertFalse(queue.offer(pcm(1)))
    }

    @Test fun cancelledTurnDropsPendingPcmAndCannotRestart() {
        val queue = DebugLanLiveVoiceQueue()
        assertTrue(queue.offer(pcm(3)))
        queue.cancel()
        assertNull(queue.poll())
        assertTrue(queue.isFinishedAndEmpty())
        assertFalse(queue.offer(pcm(4)))
        queue.clear()
    }

    @Test fun malformedFrameRejectedWithoutCreatingAnAudioChunk() {
        val queue = DebugLanLiveVoiceQueue()
        assertFalse(queue.offer(ByteArray(2)))
        assertFalse(queue.isFinishedAndEmpty())
        queue.finish()
        assertNull(queue.poll())
    }

    @Test fun encodedWireStillRequiresBoundedSameTurnDataAndFinalEnd() {
        val queue = DebugLanLiveVoiceQueue()
        assertTrue(queue.offer(pcm(42)))
        val payload = queue.poll()!!
        val start = DebugLanVoiceWireCodec.start(28L)
        val data = DebugLanVoiceWireCodec.data(28L, 0, payload)
        val end = DebugLanVoiceWireCodec.end(28L, 1)
        val assembled = com.edenpractical.comms.protocol.DebugLanVoiceAssembler()
        assertNull(assembled.accept(start))
        assertNull(assembled.accept(data))
        val result = assembled.accept(end)!!
        assertEquals(42.toByte(), result[0])
        payload.fill(0)
        data.clear()
        result.fill(0)
        assembled.clear()
        queue.clear()
    }
}
