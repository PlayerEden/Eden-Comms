package com.edenpractical.comms.wifidirect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugWifiDirectPermissionAttemptGateTest {
    private fun begin(gate: DebugWifiDirectPermissionAttemptGate): Boolean =
        gate.begin(true, true, true, false)

    @Test fun requiresRealForegroundTapAndRejectsDuplicate() {
        val gate = DebugWifiDirectPermissionAttemptGate()
        assertFalse(gate.begin(false, true, true, false))
        assertFalse(gate.begin(true, false, true, false))
        assertFalse(gate.begin(true, true, false, false))
        assertFalse(gate.begin(true, true, true, true))
        assertTrue(begin(gate))
        assertFalse(begin(gate))
        assertEquals(DebugWifiDirectPermissionAttemptGate.Outcome.DENIED,
            gate.complete(false, true))
        assertFalse(gate.outstanding)
        assertEquals(DebugWifiDirectPermissionAttemptGate.Outcome.IGNORED,
            gate.complete(true, true))
        assertTrue(begin(gate))
        assertEquals(DebugWifiDirectPermissionAttemptGate.Outcome.GRANTED,
            gate.complete(true, true))
    }

    @Test fun cancellationAndBackgroundDiscardDelayedResults() {
        val gate = DebugWifiDirectPermissionAttemptGate()
        assertTrue(begin(gate))
        gate.cancel()
        assertTrue(gate.outstanding)
        assertFalse(begin(gate))
        assertEquals(DebugWifiDirectPermissionAttemptGate.Outcome.IGNORED,
            gate.complete(true, true))
        assertTrue(begin(gate))
        assertEquals(DebugWifiDirectPermissionAttemptGate.Outcome.IGNORED,
            gate.complete(true, false))
        assertTrue(begin(gate))
        gate.cancel()
        gate.cancel()
        assertEquals(DebugWifiDirectPermissionAttemptGate.Outcome.IGNORED,
            gate.complete(false, true))
    }
}
