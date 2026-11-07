package com.edenpractical.comms.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingSessionUiTest {
    @Test fun everyInProgressRouteRequiresExplicitExit() {
        listOf("showHost", "scanHost", "showReply", "scanReply",
            "connecting", "approval", "demo").forEach {
            assertTrue(it, PairingSessionUi.needsLeaveWarning(it))
        }
    }

    @Test fun idleAndTerminalRoutesKeepOrdinaryBackNavigation() {
        listOf("checking", "choose", "result", "identityError", "unknown").forEach {
            assertFalse(it, PairingSessionUi.needsLeaveWarning(it))
        }
    }

    @Test fun labelsNeverPretendApprovalOrConnectionDuringSetup() {
        assertEquals("NOT CONNECTED", PairingSessionUi.statusLabel("choose"))
        assertEquals("EXCHANGING QR CODES", PairingSessionUi.statusLabel("showHost"))
        assertEquals("CHECKING TEST LINK", PairingSessionUi.statusLabel("connecting"))
        assertTrue(PairingSessionUi.statusLabel("approval").contains("PENDING"))
        assertEquals("TEMPORARY TEST CHAT ACTIVE", PairingSessionUi.statusLabel("demo"))
        assertEquals("TEST LINK ENDED", PairingSessionUi.statusLabel("result"))
        assertEquals("NOT CONNECTED", PairingSessionUi.statusLabel("mystery"))
    }

    @Test fun departureExplanationsDoNotPromiseRecoveryOrErasure() {
        val live = PairingSessionUi.leaveExplanation("demo")
        assertTrue(live.contains("as best effort"))
        assertTrue(live.contains("other phone"))
        assertTrue(live.contains("fresh QR"))
        val settingUp = PairingSessionUi.leaveExplanation("approval")
        assertTrue(settingUp.contains("cannot be resumed"))
        assertTrue(settingUp.contains("closes"))
        assertFalse(settingUp.contains("automatically", ignoreCase = true))
    }
}
