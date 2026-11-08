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

    private fun reviewContext(
        hostRole: Boolean = true,
        pinByte: Byte = 0x11,
        transcriptByte: Byte = 0x22
    ) = DebugBluetoothFutureSessionContext(
        hostRole, ByteArray(32) { pinByte }, ByteArray(32) { transcriptByte }
    )

    private fun start(
        owner: DebugBluetoothFutureOwnedSession,
        generation: Long,
        elapsedMs: Long,
        observations: DebugBluetoothContinuationBudget.Observations,
        resource: Closeable
    ): Boolean {
        val context = reviewContext()
        return try {
            owner.begin(generation, elapsedMs, observations, resource, context)
        } finally {
            context.close()
        }
    }

    private fun checkObservation(
        owner: DebugBluetoothFutureOwnedSession,
        generation: Long,
        elapsedMs: Long,
        foreground: Boolean,
        connectPermission: Boolean
    ): Boolean {
        val context = reviewContext()
        return try {
            owner.observe(generation, elapsedMs, foreground, connectPermission, context)
        } finally {
            context.close()
        }
    }

    @Test fun oneResourceIsKeptOnlyWithinBoundedLifetimeThenClosedExactlyOnce() {
        val link = TestResource()
        val owner = DebugBluetoothFutureOwnedSession(
            DebugBluetoothContinuationBudget(maxLifetimeMs = 5)
        )
        assertTrue(start(owner,7, 100, observations(), link))
        assertEquals(DebugBluetoothFutureOwnedSession.Phase.OBSERVING, owner.snapshot().phase)
        assertTrue(checkObservation(owner,7, 103, true, true))
        assertEquals(0, link.closes.get())
        assertTrue(checkObservation(owner,7, 104, true, true))
        assertFalse(checkObservation(owner,7, 105, true, true))
        assertEquals(1, link.closes.get())
        assertEquals(DebugBluetoothFutureOwnedSession.Phase.CLOSED, owner.snapshot().phase)
        assertEquals(DebugBluetoothFutureOwnedSession.End.INVALIDATED, owner.snapshot().end)
        owner.close()
        assertEquals(1, link.closes.get())
        assertFalse(start(owner,8, 106, observations(), TestResource()))
    }

    @Test fun failedPreconditionsCloseOfferedResourceAndCannotRearm() {
        val denied = TestResource()
        val owner = DebugBluetoothFutureOwnedSession()
        assertFalse(start(owner,
            9, 100, observations().copy(bilateralConfirmations = false), denied
        ))
        assertEquals(1, denied.closes.get())
        assertEquals(DebugBluetoothFutureOwnedSession.End.REFUSED, owner.snapshot().end)
        val retry = TestResource()
        assertFalse(start(owner,10, 101, observations(), retry))
        assertEquals(1, retry.closes.get())
        assertEquals(DebugBluetoothFutureOwnedSession.Phase.CLOSED, owner.snapshot().phase)
    }

    @Test fun staleAttemptCallbacksCannotCloseCurrentlyObservedResource() {
        val first = TestResource()
        val owner = DebugBluetoothFutureOwnedSession()
        assertTrue(start(owner,28, 100, observations(), first))
        assertFalse(checkObservation(owner,27, 110, false, false))
        assertFalse(owner.stop(27))
        assertEquals(0, first.closes.get())
        assertTrue(checkObservation(owner,28, 111, true, true))

        val other = TestResource()
        assertFalse(start(owner,29, 112, observations(), other))
        assertEquals(1, other.closes.get())
        assertEquals(0, first.closes.get())
        // Submitting the SAME already-owned resource on a duplicate start
        // does not prematurely close the actual current owner's resource.
        assertFalse(start(owner,29, 112, observations(), first))
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
        assertTrue(start(owner,3, 500, observations(), link))
        assertTrue(checkObservation(owner,3, 570, true, true))
        assertFalse(checkObservation(owner,3, 560, true, true))
        assertEquals(1, link.closes.get())
        assertFalse(checkObservation(owner,3, 580, true, true))
        assertEquals(1, link.closes.get())
    }

    @Test fun backgroundAndPermissionLossAlwaysCloseOwnedResource() {
        val backgroundLink = TestResource()
        val background = DebugBluetoothFutureOwnedSession()
        assertTrue(start(background,10, 1, observations(), backgroundLink))
        assertFalse(checkObservation(background,10, 2, false, true))
        assertEquals(1, backgroundLink.closes.get())

        val permissionLink = TestResource()
        val revoked = DebugBluetoothFutureOwnedSession()
        assertTrue(start(revoked,11, 1, observations(), permissionLink))
        assertFalse(checkObservation(revoked,11, 2, true, false))
        assertEquals(1, permissionLink.closes.get())
    }

    @Test fun userDisconnectAndOwnerCloseAreIdempotentEvenIfCloseThrows() {
        val broken = TestResource(failOnClose = true)
        val owner = DebugBluetoothFutureOwnedSession()
        assertTrue(start(owner,50, 1, observations(), broken))
        assertTrue(owner.stop(50))
        owner.close()
        assertEquals(1, broken.closes.get())
        assertEquals(DebugBluetoothFutureOwnedSession.End.USER, owner.snapshot().end)

        val normal = TestResource()
        val explicitlyClosed = DebugBluetoothFutureOwnedSession()
        assertTrue(start(explicitlyClosed,51, 1, observations(), normal))
        explicitlyClosed.close()
        explicitlyClosed.close()
        assertEquals(1, normal.closes.get())
        assertEquals(DebugBluetoothFutureOwnedSession.End.OWNER_CLOSED, explicitlyClosed.snapshot().end)
        assertFalse(checkObservation(explicitlyClosed,51, 2, true, true))
    }

    @Test fun matchingPeerPinTranscriptAndRoleRequiredForEveryObservation() {
        val alteredContexts = listOf(
            reviewContext(hostRole = false),
            reviewContext(pinByte = 0x33),
            reviewContext(transcriptByte = 0x44)
        )
        try {
            for (mismatch in alteredContexts) {
                val link = TestResource()
                val owner = DebugBluetoothFutureOwnedSession()
                assertTrue(start(owner, 71, 100, observations(), link))
                // Wrong generation must not affect a correctly bound attempt.
                assertFalse(owner.observe(72, 101, false, false, mismatch))
                assertEquals(0, link.closes.get())
                // Same generation with incorrect role/peer/transcript burns it.
                assertFalse(owner.observe(71, 102, true, true, mismatch))
                assertEquals(1, link.closes.get())
                assertEquals(DebugBluetoothFutureOwnedSession.End.INVALIDATED, owner.snapshot().end)
                assertFalse(checkObservation(owner, 71, 103, true, true))
                assertFalse(start(owner, 73, 104, observations(), TestResource()))
            }
        } finally {
            alteredContexts.forEach { it.close() }
        }
    }

    @Test fun closedContextIsNotAdmittedAndCallerArrayChangesCannotRebindSession() {
        val denied = TestResource()
        val deadContext = reviewContext()
        deadContext.close()
        val refused = DebugBluetoothFutureOwnedSession()
        assertFalse(refused.begin(1, 100, observations(), denied, deadContext))
        assertEquals(1, denied.closes.get())
        assertEquals(DebugBluetoothFutureOwnedSession.End.REFUSED, refused.snapshot().end)

        val peer = ByteArray(32) { 0x11 }
        val transcript = ByteArray(32) { 0x22 }
        val supplied = DebugBluetoothFutureSessionContext(true, peer, transcript)
        val owner = DebugBluetoothFutureOwnedSession()
        val link = TestResource()
        try {
            assertTrue(owner.begin(8, 100, observations(), link, supplied))
            // Mutating/closing the caller's arrays/context cannot rewrite
            // the owner's independent retained snapshot.
            peer.fill(0)
            transcript.fill(0)
            supplied.close()
            assertTrue(checkObservation(owner, 8, 101, true, true))
            val wrong = reviewContext(pinByte = 0x77)
            try {
                assertFalse(owner.observe(8, 102, true, true, wrong))
            } finally {
                wrong.close()
            }
            assertEquals(1, link.closes.get())
        } finally {
            supplied.close()
            owner.close()
        }
    }

    @Test fun competingDisconnectAndDeadlineCallbacksCloseExactlyOnce() {
        val link = TestResource()
        val owner = DebugBluetoothFutureOwnedSession(
            DebugBluetoothContinuationBudget(maxLifetimeMs = 10)
        )
        assertTrue(start(owner,88, 100, observations(), link))
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(4)
        try {
            val callbacks = (0 until 4).map { index ->
                pool.submit {
                    start.await()
                    when (index) {
                        0 -> owner.stop(88)
                        1 -> checkObservation(owner,88, 110, true, true)
                        2 -> checkObservation(owner,88, 101, false, true)
                        else -> owner.close()
                    }
                }
            }
            start.countDown()
            for (callback in callbacks) callback.get(3, TimeUnit.SECONDS)
            assertEquals(DebugBluetoothFutureOwnedSession.Phase.CLOSED, owner.snapshot().phase)
            assertEquals(1, link.closes.get())
            assertFalse(start(owner,89, 111, observations(), TestResource()))
        } finally {
            pool.shutdownNow()
        }
    }
}
