package com.edenpractical.comms.transport

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugTextReceiveGuardTest {
    @Test fun noTextAcceptedBeforeBothCurrentConfirmations() {
        val gate = DebugTextReceiveGuard()
        assertFalse(gate.accept(1L, confirmedHere = false))
        assertFalse(gate.accept(Long.MAX_VALUE, confirmedHere = false))
        assertTrue(gate.accept(1L, confirmedHere = true))
    }

    @Test fun IDsAreStrictlyIncreasingAndPerConnection() {
        val gate = DebugTextReceiveGuard()
        assertFalse(gate.accept(0, true))
        assertFalse(gate.accept(-1, true))
        assertTrue(gate.accept(1, true))
        assertFalse(gate.accept(1, true))
        assertFalse(gate.accept(0, true))
        assertTrue(gate.accept(3, true))
        assertFalse(gate.accept(2, true))
        assertFalse(gate.accept(3, true))
        assertFalse(gate.accept(4, false))
        assertTrue(gate.accept(4, true))
        assertTrue(DebugTextReceiveGuard().accept(1, true))
    }

    @Test fun noAcknowledgmentOrPeerReadIsDerivedFromAcceptance() {
        val gate = DebugTextReceiveGuard()
        assertTrue(gate.accept(7, true))
        // Only reader-side local acceptance; no reply to the other phone.
        assertFalse(gate.accept(7, true))
    }
}
