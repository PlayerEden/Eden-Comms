package com.edenpractical.comms.pairing

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DebugTapVoiceSendGateTest {
    @Test fun cancelBeforeCallbackMakesFutureAudioSendImpossible() {
        val gate = DebugTapVoiceSendGate()
        val sends = AtomicInteger(0)
        assertTrue(gate.cancel())
        assertTrue(gate.isCancelled)
        val result = gate.submitIfActive { sends.incrementAndGet() }
        assertNull(result)
        assertEquals(0, sends.get())
        assertFalse(gate.cancel())
    }

    @Test fun onlyOneCallbackMayQueueEvenIfInvokedTwice() {
        val gate = DebugTapVoiceSendGate()
        val sends = AtomicInteger(0)
        assertEquals(1, gate.submitIfActive { sends.incrementAndGet() })
        assertNull(gate.submitIfActive { sends.incrementAndGet() })
        assertEquals(1, sends.get())
        // Already-queued output cannot be retroactively cancelled.
        assertFalse(gate.cancel())
        assertFalse(gate.isCancelled)
    }

    @Test fun concurrentCancelWinsBeforeCallbackWithoutQueueing() {
        val gate = DebugTapVoiceSendGate()
        val worker = Executors.newSingleThreadExecutor()
        try {
            assertTrue(gate.cancel())
            val sent = worker.submit<Int?> {
                gate.submitIfActive { 1 }
            }
            assertNull(sent.get(2, TimeUnit.SECONDS))
        } finally { worker.shutdownNow() }
    }

    @Test fun cancellationCannotInterleaveWithTheCommittedSendHandoff() {
        val gate = DebugTapVoiceSendGate()
        val pool = Executors.newFixedThreadPool(2)
        val inSubmit = CountDownLatch(1)
        val letSubmitFinish = CountDownLatch(1)
        try {
            val send = pool.submit<Int?> {
                gate.submitIfActive {
                    inSubmit.countDown()
                    assertTrue(letSubmitFinish.await(2, TimeUnit.SECONDS))
                    42
                }
            }
            assertTrue(inSubmit.await(2, TimeUnit.SECONDS))
            val cancellation = pool.submit<Boolean> { gate.cancel() }
            letSubmitFinish.countDown()
            assertEquals(42, send.get(2, TimeUnit.SECONDS))
            assertFalse(cancellation.get(2, TimeUnit.SECONDS))
        } finally {
            letSubmitFinish.countDown()
            pool.shutdownNow()
        }
    }

    @Test fun failedSubmissionStillCannotBeResent() {
        val gate = DebugTapVoiceSendGate()
        try {
            gate.submitIfActive<Int> { throw IllegalStateException("Simulated writer rejection") }
            fail("Expected simulated rejection")
        } catch (_: IllegalStateException) {
            // Real caller releases the unstarted floor; no automatic retries.
        }
        assertNull(gate.submitIfActive { 5 })
        assertFalse(gate.cancel())
    }
}
