package com.edenpractical.comms.transport

import com.edenpractical.comms.transport.DebugApprovalGate.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugApprovalGateTest {
    @Test fun noPresetBeforeBothRealControlObservations() {
        val gate = DebugApprovalGate()
        assertEquals(State.WAITING, gate.snapshot())
        assertFalse(gate.maySendPreset())
        assertEquals(State.LOCAL_ONLY, gate.localWritten())
        assertFalse(gate.maySendPreset())
        assertEquals(State.BOTH_OBSERVED, gate.peerObserved())
        assertTrue(gate.maySendPreset())
    }

    @Test fun peerMayApproveFirstWithoutImplyingLocalConsent() {
        val gate = DebugApprovalGate()
        assertEquals(State.PEER_ONLY, gate.peerObserved())
        assertFalse(gate.maySendPreset())
        assertEquals(State.BOTH_OBSERVED, gate.localWritten())
        assertTrue(gate.maySendPreset())
    }

    @Test fun repeatedDecisionsFailClosed() {
        val local = DebugApprovalGate()
        assertEquals(State.LOCAL_ONLY, local.localWritten())
        assertEquals(State.INVALID, local.localWritten())
        assertFalse(local.maySendPreset())
        assertEquals(State.INVALID, local.peerObserved())

        val remote = DebugApprovalGate()
        assertEquals(State.PEER_ONLY, remote.peerObserved())
        assertEquals(State.INVALID, remote.peerObserved())
        assertFalse(remote.maySendPreset())

        val completed = DebugApprovalGate()
        completed.localWritten()
        completed.peerObserved()
        assertEquals(State.INVALID, completed.peerObserved())
        assertFalse(completed.maySendPreset())
    }

    @Test fun declineRevokesEvenAfterBothObserved() {
        val gate = DebugApprovalGate()
        gate.localWritten()
        gate.peerObserved()
        assertTrue(gate.maySendPreset())
        assertEquals(State.DECLINED, gate.decline())
        assertFalse(gate.maySendPreset())
        assertEquals(State.DECLINED, gate.peerObserved())
        assertEquals(State.DECLINED, gate.localWritten())
    }

    @Test fun closeIsIrreversibleAndNeverCarriesOver() {
        val previous = DebugApprovalGate()
        previous.localWritten()
        previous.peerObserved()
        assertTrue(previous.maySendPreset())
        assertEquals(State.CLOSED, previous.close())
        assertEquals(State.CLOSED, previous.peerObserved())
        assertEquals(State.CLOSED, previous.localWritten())
        assertEquals(State.CLOSED, previous.decline())
        assertFalse(previous.maySendPreset())
        val next = DebugApprovalGate()
        assertEquals(State.WAITING, next.snapshot())
        assertFalse(next.maySendPreset())
    }

    @Test fun thisIsNotProductionMessagingApproval() {
        val gate = DebugApprovalGate()
        gate.localWritten()
        gate.peerObserved()
        assertTrue(gate.maySendPreset())
        assertFalse(com.edenpractical.comms.model.ReleaseCapabilities.CAN_CONNECT)
        assertFalse(com.edenpractical.comms.model.ReleaseCapabilities.CAN_SEND_MESSAGES)
        assertFalse(com.edenpractical.comms.model.ReleaseCapabilities.CAN_TRANSMIT_VOICE)
    }
}
