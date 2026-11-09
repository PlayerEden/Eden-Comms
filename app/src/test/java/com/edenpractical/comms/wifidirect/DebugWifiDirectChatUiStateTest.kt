package com.edenpractical.comms.wifidirect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugWifiDirectChatUiStateTest {
    @Test fun newExplicitRadioAttemptClearsStaleTerminalFeedbackAndDraft() {
        val state = DebugWifiDirectChatUiState()
        state.draft = "harmless test draft"
        state.noteQueueResult(false)
        state.approval(true)
        assertTrue(state.sendFeedback.contains("Not queued"))
        state.status(
            DebugWifiDirectApprovedTcpSession.Phase.STOPPED,
            "Session ended."
        )
        assertEquals(DebugWifiDirectApprovedTcpSession.Phase.STOPPED, state.phase)
        state.draft = "stale text"
        state.noteQueueResult(true)
        state.beginNewAttempt()
        assertEquals(DebugWifiDirectApprovedTcpSession.Phase.IDLE, state.phase)
        assertTrue(state.note.contains("No verified conversation yet"))
        assertEquals("", state.draft)
        assertEquals("", state.sendFeedback)
        assertFalse(state.approvalPending)
        assertTrue(state.lines.isEmpty())
    }

    @Test fun onlyAuthenticatedReadyEventAdvancesToChat() {
        val state = DebugWifiDirectChatUiState()
        state.beginNewAttempt()
        assertEquals(DebugWifiDirectApprovedTcpSession.Phase.IDLE, state.phase)
        state.noteQueueResult(false)
        assertEquals(DebugWifiDirectApprovedTcpSession.Phase.IDLE, state.phase)
        state.clear()
        assertEquals(DebugWifiDirectApprovedTcpSession.Phase.STOPPED, state.phase)
        state.beginNewAttempt()
        assertEquals(DebugWifiDirectApprovedTcpSession.Phase.IDLE, state.phase)
    }
}
