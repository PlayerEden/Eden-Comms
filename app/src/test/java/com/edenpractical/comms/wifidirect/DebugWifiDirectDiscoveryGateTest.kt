package com.edenpractical.comms.wifidirect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugWifiDirectDiscoveryGateTest {
    private fun begin(g: DebugWifiDirectDiscoveryGate) = g.begin(true, true)!!

    @Test fun discoveryNeedsUserStartAndPositiveP2pStateAndAsyncAcceptance() {
        val g = DebugWifiDirectDiscoveryGate()
        assertNull(g.begin(false, true))
        assertNull(g.begin(true, false))
        val t = begin(g)
        assertNull(g.begin(true, true))
        assertFalse(g.requestSent(t))
        assertFalse(g.discoveryAccepted(t))
        assertFalse(g.peerListChanged(t))
        assertTrue(g.observedRadio(t, true))
        assertTrue(g.requestSent(t))
        assertFalse(g.requestSent(t))
        assertFalse(g.peerListChanged(t))
        assertTrue(g.discoveryAccepted(t))
        assertTrue(g.peerListChanged(t))
        assertEquals(1, g.snapshot().events)
        assertEquals(DebugWifiDirectDiscoveryGate.Phase.SCANNING, g.snapshot().phase)
    }

    @Test fun noLateCallbackCanReactivateStoppedOrNextGeneration() {
        val g = DebugWifiDirectDiscoveryGate()
        val old = begin(g)
        assertTrue(g.stop(old, DebugWifiDirectDiscoveryGate.End.STOP_BUTTON))
        assertFalse(g.discoveryAccepted(old))
        assertFalse(g.peerListChanged(old))
        assertFalse(g.stop(old, DebugWifiDirectDiscoveryGate.End.TIMEOUT))
        assertTrue(g.reset())
        val fresh = begin(g)
        assertFalse(g.observedRadio(old, true))
        assertFalse(g.requestSent(old))
        assertTrue(g.observedRadio(fresh, true))
        assertEquals(old.generation + 1L, fresh.generation)
        assertTrue(g.stop(fresh, DebugWifiDirectDiscoveryGate.End.BACKGROUND))
        assertFalse(g.active(fresh))
        assertFalse(g.discoveryFailed(fresh))
    }

    @Test fun disabledRadioFailureAndEventLimitAreTerminal() {
        val g = DebugWifiDirectDiscoveryGate()
        val disabled = begin(g)
        assertFalse(g.observedRadio(disabled, false))
        assertEquals(DebugWifiDirectDiscoveryGate.End.RADIO_LOST, g.snapshot().end)
        assertTrue(g.reset())
        val fail = begin(g)
        assertTrue(g.observedRadio(fail, true))
        assertTrue(g.requestSent(fail))
        assertTrue(g.discoveryFailed(fail))
        assertFalse(g.discoveryAccepted(fail))
        assertTrue(g.reset())
        val cap = begin(g)
        assertTrue(g.observedRadio(cap, true))
        assertTrue(g.requestSent(cap))
        assertTrue(g.discoveryAccepted(cap))
        repeat(DebugWifiDirectDiscoveryGate.MAX_EVENTS) { assertTrue(g.peerListChanged(cap)) }
        assertFalse(g.peerListChanged(cap))
        assertEquals(DebugWifiDirectDiscoveryGate.End.EVENT_LIMIT, g.snapshot().end)
        assertFalse(g.peerListChanged(cap))
    }
}
