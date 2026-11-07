package com.edenpractical.comms.wifidirect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugWifiDirectGroupHostGateTest {
    private fun begin(g: DebugWifiDirectGroupHostGate) =
        requireNotNull(g.start(true, true, true, 29))

    @Test fun onlyHumanStartOnSupportedForegroundDevice() {
        val g = DebugWifiDirectGroupHostGate()
        assertNull(g.start(false, true, true, 29))
        assertNull(g.start(true, false, true, 29))
        assertNull(g.start(true, true, false, 29))
        assertNull(g.start(true, true, true, 28))
        val t = begin(g)
        assertNull(g.start(true, true, true, 29))
        assertFalse(g.submitCreation(t))
        assertFalse(g.platformAccepted(t))
        assertTrue(g.observedExisting(t, false))
        assertTrue(g.submitCreation(t))
        assertFalse(g.submitCreation(t))
        assertTrue(g.platformAccepted(t))
        assertFalse(g.groupSeen(t, false))
        assertTrue(g.groupSeen(t, true))
        assertTrue(g.snapshot().radioHostObserved)
        assertEquals(DebugWifiDirectGroupHostGate.Phase.HOST_OBSERVED, g.snapshot().phase)
    }

    @Test fun existingGroupIsUntouchedAndNeverRunsCreateOrCleanup() {
        val g = DebugWifiDirectGroupHostGate()
        val t = begin(g)
        assertFalse(g.observedExisting(t, true))
        assertEquals(DebugWifiDirectGroupHostGate.End.EXISTING_GROUP, g.snapshot().reason)
        assertFalse(g.submitCreation(t))
        assertFalse(g.snapshot().creationSubmitted)
        assertFalse(g.stop(t, DebugWifiDirectGroupHostGate.End.USER_STOP))
        assertTrue(g.reset())
        assertTrue(begin(g).generation > t.generation)
    }

    @Test fun stopAfterCreateRequiresCleanupAndRejectsLateCallbacks() {
        val g = DebugWifiDirectGroupHostGate()
        val old = begin(g)
        assertTrue(g.observedExisting(old, false))
        assertTrue(g.submitCreation(old))
        assertTrue(g.stop(old, DebugWifiDirectGroupHostGate.End.BACKGROUND))
        assertTrue(g.pending(old))
        assertFalse(g.platformAccepted(old))
        assertFalse(g.groupSeen(old, true))
        assertNull(g.start(true, true, true, 36))
        assertTrue(g.cleaned(old, false))
        assertEquals(DebugWifiDirectGroupHostGate.End.CLEANUP_UNCONFIRMED, g.snapshot().reason)
        assertTrue(g.reset())
        val fresh = begin(g)
        assertFalse(g.observedExisting(old, false))
        assertFalse(g.cleaned(old, true))
        assertTrue(g.observedExisting(fresh, false))
        assertTrue(g.submitCreation(fresh))
        assertTrue(g.stop(fresh, DebugWifiDirectGroupHostGate.End.TIMEOUT))
        assertTrue(g.cleaned(fresh, true))
        assertEquals(DebugWifiDirectGroupHostGate.End.CLEANUP_CONFIRMED, g.snapshot().reason)
    }

    @Test fun stopBeforeCreateNeedsNoGroupRemoval() {
        val g = DebugWifiDirectGroupHostGate()
        val t = begin(g)
        assertTrue(g.stop(t, DebugWifiDirectGroupHostGate.End.USER_STOP))
        assertEquals(DebugWifiDirectGroupHostGate.Phase.STOPPED, g.snapshot().phase)
        assertFalse(g.snapshot().creationSubmitted)
        assertFalse(g.cleaned(t, true))
        assertTrue(g.reset())
    }
}
