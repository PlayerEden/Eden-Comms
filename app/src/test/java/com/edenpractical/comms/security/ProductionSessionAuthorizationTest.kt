package com.edenpractical.comms.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionSessionAuthorizationTest {
    private val local = ByteArray(32) { 0x11.toByte() }
    private val peer = ByteArray(32) { 0x22.toByte() }
    private val otherPeer = ByteArray(32) { 0x33.toByte() }
    private val transcriptA = ByteArray(32) { 0x44.toByte() }
    private val transcriptB = ByteArray(32) { 0x55.toByte() }

    private fun start(gate: ProductionSessionAuthorization) =
        requireNotNull(gate.begin(local, peer))

    private fun authenticated(gate: ProductionSessionAuthorization):
        ProductionSessionAuthorization.AttemptId {
        val attempt = start(gate)
        assertTrue(gate.onAuthenticatedChannel(attempt, peer, transcriptA))
        return attempt
    }

    @Test fun disconnectedByDefaultAndAllProductionCapabilitiesRemainDisabled() {
        val gate = ProductionSessionAuthorization()
        assertEquals(ProductionSessionAuthorization.Phase.DISCONNECTED, gate.phase)
        assertFalse(gate.approvalModelComplete)
        assertFalse(gate.maySendProductionText)
        assertFalse(gate.mayTransmitProductionVoice)
    }

    @Test fun malformedSelfPairAndMissingPinsBlockBeforeNegotiation() {
        listOf(
            Pair(ByteArray(31), peer),
            Pair(local, ByteArray(33)),
            Pair(ByteArray(32), peer),
            Pair(local, ByteArray(32)),
            Pair(local, local)
        ).forEach { (one, two) ->
            val gate = ProductionSessionAuthorization()
            assertNull(gate.begin(one, two))
            assertEquals(ProductionSessionAuthorization.Phase.BLOCKED, gate.phase)
            assertFalse(gate.maySendProductionText)
            gate.endSession()
            assertNotNull(gate.begin(local, peer))
        }
    }

    @Test fun discoveryAndMutualHandshakeAloneAreNotPermission() {
        val gate = ProductionSessionAuthorization()
        val id = start(gate)
        assertEquals(ProductionSessionAuthorization.Phase.AWAITING_AUTHENTICATED_CHANNEL, gate.phase)
        assertFalse(gate.approvalModelComplete)
        assertTrue(gate.onAuthenticatedChannel(id, peer, transcriptA))
        assertEquals(ProductionSessionAuthorization.Phase.AWAITING_BOTH_APPROVALS, gate.phase)
        assertFalse(gate.approvalModelComplete)
        assertFalse(gate.maySendProductionText)
    }

    @Test fun localThenAuthenticatedRemoteApprovalCompleteOnlyModelAndNeverEnableReleaseSend() {
        val gate = ProductionSessionAuthorization()
        val id = authenticated(gate)
        assertTrue(gate.localApproved(id, transcriptA))
        assertFalse(gate.approvalModelComplete)
        assertTrue(gate.onAuthenticatedRemoteApproval(id, transcriptA))
        assertTrue(gate.approvalModelComplete)
        assertEquals(ProductionSessionAuthorization.Phase.APPROVALS_COMPLETE, gate.phase)
        assertFalse(gate.maySendProductionText)
        assertFalse(gate.mayTransmitProductionVoice)
    }

    @Test fun remoteThenLocalApprovalHasTheSameStrictOrdering() {
        val gate = ProductionSessionAuthorization()
        val id = authenticated(gate)
        assertTrue(gate.onAuthenticatedRemoteApproval(id, transcriptA))
        assertFalse(gate.approvalModelComplete)
        assertTrue(gate.localApproved(id, transcriptA))
        assertTrue(gate.approvalModelComplete)
        assertFalse(gate.maySendProductionText)
    }

    @Test fun missingAuthenticatedChannelCannotBeReplacedByLocalOrRemoteConsent() {
        val gate = ProductionSessionAuthorization()
        val id = start(gate)
        assertFalse(gate.localApproved(id, transcriptA))
        assertEquals(ProductionSessionAuthorization.Phase.BLOCKED, gate.phase)
        assertFalse(gate.onAuthenticatedRemoteApproval(id, transcriptA))
        assertFalse(gate.maySendProductionText)
    }

    @Test fun wrongAuthenticatedPeerPinBlocks() {
        val gate = ProductionSessionAuthorization()
        val id = start(gate)
        assertFalse(gate.onAuthenticatedChannel(id, otherPeer, transcriptA))
        assertEquals(ProductionSessionAuthorization.Phase.BLOCKED, gate.phase)
    }

    @Test fun zeroOrShortChannelTranscriptCannotBeUsed() {
        listOf(ByteArray(0), ByteArray(32), ByteArray(31)).forEach { malformed ->
            val gate = ProductionSessionAuthorization()
            val id = start(gate)
            assertFalse(gate.onAuthenticatedChannel(id, peer, malformed))
            assertEquals(ProductionSessionAuthorization.Phase.BLOCKED, gate.phase)
        }
    }

    @Test fun transcriptMismatchesFailClosedRegardlessOfConsentDirection() {
        listOf(true, false).forEach { isRemote ->
            val gate = ProductionSessionAuthorization()
            val id = authenticated(gate)
            val result = if (isRemote)
                gate.onAuthenticatedRemoteApproval(id, transcriptB)
            else gate.localApproved(id, transcriptB)
            assertFalse(result)
            assertEquals(ProductionSessionAuthorization.Phase.BLOCKED, gate.phase)
            assertFalse(gate.approvalModelComplete)
        }
    }

    @Test fun duplicateAuthenticatedTransportNotificationBlocksAnAmbiguousSession() {
        val gate = ProductionSessionAuthorization()
        val id = authenticated(gate)
        assertFalse(gate.onAuthenticatedChannel(id, peer, transcriptB))
        assertEquals(ProductionSessionAuthorization.Phase.BLOCKED, gate.phase)
    }

    @Test fun expectedPeerInputIsCopiedRatherThanTrustedAsMutableCallerMemory() {
        val gate = ProductionSessionAuthorization()
        val callerPin = peer.copyOf()
        val id = requireNotNull(gate.begin(local, callerPin))
        callerPin.fill(0)
        assertTrue(gate.onAuthenticatedChannel(id, peer, transcriptA))
    }

    @Test fun lateCallbacksFromEndedConnectionNeverTouchTheNewConnection() {
        val gate = ProductionSessionAuthorization()
        val oldId = authenticated(gate)
        gate.transportLost(oldId)
        assertEquals(ProductionSessionAuthorization.Phase.DISCONNECTED, gate.phase)
        val newId = start(gate)
        assertFalse(gate.onAuthenticatedChannel(oldId, peer, transcriptA))
        assertFalse(gate.localApproved(oldId, transcriptA))
        assertFalse(gate.onAuthenticatedRemoteApproval(oldId, transcriptA))
        gate.transportLost(oldId)
        gate.authenticationFailed(oldId)
        gate.authenticatedPeerChanged(oldId)
        assertEquals(ProductionSessionAuthorization.Phase.AWAITING_AUTHENTICATED_CHANNEL, gate.phase)
        assertTrue(gate.onAuthenticatedChannel(newId, peer, transcriptB))
        assertFalse(gate.approvalModelComplete)
    }

    @Test fun transportLossImmediatelyRevokesEvenCompletedApprovals() {
        val gate = ProductionSessionAuthorization()
        val id = authenticated(gate)
        gate.localApproved(id, transcriptA)
        gate.onAuthenticatedRemoteApproval(id, transcriptA)
        assertTrue(gate.approvalModelComplete)
        gate.transportLost(id)
        assertFalse(gate.approvalModelComplete)
        assertFalse(gate.maySendProductionText)
        assertEquals(ProductionSessionAuthorization.Phase.DISCONNECTED, gate.phase)
    }

    @Test fun peerChangeAndAuthenticationFailureAreTerminalUntilExplicitReset() {
        listOf(true, false).forEach { change ->
            val gate = ProductionSessionAuthorization()
            val id = authenticated(gate)
            if (change) gate.authenticatedPeerChanged(id) else gate.authenticationFailed(id)
            assertEquals(ProductionSessionAuthorization.Phase.BLOCKED, gate.phase)
            assertNull(gate.begin(local, peer))
            gate.endSession()
            assertNotNull(gate.begin(local, peer))
        }
    }

    @Test fun appPauseOrCancelWipesPendingApprovalsAndInvalidatesOldAttempt() {
        val gate = ProductionSessionAuthorization()
        val oldId = authenticated(gate)
        gate.localApproved(oldId, transcriptA)
        gate.endSession()
        assertFalse(gate.onAuthenticatedRemoteApproval(oldId, transcriptA))
        assertFalse(gate.approvalModelComplete)
        assertFalse(gate.maySendProductionText)
        val next = start(gate)
        assertFalse(gate.localApproved(oldId, transcriptA))
        assertTrue(gate.onAuthenticatedChannel(next, peer, transcriptB))
        assertFalse(gate.approvalModelComplete)
    }
}
