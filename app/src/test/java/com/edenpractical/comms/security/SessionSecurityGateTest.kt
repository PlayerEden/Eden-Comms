package com.edenpractical.comms.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSecurityGateTest {
    @Test fun disconnectedByDefault() {
        val gate = SessionSecurityGate()
        assertEquals(SessionPhase.DISCONNECTED, gate.phase)
        assertFalse(gate.maySendMessage)
        assertFalse(gate.mayTransmitAudio)
    }

    @Test fun discoveryOrHandshakeAloneCannotUnlockMessages() {
        val gate = SessionSecurityGate()
        assertTrue(gate.begin("alice-identity"))
        assertFalse(gate.maySendMessage)
        assertTrue(gate.authenticatedChannelEstablished("alice-identity", "session-A"))
        assertEquals(SessionPhase.AWAITING_BOTH_CONFIRMATIONS, gate.phase)
        assertFalse(gate.maySendMessage)
    }

    @Test fun bothConfirmationsRequiredForSameSession() {
        val gate = SessionSecurityGate()
        gate.begin("alice")
        gate.authenticatedChannelEstablished("alice", "transcript-A")
        assertTrue(gate.localCodeConfirmed("transcript-A"))
        assertFalse(gate.maySendMessage)
        assertTrue(gate.remoteCodeConfirmed("transcript-A"))
        assertTrue(gate.maySendMessage)
        assertTrue(gate.mayTransmitAudio)
    }

    @Test fun remoteConfirmationAloneDoesNotUnlock() {
        val gate = SessionSecurityGate()
        gate.begin("alice")
        gate.authenticatedChannelEstablished("alice", "transcript-A")
        gate.remoteCodeConfirmed("transcript-A")
        assertFalse(gate.maySendMessage)
    }

    @Test fun incorrectPeerIdentityFailsClosed() {
        val gate = SessionSecurityGate()
        gate.begin("alice")
        assertFalse(gate.authenticatedChannelEstablished("mallory", "transcript-A"))
        assertEquals(SessionPhase.BLOCKED, gate.phase)
        assertFalse(gate.maySendMessage)
    }

    @Test fun staleOrWrongConfirmationBlocksSession() {
        val gate = SessionSecurityGate()
        gate.begin("alice")
        gate.authenticatedChannelEstablished("alice", "transcript-A")
        assertFalse(gate.localCodeConfirmed("transcript-old"))
        assertEquals(SessionPhase.BLOCKED, gate.phase)
        assertFalse(gate.remoteCodeConfirmed("transcript-A"))
        assertFalse(gate.maySendMessage)
    }

    @Test fun transportLossRevokesTrustImmediately() {
        val gate = SessionSecurityGate()
        gate.begin("alice")
        gate.authenticatedChannelEstablished("alice", "session-A")
        gate.localCodeConfirmed("session-A")
        gate.remoteCodeConfirmed("session-A")
        assertTrue(gate.maySendMessage)
        gate.transportLost()
        assertEquals(SessionPhase.DISCONNECTED, gate.phase)
        assertFalse(gate.maySendMessage)
        assertTrue(gate.begin("alice"))
        assertFalse(gate.maySendMessage)
        assertFalse(gate.remoteCodeConfirmed("session-A"))
    }

    @Test fun explicitResetIsRequiredAfterSecurityError() {
        val gate = SessionSecurityGate()
        gate.begin("alice")
        gate.authenticationFailed()
        assertEquals(SessionPhase.BLOCKED, gate.phase)
        assertFalse(gate.begin("alice"))
        gate.endSession()
        assertTrue(gate.begin("alice"))
        assertFalse(gate.maySendMessage)
    }

    @Test fun cannotVerifyBeforeAnAuthenticatedHandshake() {
        val gate = SessionSecurityGate()
        gate.begin("alice")
        assertFalse(gate.localCodeConfirmed("invented-binding"))
        assertEquals(SessionPhase.BLOCKED, gate.phase)
        assertFalse(gate.maySendMessage)
    }

    @Test fun cannotChangePeerOrBeginSecondSessionSilently() {
        val gate = SessionSecurityGate()
        assertTrue(gate.begin("alice"))
        assertFalse(gate.begin("bob"))
        gate.peerIdentityChanged()
        assertFalse(gate.maySendMessage)
        assertEquals(SessionPhase.BLOCKED, gate.phase)
    }
}
