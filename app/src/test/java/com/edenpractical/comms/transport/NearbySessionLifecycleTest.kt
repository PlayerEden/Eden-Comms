package com.edenpractical.comms.transport

import com.edenpractical.comms.model.ConnectionMethod
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbySessionLifecycleTest {
    private val Phase = NearbySessionLifecycle.Phase
    private val EndReason = NearbySessionLifecycle.EndReason

    private fun start(
        model: NearbySessionLifecycle,
        method: ConnectionMethod = ConnectionMethod.LAN
    ) = requireNotNull(model.begin(method))

    @Test fun startsIdleWithoutNetworkOrProductionAuthorization() {
        val model = NearbySessionLifecycle()
        assertEquals(Phase.IDLE, model.snapshot().phase)
        assertNull(model.snapshot().method)
        assertNull(model.snapshot().endReason)
        assertFalse(com.edenpractical.comms.model.ReleaseCapabilities.CAN_CONNECT)
        assertFalse(com.edenpractical.comms.model.ReleaseCapabilities.CAN_SEND_MESSAGES)
        assertFalse(com.edenpractical.comms.model.ReleaseCapabilities.CAN_TRANSMIT_VOICE)
    }

    @Test fun exactSameBookkeepingForAllThreePlannedMethodsDoesNotActivateThem() {
        for (method in ConnectionMethod.values()) {
            val model = NearbySessionLifecycle()
            val attempt = start(model, method)
            assertEquals(method, model.snapshot().method)
            assertEquals(Phase.STARTING, model.snapshot().phase)
            assertFalse(model.isPreviewActive(attempt))
            assertTrue(model.linkPresent(attempt))
            assertEquals(Phase.LINK_PRESENT, model.snapshot().phase)
            assertFalse(model.isPreviewActive(attempt))
            assertTrue(model.previewReady(attempt))
            assertEquals(Phase.PREVIEW_READY, model.snapshot().phase)
            assertTrue(model.isPreviewActive(attempt))
            // A status-only 'ready' label cannot authorize any actual send.
            assertFalse(com.edenpractical.comms.model.ReleaseCapabilities.CAN_CONNECT)
            assertFalse(com.edenpractical.comms.model.ReleaseCapabilities.CAN_SEND_MESSAGES)
            assertFalse(com.edenpractical.comms.model.ReleaseCapabilities.CAN_TRANSMIT_VOICE)
            assertTrue(model.finish(attempt, EndReason.TEST_ENDED))
            assertEquals(Phase.TERMINATED, model.snapshot().phase)
            assertFalse(model.isPreviewActive(attempt))
            assertEquals(EndReason.TEST_ENDED, model.snapshot().endReason)
        }
    }

    @Test fun aNewAttemptRequiresExplicitTerminalReset() {
        val model = NearbySessionLifecycle()
        val old = start(model)
        assertNull(model.begin(ConnectionMethod.BLUETOOTH))
        assertFalse(model.resetAfterTermination())
        assertTrue(model.finish(old, EndReason.USER_CANCELLED))
        assertNull(model.begin(ConnectionMethod.WIFI_DIRECT))
        assertTrue(model.resetAfterTermination())
        assertEquals(Phase.IDLE, model.snapshot().phase)
        assertNull(model.snapshot().method)
        assertNull(model.snapshot().endReason)
        val next = start(model, ConnectionMethod.WIFI_DIRECT)
        assertEquals(old.generation + 1, next.generation)
        assertFalse(model.linkPresent(old))
        assertFalse(model.isPreviewActive(old))
        assertTrue(model.linkPresent(next))
        assertEquals(Phase.LINK_PRESENT, model.snapshot().phase)
    }

    @Test fun secondCoordinatorCannotUseAnotherCoordinatorsAttempt() {
        val left = NearbySessionLifecycle()
        val right = NearbySessionLifecycle()
        val a = start(left)
        val b = start(right)
        assertEquals(a.generation, b.generation)
        assertFalse(left.linkPresent(b))
        assertFalse(right.previewReady(a))
        assertFalse(left.finish(b, EndReason.NETWORK_CHANGED))
        assertEquals(Phase.STARTING, left.snapshot().phase)
        assertEquals(Phase.STARTING, right.snapshot().phase)
        assertTrue(left.linkPresent(a))
    }

    @Test fun noReadyWithoutLinkAndInvalidCurrentSequenceFailsClosed() {
        val model = NearbySessionLifecycle()
        val attempt = start(model)
        assertFalse(model.previewReady(attempt))
        assertEquals(Phase.TERMINATED, model.snapshot().phase)
        assertEquals(EndReason.INVALID_SEQUENCE, model.snapshot().endReason)
        assertFalse(model.linkPresent(attempt))
        assertFalse(model.previewReady(attempt))
        assertFalse(model.isPreviewActive(attempt))
        assertTrue(model.resetAfterTermination())
        val next = start(model)
        assertTrue(model.linkPresent(next))
        assertFalse(model.linkPresent(next)) // duplicate must not silently proceed
        assertEquals(EndReason.INVALID_SEQUENCE, model.snapshot().endReason)
    }

    @Test fun duplicateReadyIsTerminalAndNeverRestoresTestActivity() {
        val model = NearbySessionLifecycle()
        val token = start(model)
        assertTrue(model.linkPresent(token))
        assertTrue(model.previewReady(token))
        assertFalse(model.previewReady(token))
        assertEquals(Phase.TERMINATED, model.snapshot().phase)
        assertEquals(EndReason.INVALID_SEQUENCE, model.snapshot().endReason)
        assertFalse(model.isPreviewActive(token))
    }

    @Test fun anyFixedTerminalReasonClosesAttemptWithoutRawErrorDetails() {
        for (reason in EndReason.values()) {
            val model = NearbySessionLifecycle()
            val attempt = start(model)
            assertTrue(model.finish(attempt, reason))
            assertEquals(reason, model.snapshot().endReason)
            assertFalse(model.finish(attempt, reason))
            assertFalse(model.linkPresent(attempt))
            assertFalse(model.previewReady(attempt))
        }
    }

    @Test fun endFromEveryLivePhaseIsImmediateAndIrreversible() {
        for (phase in listOf(Phase.STARTING, Phase.LINK_PRESENT, Phase.PREVIEW_READY)) {
            val model = NearbySessionLifecycle()
            val attempt = start(model)
            if (phase != Phase.STARTING) assertTrue(model.linkPresent(attempt))
            if (phase == Phase.PREVIEW_READY) assertTrue(model.previewReady(attempt))
            assertEquals(phase, model.snapshot().phase)
            assertTrue(model.finish(attempt, EndReason.APP_BACKGROUNDED))
            assertFalse(model.isPreviewActive(attempt))
            assertFalse(model.previewReady(attempt))
            assertEquals(Phase.TERMINATED, model.snapshot().phase)
        }
    }

    @Test fun staleLateEventsCannotPoisonNewAttempt() {
        val model = NearbySessionLifecycle()
        val old = start(model)
        model.finish(old, EndReason.PEER_LEFT)
        assertTrue(model.resetAfterTermination())
        val fresh = start(model)
        assertFalse(model.previewReady(old))
        assertFalse(model.linkPresent(old))
        assertFalse(model.finish(old, EndReason.VERIFICATION_REJECTED))
        assertEquals(Phase.STARTING, model.snapshot().phase)
        assertNull(model.snapshot().endReason)
        assertTrue(model.linkPresent(fresh))
        assertTrue(model.previewReady(fresh))
    }

    @Test fun concurrentReadyAndCancelCanNeverLeaveModelActive() {
        repeat(30) {
            val model = NearbySessionLifecycle()
            val token = start(model)
            assertTrue(model.linkPresent(token))
            val start = CountDownLatch(1)
            val done = CountDownLatch(2)
            val a = thread {
                try {
                    start.await(3, TimeUnit.SECONDS)
                    model.previewReady(token)
                } finally {
                    done.countDown()
                }
            }
            val b = thread {
                try {
                    start.await(3, TimeUnit.SECONDS)
                    model.finish(token, EndReason.USER_CANCELLED)
                } finally {
                    done.countDown()
                }
            }
            start.countDown()
            assertTrue(done.await(5, TimeUnit.SECONDS))
            a.join(1_000)
            b.join(1_000)
            assertEquals(Phase.TERMINATED, model.snapshot().phase)
            assertFalse(model.isPreviewActive(token))
        }
    }

    @Test fun noUserPayloadOrPeerIdentifierAppearsInSnapshot() {
        val model = NearbySessionLifecycle()
        val attempt = start(model, ConnectionMethod.LAN)
        val before = model.snapshot()
        assertEquals(ConnectionMethod.LAN, before.method)
        assertEquals(attempt.generation, before.generation)
        assertNull(before.endReason)
        val fieldNames = NearbySessionLifecycle.Snapshot::class.java.declaredFields
            .map { it.name.lowercase() }
        for (forbidden in listOf("message", "content", "key", "pin", "peerid", "ipaddress")) {
            assertFalse("Unexpected field: $forbidden", fieldNames.any { it.contains(forbidden) })
        }
    }
}
