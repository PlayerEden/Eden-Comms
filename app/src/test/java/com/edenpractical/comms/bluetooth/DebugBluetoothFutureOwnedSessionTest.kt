package com.edenpractical.comms.bluetooth

import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM resource-lifetime rehearsal. These synthetic observations do NOT
 * prove QR identity, real TLS human approvals or Android radio compatibility.
 */
class DebugBluetoothFutureOwnedSessionTest {
    private class TestResource(private val failOnClose: Boolean = false) : Closeable {
        val closes = AtomicInteger(0)
        override fun close() {
            closes.incrementAndGet()
            if (failOnClose) throw IllegalStateException("Synthetic close failure")
        }
    }

    private fun observations() = DebugBluetoothContinuationBudget.Observations(
        peerPinChecked = true,
        qrChallengeChecked = true,
        exporterChecked = true,
        bilateralApprovals = true,
        bilateralConfirmations = true,
        foreground = true,
        connectPermission = true
    )

    @Test fun oneResourceIsKeptOnlyWithinBoundedLifetimeThenClosedExactlyOnce() {
        val link = TestResource()
        val owner = DebugBluetoothFutureOwnedSession(
            DebugBluetoothContinuationBudget(maxLifetimeMs = 5)
        )
        assertTrue(owner.begin(7, 100, observations(), link))
        assertEquals(DebugBluetoothFutureOwnedSession.Phase.OBSERVING, owner.snapshot().phase)
        assertTrue(owner.observe(7, 103, true, true))
        assertEquals(0, link.closes.get())
        assertTrue(owner.observe(7, 104, true, true))
        assertFalse(owner.observe(7, 105, true, true))
        assertEquals(1, link.closes.get())
        assertEquals(DebugBluetoothFutureOwnedSession.Phase.CLOSED, owner.snapshot().phase)
        assertEquals(DebugBluetoothFutureOwnedSession.End.INVALIDATED, owner.snapshot().end)
        owner.close()
        assertEquals(1, link.closes.get())
        assertFalse(owner.begin(8, 106, observations(), TestResource()))
    }

    @Test fun failedPreconditionsCloseOfferedResourceAndCannotRearm() {
        val denied = TestResource()
        val owner = DebugBluetoothFutureOwnedSession()
        assertFalse(owner.begin(
            9, 100, observations().copy(bilateralConfirmations = false), denied
        ))
        assertEquals(1, denied.closes.get())
        assertEquals(DebugBluetoothFutureOwnedSession.End.REFUSED, owner.snapshot().end)
        val retry = TestResource()
        assertFalse(owner.begin(10, 101, observations(), retry))
        assertEquals(1, retry.closes.get())
        assertEquals(DebugBluetoothFutureOwnedSession.Phase.CLOSED, owner.snapshot().phase)
    }

    @Test fun staleAttemptCallbacksCannotCloseCurrentlyObservedResource() {
        val first = TestResource()
        val owner = DebugBluetoothFutureOwnedSession()
        assertTrue(owner.begin(28, 100, observations(), first))
        assertFalse(owner.observe(27, 110, false, false))
        assertFalse(owner.stop(27))
        assertEquals(0, first.closes.get())
        assertTrue(owner.observe(28, 111, true, true))

        val other = TestResource()
        assertFalse(owner.begin(29, 112, observations(), other))
        assertEquals(1, other.closes.get())
        assertEquals(0, first.closes.get())
        // Submitting the SAME already-owned resource on a duplicate start
        // does not prematurely close the actual current owner's resource.
        assertFalse(owner.begin(29, 112, observations(), first))
        assertEquals(0, first.closes.get())
        assertTrue(owner.stop(28))
        assertEquals(1, first.closes.get())
        assertFalse(owner.stop(28))
    }

    @Test fun validThenBackwardClockClosesLinkAndBurnsGeneration() {
        val link = TestResource()
        val owner = DebugBluetoothFutureOwnedSession(
            DebugBluetoothContinuationBudget(maxLifetimeMs = 100)
        )
        assertTrue(owner.begin(3, 500, observations(), link))
        assertTrue(owner.observe(3, 570, true, true))
        assertFalse(owner.observe(3, 560, true, true))
        assertEquals(1, link.closes.get())
        assertFalse(owner.observe(3, 580, true, true))
        assertEquals(1, link.closes.get())
    }

    @Test fun backgroundAndPermissionLossAlwaysCloseOwnedResource() {
        val backgroundLink = TestResource()
        val background = DebugBluetoothFutureOwnedSession()
        assertTrue(background.begin(10, 1, observations(), backgroundLink))
        assertFalse(background.observe(10, 2, false, true))
        assertEquals(1, backgroundLink.closes.get())

        val permissionLink = TestResource()
        val revoked = DebugBluetoothFutureOwnedSession()
        assertTrue(revoked.begin(11, 1, observations(), permissionLink))
        assertFalse(revoked.observe(11, 2, true, false))
        assertEquals(1, permissionLink.closes.get())
    }

    @Test fun userDisconnectAndOwnerCloseAreIdempotentEvenIfCloseThrows() {
        val broken = TestResource(failOnClose = true)
        val owner = DebugBluetoothFutureOwnedSession()
        assertTrue(owner.begin(50, 1, observations(), broken))
        assertTrue(owner.stop(50))
        owner.close()
        assertEquals(1, broken.closes.get())
        assertEquals(DebugBluetoothFutureOwnedSession.End.USER, owner.snapshot().end)

        val normal = TestResource()
        val explicitlyClosed = DebugBluetoothFutureOwnedSession()
        assertTrue(explicitlyClosed.begin(51, 1, observations(), normal))
        explicitlyClosed.close()
        explicitlyClosed.close()
        assertEquals(1, normal.closes.get())
        assertEquals(DebugBluetoothFutureOwnedSession.End.OWNER_CLOSED, explicitlyClosed.snapshot().end)
        assertFalse(explicitlyClosed.observe(51, 2, true, true))
    }

    @Test fun competingDisconnectAndDeadlineCallbacksCloseExactlyOnce() {
        val link = TestResource()
        val owner = DebugBluetoothFutureOwnedSession(
            DebugBluetoothContinuationBudget(maxLifetimeMs = 10)
        )
        assertTrue(owner.begin(88, 100, observations(), link))
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(4)
        try {
            val callbacks = (0 until 4).map { index ->
                pool.submit {
                    start.await()
                    when (index) {
                        0 -> owner.stop(88)
                        1 -> owner.observe(88, 110, true, true)
                        2 -> owner.observe(88, 101, false, true)
                        else -> owner.close()
                    }
                }
            }
            start.countDown()
            for (callback in callbacks) callback.get(3, TimeUnit.SECONDS)
            assertEquals(DebugBluetoothFutureOwnedSession.Phase.CLOSED, owner.snapshot().phase)
            assertEquals(1, link.closes.get())
            assertFalse(owner.begin(89, 111, observations(), TestResource()))
        } finally {
            pool.shutdownNow()
        }
    }
}
