package com.edenpractical.comms.wifidirect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugWifiDirectTargetLinkGateTest {
    private fun begin(g: DebugWifiDirectTargetLinkGate) =
        requireNotNull(g.start(confirmed = true, foreground = true, ready = true, api = 29))

    @Test fun requiresExplicitConfirmationAndSupportedForegroundRadio() {
        val g = DebugWifiDirectTargetLinkGate()
        assertNull(g.start(false, true, true, 29))
        assertNull(g.start(true, false, true, 29))
        assertNull(g.start(true, true, false, 29))
        assertNull(g.start(true, true, true, 28))
        val token = begin(g)
        assertNull(g.start(true, true, true, 29))
        assertFalse(g.submit(token))
        assertFalse(g.requestAccepted(token))
        assertFalse(g.observedGroup(token, true))
        assertTrue(g.checkedExisting(token, false))
        assertTrue(g.submit(token))
        assertFalse(g.submit(token))
        assertTrue(g.requestAccepted(token))
        assertTrue(g.observedGroup(token, true))
        assertTrue(g.snapshot().groupObserved)
        assertEquals(DebugWifiDirectTargetLinkGate.Phase.GROUP_OBSERVED, g.snapshot().phase)
    }

    @Test fun preExistingGroupIsRefusedAndNeverRemovedByThisGate() {
        val g = DebugWifiDirectTargetLinkGate()
        val token = begin(g)
        assertFalse(g.checkedExisting(token, true))
        assertEquals(DebugWifiDirectTargetLinkGate.End.EXISTING_GROUP, g.snapshot().reason)
        assertFalse(g.snapshot().submitted)
        assertFalse(g.submit(token))
        assertFalse(g.stop(token, DebugWifiDirectTargetLinkGate.End.USER_STOP))
        assertFalse(g.pendingCleanup(token))
    }

    @Test fun cleanupFencesLateSuccessAndRejectsNewStartsBeforeFinish() {
        val g = DebugWifiDirectTargetLinkGate()
        val token = begin(g)
        assertTrue(g.checkedExisting(token, false))
        assertTrue(g.submit(token))
        assertTrue(g.stop(token, DebugWifiDirectTargetLinkGate.End.BACKGROUND))
        assertTrue(g.pendingCleanup(token))
        assertFalse(g.requestAccepted(token))
        assertFalse(g.observedGroup(token, true))
        assertNull(g.start(true, true, true, 29))
        assertTrue(g.cleanupCompleted(token, false))
        assertEquals(DebugWifiDirectTargetLinkGate.End.CLEANUP_UNCONFIRMED,
            g.snapshot().reason)
        assertTrue(g.reset())
        val fresh = begin(g)
        assertTrue(fresh.generation > token.generation)
        assertFalse(g.checkedExisting(token, false))
        assertFalse(g.cleanupCompleted(token, true))
        assertTrue(g.checkedExisting(fresh, false))
        assertTrue(g.submit(fresh))
        assertTrue(g.stop(fresh, DebugWifiDirectTargetLinkGate.End.TIMEOUT))
        assertTrue(g.cleanupCompleted(fresh, true))
        assertEquals(DebugWifiDirectTargetLinkGate.End.CLEANUP_ABSENT,
            g.snapshot().reason)
    }

    @Test fun stopBeforeSubmitClosesWithoutGroupCleanup() {
        val g = DebugWifiDirectTargetLinkGate()
        val token = begin(g)
        assertTrue(g.stop(token, DebugWifiDirectTargetLinkGate.End.USER_STOP))
        assertFalse(g.snapshot().submitted)
        assertFalse(g.pendingCleanup(token))
        assertFalse(g.cleanupCompleted(token, true))
        assertTrue(g.reset())
    }

    @Test fun asynchronousGroupCanAppearBeforeConnectActionCallback() {
        val g = DebugWifiDirectTargetLinkGate()
        val token = begin(g)
        assertTrue(g.checkedExisting(token, false))
        assertTrue(g.submit(token))
        assertTrue(g.observedGroup(token, true))
        assertFalse(g.requestAccepted(token))
        assertTrue(g.stop(token, DebugWifiDirectTargetLinkGate.End.USER_STOP))
        assertTrue(g.pendingCleanup(token))
        assertTrue(g.cleanupCompleted(token, false))
    }
}
