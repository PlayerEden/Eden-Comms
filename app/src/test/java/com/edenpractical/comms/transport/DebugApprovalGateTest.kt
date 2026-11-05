package com.edenpractical.comms.transport

import com.edenpractical.comms.transport.DebugApprovalGate.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugApprovalGateTest {
    @Test fun twoApprovalsAloneNeverAuthorizeEvenPresetTestPhrases() {
        val gate = DebugApprovalGate()
        assertEquals(State.WAITING, gate.snapshot())
        assertFalse(gate.maySendPreset())
        assertFalse(gate.mayWriteConfirmation())
        assertEquals(State.LOCAL_ONLY, gate.localWritten())
        assertFalse(gate.maySendPreset())
        assertEquals(State.BOTH_OBSERVED, gate.peerObserved())
        assertTrue(gate.mayWriteConfirmation())
        assertFalse(gate.maySendPreset())
        assertEquals(State.LOCAL_CONFIRMED, gate.localConfirmationWritten())
        assertFalse(gate.maySendPreset())
        assertFalse(gate.mayWriteConfirmation())
        assertEquals(State.CONFIRMED, gate.peerConfirmationObserved())
        assertTrue(gate.maySendPreset())
    }

    @Test fun peerApprovesFirstAndBothConfirmationsMayArriveInEitherOrder() {
        val gate = DebugApprovalGate()
        assertEquals(State.PEER_ONLY, gate.peerObserved())
        assertFalse(gate.maySendPreset())
        assertEquals(State.BOTH_OBSERVED, gate.localWritten())
        assertEquals(State.PEER_CONFIRMED, gate.peerConfirmationObserved())
        assertFalse(gate.maySendPreset())
        assertTrue(gate.mayWriteConfirmation())
        assertEquals(State.CONFIRMED, gate.localConfirmationWritten())
        assertTrue(gate.maySendPreset())
    }

    @Test fun peerConfirmMayRaceLocalWriteCallbackButCannotUnlockBeforeIt() {
        val gate = DebugApprovalGate()
        gate.peerObserved()
        // The peer confirms only after seeing our APPROVE written to TLS.
        // Our reader callback can be scheduled before our writer's state update.
        assertEquals(State.PEER_ONLY, gate.peerConfirmationObserved())
        assertFalse(gate.maySendPreset())
        assertFalse(gate.mayWriteConfirmation())
        assertEquals(State.PEER_CONFIRMED, gate.localWritten())
        assertFalse(gate.maySendPreset())
        assertTrue(gate.mayWriteConfirmation())
        assertEquals(State.CONFIRMED, gate.localConfirmationWritten())
        assertTrue(gate.maySendPreset())
    }

    @Test fun unsolicitedOrDuplicateFinalConfirmationFailsClosed() {
        val early = DebugApprovalGate()
        assertEquals(State.INVALID, early.peerConfirmationObserved())
        assertFalse(early.maySendPreset())
        assertEquals(State.INVALID, early.localWritten())

        val repeat = DebugApprovalGate()
        repeat.localWritten()
        repeat.peerObserved()
        repeat.peerConfirmationObserved()
        assertEquals(State.INVALID, repeat.peerConfirmationObserved())
        assertFalse(repeat.maySendPreset())

        val local = DebugApprovalGate()
        assertEquals(State.INVALID, local.localConfirmationWritten())
        assertFalse(local.mayWriteConfirmation())

        val duplicates = DebugApprovalGate()
        duplicates.localWritten()
        assertEquals(State.INVALID, duplicates.localWritten())
        assertFalse(duplicates.maySendPreset())

        val confirmed = DebugApprovalGate()
        confirmed.peerObserved()
        confirmed.localWritten()
        confirmed.peerConfirmationObserved()
        confirmed.localConfirmationWritten()
        assertTrue(confirmed.maySendPreset())
        assertEquals(State.INVALID, confirmed.localConfirmationWritten())
        assertFalse(confirmed.maySendPreset())
    }

    @Test fun declineAndDisconnectRevokeEvenPreviouslyConfirmedSession() {
        val gate = DebugApprovalGate()
        gate.localWritten()
        gate.peerObserved()
        gate.localConfirmationWritten()
        gate.peerConfirmationObserved()
        assertTrue(gate.maySendPreset())
        assertEquals(State.DECLINED, gate.decline())
        assertFalse(gate.maySendPreset())
        assertFalse(gate.mayWriteConfirmation())
        assertEquals(State.DECLINED, gate.peerConfirmationObserved())
        assertEquals(State.DECLINED, gate.localWritten())
        assertEquals(State.CLOSED, gate.close())
        assertFalse(gate.maySendPreset())
    }

    @Test fun oldSocketCallbacksCannotAuthorizeFreshAttempt() {
        val first = DebugApprovalGate()
        first.localWritten()
        first.peerObserved()
        first.localConfirmationWritten()
        first.peerConfirmationObserved()
        assertTrue(first.maySendPreset())
        assertEquals(State.CLOSED, first.close())
        assertEquals(State.CLOSED, first.peerConfirmationObserved())
        assertFalse(first.maySendPreset())
        val fresh = DebugApprovalGate()
        assertEquals(State.WAITING, fresh.snapshot())
        assertFalse(fresh.maySendPreset())
    }

    @Test fun simulatorDoesNotEnableProductionTextOrVoice() {
        val gate = DebugApprovalGate()
        gate.localWritten()
        gate.peerObserved()
        gate.localConfirmationWritten()
        gate.peerConfirmationObserved()
        assertTrue(gate.maySendPreset())
        assertFalse(com.edenpractical.comms.model.ReleaseCapabilities.CAN_CONNECT)
        assertFalse(com.edenpractical.comms.model.ReleaseCapabilities.CAN_SEND_MESSAGES)
        assertFalse(com.edenpractical.comms.model.ReleaseCapabilities.CAN_TRANSMIT_VOICE)
    }
}
