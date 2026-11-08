package com.edenpractical.comms.transport

import com.edenpractical.comms.model.ConnectionMethod
import com.edenpractical.comms.model.ReleaseCapabilities
import com.edenpractical.comms.transport.NearbySessionLifecycle.EndReason
import com.edenpractical.comms.transport.NearbySessionLifecycle.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * REVIEW-ONLY virtual two-peer fault rehearsal. The simulator is NOT a phone,
 * TLS link, Wi-Fi radio, QR/Keystore flow or production authorization.
 */
class TwoPeerFailureRehearsalTest {
    private class Peer(method: ConnectionMethod) {
        val transport = ScriptedNearbyTransportAdapter(method)
        val attempt = requireNotNull(transport.begin())
        val consent = DebugApprovalGate()

        fun canSendFakePreset(): Boolean =
            transport.lifecycle.isPreviewActive(attempt) && consent.maySendPreset()

        fun arriveReady() {
            assertTrue(transport.post(attempt,
                ScriptedNearbyTransportAdapter.Signal.LINK_PRESENT, 1))
            assertTrue(transport.post(attempt,
                ScriptedNearbyTransportAdapter.Signal.PREVIEW_READY, 2))
            assertEquals(listOf(true, true),
                transport.advanceBy(2).map { it.accepted })
            assertEquals(Phase.PREVIEW_READY, transport.lifecycle.snapshot().phase)
        }
    }

    @Test fun bothSidesRequireTheirOwnApprovalsAndRemoteObservation() {
        val host = Peer(ConnectionMethod.LAN)
        val joiner = Peer(ConnectionMethod.LAN)
        host.arriveReady(); joiner.arriveReady()
        assertFalse(host.canSendFakePreset())
        assertFalse(joiner.canSendFakePreset())

        host.consent.localWritten(); joiner.consent.peerObserved()
        joiner.consent.localWritten(); host.consent.peerObserved()
        assertFalse(host.canSendFakePreset())
        assertFalse(joiner.canSendFakePreset())
        host.consent.localConfirmationWritten()
        joiner.consent.peerConfirmationObserved()
        assertFalse(host.canSendFakePreset())
        assertFalse(joiner.canSendFakePreset())
        joiner.consent.localConfirmationWritten()
        // The joiner's local facts are complete, but this is not proof the
        // host already observed the joiner's final confirmation.
        assertTrue(joiner.canSendFakePreset())
        assertFalse(host.canSendFakePreset())
        host.consent.peerConfirmationObserved()
        assertTrue(host.canSendFakePreset())

        assertTrue(host.transport.cancel(host.attempt))
        host.consent.close()
        assertTrue(joiner.transport.post(joiner.attempt,
            ScriptedNearbyTransportAdapter.Signal.PEER_LOST, 1))
        assertTrue(joiner.transport.advanceBy(1).single().accepted)
        joiner.consent.close()
        assertEquals(EndReason.USER_CANCELLED,
            host.transport.lifecycle.snapshot().endReason)
        assertEquals(EndReason.PEER_LEFT,
            joiner.transport.lifecycle.snapshot().endReason)
        assertFalse(host.canSendFakePreset())
        assertFalse(joiner.canSendFakePreset())
        assertFalse(ReleaseCapabilities.CAN_CONNECT)
        assertFalse(ReleaseCapabilities.CAN_SEND_MESSAGES)
        assertFalse(ReleaseCapabilities.CAN_TRANSMIT_VOICE)
    }

    @Test fun cancelBackgroundAndTimeoutBeforeReadyRejectDelayedSignals() {
        for (method in ConnectionMethod.values()) {
            val host = Peer(method)
            val joiner = Peer(method)
            host.transport.post(host.attempt,
                ScriptedNearbyTransportAdapter.Signal.LINK_PRESENT, 10)
            host.transport.post(host.attempt,
                ScriptedNearbyTransportAdapter.Signal.PREVIEW_READY, 20)
            joiner.transport.post(joiner.attempt,
                ScriptedNearbyTransportAdapter.Signal.TIMED_OUT, 15)
            joiner.transport.post(joiner.attempt,
                ScriptedNearbyTransportAdapter.Signal.PREVIEW_READY, 20)

            assertTrue(host.transport.appBackgrounded(host.attempt))
            host.consent.close()
            assertEquals(listOf(false, false),
                host.transport.advanceBy(20).map { it.accepted })
            assertEquals(listOf(true, false),
                joiner.transport.advanceBy(20).map { it.accepted })
            joiner.consent.close()
            assertEquals(EndReason.APP_BACKGROUNDED,
                host.transport.lifecycle.snapshot().endReason)
            assertEquals(EndReason.TIMED_OUT,
                joiner.transport.lifecycle.snapshot().endReason)
            assertFalse(host.canSendFakePreset())
            assertFalse(joiner.canSendFakePreset())
        }
    }

    @Test fun oldSessionCallbacksCannotTerminateFreshTwoPeerAttempt() {
        val a = Peer(ConnectionMethod.LAN)
        val b = Peer(ConnectionMethod.LAN)
        a.transport.post(a.attempt, ScriptedNearbyTransportAdapter.Signal.PEER_LOST, 40)
        b.transport.post(b.attempt, ScriptedNearbyTransportAdapter.Signal.NETWORK_CHANGED, 45)
        assertTrue(a.transport.cancel(a.attempt))
        assertTrue(b.transport.cancel(b.attempt))
        a.consent.close(); b.consent.close()
        assertTrue(a.transport.resetAfterTermination())
        assertTrue(b.transport.resetAfterTermination())

        for (peer in listOf(a, b)) {
            val fresh = requireNotNull(peer.transport.begin())
            assertTrue(fresh.generation > peer.attempt.generation)
            peer.transport.post(fresh, ScriptedNearbyTransportAdapter.Signal.LINK_PRESENT, 1)
            peer.transport.post(fresh, ScriptedNearbyTransportAdapter.Signal.PREVIEW_READY, 2)
            assertEquals(listOf(true, true),
                peer.transport.advanceBy(2).map { it.accepted })
            assertFalse(peer.consent.maySendPreset())
        }
        assertEquals(listOf(false), a.transport.advanceBy(45).map { it.accepted })
        assertEquals(listOf(false), b.transport.advanceBy(45).map { it.accepted })
        assertEquals(Phase.PREVIEW_READY, a.transport.lifecycle.snapshot().phase)
        assertEquals(Phase.PREVIEW_READY, b.transport.lifecycle.snapshot().phase)
    }

    @Test fun independentNetworkChangeAndVerificationRejectAreTerminal() {
        for (method in ConnectionMethod.values()) {
            val a = Peer(method)
            val b = Peer(method)
            a.arriveReady(); b.arriveReady()
            a.transport.post(a.attempt,
                ScriptedNearbyTransportAdapter.Signal.NETWORK_CHANGED, 1)
            b.transport.post(b.attempt,
                ScriptedNearbyTransportAdapter.Signal.VERIFICATION_REJECTED, 1)
            assertTrue(a.transport.advanceBy(1).single().accepted)
            assertTrue(b.transport.advanceBy(1).single().accepted)
            a.consent.close(); b.consent.close()
            assertEquals(EndReason.NETWORK_CHANGED,
                a.transport.lifecycle.snapshot().endReason)
            assertEquals(EndReason.VERIFICATION_REJECTED,
                b.transport.lifecycle.snapshot().endReason)
            assertFalse(a.canSendFakePreset())
            assertFalse(b.canSendFakePreset())
        }
    }
}
