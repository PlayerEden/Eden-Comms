package com.edenpractical.comms.wifidirect

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure deterministic lifetime tests for the actual debug controller/UI
 * callback admission policy; no fake OS group/TLS/phone acceptance claim.
 */
class DebugWifiDirectCallbackFenceTest {
    private val fence = DebugWifiDirectCallbackFence

    @Test fun onlyExactCurrentControllerMayMutateUi() {
        val old = Any()
        val current = Any()
        assertFalse(fence.owns(current, old))
        assertTrue(fence.owns(current, current))
        assertFalse(fence.owns(null, old))
        assertFalse(fence.owns(null, null))
        assertFalse(fence.owns(old, null))
    }

    @Test fun pendingFailureMustMatchBothAttemptAndOneShotQrRequest() {
        val token = Any()
        val request = Any()
        assertTrue(fence.mayFailPending(
            token, token, request, request,
            groupObserved = true, verifiedSessionPresent = false
        ))
        assertFalse(fence.mayFailPending(
            Any(), token, request, request,
            groupObserved = true, verifiedSessionPresent = false
        ))
        assertFalse(fence.mayFailPending(
            token, token, Any(), request,
            groupObserved = true, verifiedSessionPresent = false
        ))
        assertFalse(fence.mayFailPending(
            token, token, request, request,
            groupObserved = false, verifiedSessionPresent = false
        ))
        assertFalse(fence.mayFailPending(
            token, token, request, request,
            groupObserved = true, verifiedSessionPresent = true
        ))
    }

    @Test fun canceledOrPromotedRequestCannotCloseAnotherAttempt() {
        val oldAttempt = Any()
        val newAttempt = Any()
        val oldQrRequest = Any()
        val newQrRequest = Any()
        // Cancel: the original owner is no longer active.
        assertFalse(fence.mayFailPending(
            null, oldAttempt, null, oldQrRequest, true, false
        ))
        // New attempt: even with a pending newer QR, the old OS callback
        // must neither close that request nor publish an old STOPPED state.
        assertFalse(fence.mayFailPending(
            newAttempt, oldAttempt, newQrRequest, oldQrRequest, true, false
        ))
        // Same attempt but the old QR was already handed to a session:
        // a delayed failure must not clobber the established channel.
        assertFalse(fence.mayFailPending(
            oldAttempt, oldAttempt, null, oldQrRequest, true, true
        ))
        assertFalse(fence.mayFailPending(
            oldAttempt, oldAttempt, null, oldQrRequest, true, false
        ))
        assertTrue(fence.mayFailPending(
            newAttempt, newAttempt, newQrRequest, newQrRequest, true, false
        ))
    }

    @Test fun delayedCallbackCannotClobberNewOwnerAfterInterruption() {
        val old = Any()
        val newer = Any()
        val owner = AtomicReference<Any?>(old)
        val applied = AtomicInteger(0)
        val queued = CountDownLatch(1)
        val fire = CountDownLatch(1)
        val pool = Executors.newSingleThreadExecutor()
        try {
            val future = pool.submit {
                queued.countDown()
                if (fire.await(3, TimeUnit.SECONDS) &&
                    fence.owns(owner.get(), old)
                ) applied.incrementAndGet()
            }
            assertTrue(queued.await(3, TimeUnit.SECONDS))
            owner.set(newer) // old session canceled and another started
            fire.countDown()
            future.get(5, TimeUnit.SECONDS)
            assertEquals(0, applied.get())
            assertTrue(fence.owns(owner.get(), newer))
        } finally {
            fire.countDown()
            pool.shutdownNow()
        }
    }
}
