package com.edenpractical.comms.bluetooth

import com.edenpractical.comms.model.ReleaseCapabilities
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugBluetoothConsentDecisionTest {
    @Test fun approvalRequiresAnExplicitTapAndCannotBeRepeated() {
        val prompt = DebugBluetoothConsentDecision()
        assertTrue(prompt.approve())
        assertFalse(prompt.approve())
        assertFalse(prompt.decline())
        assertEquals(
            DebugBluetoothConsentDecision.Decision.APPROVE,
            prompt.await(100)
        )
        assertFalse(ReleaseCapabilities.CAN_SEND_MESSAGES)
        assertFalse(ReleaseCapabilities.CAN_CONNECT)
        assertFalse(ReleaseCapabilities.CAN_TRANSMIT_VOICE)
    }

    @Test fun explicitDeclineIsNotApproval() {
        val prompt = DebugBluetoothConsentDecision()
        assertTrue(prompt.decline())
        assertEquals(
            DebugBluetoothConsentDecision.Decision.DECLINE,
            prompt.await(100)
        )
        assertFalse(prompt.approve())
    }

    @Test fun noUserResponseTimesOutWithoutAutomaticApproval() {
        val prompt = DebugBluetoothConsentDecision()
        assertEquals(
            DebugBluetoothConsentDecision.Decision.TIMEOUT,
            prompt.await(1)
        )
        prompt.close()
        assertFalse(prompt.approve())
    }

    @Test fun cancellationOverridesAnEarlierTapAndWakesWaiter() {
        val prompt = DebugBluetoothConsentDecision()
        assertTrue(prompt.approve())
        prompt.close()
        assertEquals(
            DebugBluetoothConsentDecision.Decision.CANCELED,
            prompt.await(100)
        )
        assertFalse(prompt.approve())
    }

    @Test fun backgroundClosureUnblocksWaitingWorker() {
        val prompt = DebugBluetoothConsentDecision()
        val entered = CountDownLatch(1)
        val ended = CountDownLatch(1)
        var result: DebugBluetoothConsentDecision.Decision? = null
        val worker = Thread {
            entered.countDown()
            result = prompt.await(5_000)
            ended.countDown()
        }
        worker.start()
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        prompt.close()
        assertTrue(ended.await(2, TimeUnit.SECONDS))
        worker.join(2_000)
        assertEquals(DebugBluetoothConsentDecision.Decision.CANCELED, result)
    }

    @Test fun unboundedConsentWaitIsProhibited() {
        val prompt = DebugBluetoothConsentDecision()
        assertThrows(IllegalArgumentException::class.java) {
            prompt.await(0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            prompt.await(60_000)
        }
    }

    @Test fun aTimedOutWaitCanNeverAcceptAStaleApprovalOrDecline() {
        val prompt = DebugBluetoothConsentDecision()
        assertEquals(DebugBluetoothConsentDecision.Decision.TIMEOUT, prompt.await(1))
        assertFalse(prompt.approve())
        assertFalse(prompt.decline())
        // A different worker cannot reopen an already consumed prompt.
        assertEquals(DebugBluetoothConsentDecision.Decision.CANCELED, prompt.await(100))
    }

    @Test fun aLateTapFailsEvenIfWaitingThreadHasNotYetResumed() {
        val now = AtomicLong(2_000_000_000L)
        val enteredWait = CountDownLatch(1)
        val returned = CountDownLatch(1)
        val answer = AtomicReference<DebugBluetoothConsentDecision.Decision?>(null)
        val prompt = DebugBluetoothConsentDecision {
            val sampled = now.get()
            enteredWait.countDown()
            sampled
        }
        val worker = Thread {
            try {
                answer.set(prompt.await(5_000))
            } finally {
                returned.countDown()
            }
        }
        worker.start()
        try {
            assertTrue(enteredWait.await(2, TimeUnit.SECONDS))
            // The native/user callback may arrive after the 5-second
            // deadline while the waiting JVM thread is unscheduled.
            now.addAndGet(TimeUnit.MILLISECONDS.toNanos(5_000))
            assertFalse(prompt.approve())
            assertFalse(prompt.decline())
            assertTrue(returned.await(2, TimeUnit.SECONDS))
            assertEquals(DebugBluetoothConsentDecision.Decision.TIMEOUT, answer.get())
        } finally {
            prompt.close()
            worker.interrupt()
            worker.join(2_000)
        }
    }

    @Test fun aTapBeforeMonotonicDeadlineStillWorksOnce() {
        val now = AtomicLong(8_000_000_000L)
        val enteredWait = CountDownLatch(1)
        val answer = AtomicReference<DebugBluetoothConsentDecision.Decision?>(null)
        val done = CountDownLatch(1)
        val prompt = DebugBluetoothConsentDecision {
            val sampled = now.get()
            enteredWait.countDown()
            sampled
        }
        val worker = Thread {
            try {
                answer.set(prompt.await(5_000))
            } finally {
                done.countDown()
            }
        }
        worker.start()
        try {
            assertTrue(enteredWait.await(2, TimeUnit.SECONDS))
            now.addAndGet(TimeUnit.MILLISECONDS.toNanos(5_000) - 1L)
            assertTrue(prompt.approve())
            assertFalse(prompt.approve())
            assertTrue(done.await(2, TimeUnit.SECONDS))
            assertEquals(DebugBluetoothConsentDecision.Decision.APPROVE, answer.get())
        } finally {
            prompt.close()
            worker.interrupt()
            worker.join(2_000)
        }
    }

    @Test fun elapsedClockRollbackFailsClosedAndWakesWorker() {
        val now = AtomicLong(10_000L)
        val started = CountDownLatch(1)
        val done = CountDownLatch(1)
        val result = AtomicReference<DebugBluetoothConsentDecision.Decision?>(null)
        val prompt = DebugBluetoothConsentDecision {
            val sampled = now.get()
            started.countDown()
            sampled
        }
        val worker = Thread {
            try {
                result.set(prompt.await(5_000))
            } finally {
                done.countDown()
            }
        }
        worker.start()
        try {
            assertTrue(started.await(2, TimeUnit.SECONDS))
            now.set(9_999L)
            assertFalse(prompt.approve())
            assertTrue(done.await(2, TimeUnit.SECONDS))
            assertEquals(DebugBluetoothConsentDecision.Decision.TIMEOUT, result.get())
        } finally {
            prompt.close()
            worker.interrupt()
            worker.join(2_000)
        }
    }

    @Test fun interruptionCancelsThePromptAndRejectsAnyLateTap() {
        val started = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val interrupted = AtomicBoolean(false)
        val prompt = DebugBluetoothConsentDecision {
            started.countDown()
            System.nanoTime()
        }
        val worker = Thread {
            try {
                prompt.await(5_000)
            } catch (_: InterruptedException) {
                interrupted.set(true)
            } finally {
                finished.countDown()
            }
        }
        worker.start()
        try {
            assertTrue(started.await(2, TimeUnit.SECONDS))
            worker.interrupt()
            assertTrue(finished.await(2, TimeUnit.SECONDS))
            assertTrue(interrupted.get())
            assertFalse(prompt.approve())
            assertFalse(prompt.decline())
        } finally {
            prompt.close()
            worker.interrupt()
            worker.join(2_000)
        }
    }

    @Test fun aSecondWaiterCannotReuseAnActivePrompt() {
        val started = CountDownLatch(1)
        val ended = CountDownLatch(1)
        val answer = AtomicReference<DebugBluetoothConsentDecision.Decision?>(null)
        val prompt = DebugBluetoothConsentDecision {
            started.countDown()
            System.nanoTime()
        }
        val first = Thread {
            try { answer.set(prompt.await(5_000)) } finally { ended.countDown() }
        }
        first.start()
        try {
            assertTrue(started.await(2, TimeUnit.SECONDS))
            assertEquals(DebugBluetoothConsentDecision.Decision.CANCELED, prompt.await(100))
            assertTrue(prompt.decline())
            assertTrue(ended.await(2, TimeUnit.SECONDS))
            assertEquals(DebugBluetoothConsentDecision.Decision.DECLINE, answer.get())
        } finally {
            prompt.close()
            first.interrupt()
            first.join(2_000)
        }
    }
}
