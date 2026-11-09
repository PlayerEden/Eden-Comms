package com.edenpractical.comms.pairing

import com.edenpractical.comms.protocol.DebugLanVoiceWireCodec
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class DebugLiveVoiceStopBoundariesTest {
    private fun pcm(value: Int) =
        ByteArray(DebugLanVoiceWireCodec.PCM_BYTES) { value.toByte() }

    @Test fun microphoneReleaseBeforeReadCompletionNeverHandsOffLatePcm() {
        val gate = DebugLiveMicFrameGate()
        val writes = AtomicInteger(0)
        gate.stop()
        assertFalse(gate.offerIfActive(pcm(9)) {
            writes.incrementAndGet()
            true
        })
        assertEquals(0, writes.get())
    }

    @Test fun microphoneStopCannotSlipInsideOneCommittedFrameHandoff() {
        val gate = DebugLiveMicFrameGate()
        val enteredOffer = CountDownLatch(1)
        val releaseOffer = CountDownLatch(1)
        val stopFinished = CountDownLatch(1)
        val sent = AtomicInteger(0)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val first = pool.submit<Boolean> {
                gate.offerIfActive(pcm(11)) {
                    enteredOffer.countDown()
                    assertTrue(releaseOffer.await(3, TimeUnit.SECONDS))
                    sent.incrementAndGet()
                    true
                }
            }
            assertTrue(enteredOffer.await(2, TimeUnit.SECONDS))
            val stop = pool.submit {
                gate.stop()
                stopFinished.countDown()
            }
            releaseOffer.countDown()
            assertTrue(first.get(3, TimeUnit.SECONDS))
            stop.get(3, TimeUnit.SECONDS)
            assertEquals(0L, stopFinished.count)
            assertEquals(1, sent.get())
            // A sample that was already committed cannot be recalled, but
            // NO subsequent frame can be handed off after stop returns.
            assertFalse(gate.offerIfActive(pcm(12)) {
                sent.incrementAndGet()
                true
            })
            assertEquals(1, sent.get())
        } finally {
            releaseOffer.countDown()
            pool.shutdownNow()
        }
    }

    @Test fun microphoneStopBeforeOfferedCallbackWinsEvenOnAnotherThread() {
        val gate = DebugLiveMicFrameGate()
        val pool = Executors.newSingleThreadExecutor()
        val writes = AtomicInteger()
        try {
            gate.stop()
            val future = pool.submit<Boolean> {
                gate.offerIfActive(pcm(6)) {
                    writes.incrementAndGet()
                    true
                }
            }
            assertFalse(future.get(2, TimeUnit.SECONDS))
            assertEquals(0, writes.get())
        } finally {
            pool.shutdownNow()
        }
    }

    @Test fun speakerBufferCopiesPcmAndCancelDropsQueuedFrames() {
        val buffer = DebugLivePlaybackBuffer()
        val original = pcm(42)
        assertTrue(buffer.offer(original))
        original.fill(0)
        val got = buffer.poll(0, TimeUnit.MILLISECONDS)!!
        assertEquals(42.toByte(), got[0])
        got.fill(0)

        assertTrue(buffer.offer(pcm(7)))
        buffer.cancel()
        assertTrue(buffer.isCancelled())
        assertTrue(buffer.isEnded())
        assertTrue(buffer.isEmpty())
        assertNull(buffer.poll(0, TimeUnit.MILLISECONDS))
        assertFalse(buffer.offer(pcm(8)))
        buffer.cancel() // idempotent, no PCM resurrected
        assertTrue(buffer.isEmpty())
    }

    @Test fun speakerFullBufferFailsClosedAndDoesNotRetainOrExtendTurn() {
        val buffer = DebugLivePlaybackBuffer()
        repeat(DebugLivePlaybackBuffer.MAX_PENDING_FRAMES) { n ->
            assertTrue(buffer.offer(pcm(n)))
        }
        assertFalse(buffer.offer(pcm(99)))
        assertTrue(buffer.isCancelled())
        assertTrue(buffer.isEmpty())
        assertNull(buffer.poll(0, TimeUnit.MILLISECONDS))
        assertFalse(buffer.offer(pcm(100)))
    }

    @Test fun speakerNormalEndDrainsTailWithoutAcceptingMorePcm() {
        val buffer = DebugLivePlaybackBuffer()
        assertTrue(buffer.offer(pcm(13)))
        buffer.finish()
        assertTrue(buffer.isEnded())
        assertFalse(buffer.offer(pcm(14)))
        val result = buffer.poll(0, TimeUnit.MILLISECONDS)!!
        assertEquals(13.toByte(), result[0])
        result.fill(0)
        assertTrue(buffer.isEmpty())
    }

    @Test fun speakerCancelAndRacingOfferCannotLeaveLateBufferedAudio() {
        val buffer = DebugLivePlaybackBuffer()
        val pool = Executors.newFixedThreadPool(2)
        val done = AtomicBoolean(false)
        try {
            val sender = pool.submit {
                repeat(250) { n ->
                    if (done.get()) return@submit
                    buffer.offer(pcm(n))
                }
            }
            val cancel = pool.submit {
                buffer.cancel()
                done.set(true)
            }
            sender.get(3, TimeUnit.SECONDS)
            cancel.get(3, TimeUnit.SECONDS)
            assertTrue(buffer.isCancelled())
            assertTrue(buffer.isEmpty())
            assertFalse(buffer.offer(pcm(1)))
            assertNull(buffer.poll(0, TimeUnit.MILLISECONDS))
        } finally {
            pool.shutdownNow()
        }
    }
}
