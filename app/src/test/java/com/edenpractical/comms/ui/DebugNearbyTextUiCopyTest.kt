package com.edenpractical.comms.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugNearbyTextUiCopyTest {
    @Test fun eachObservedEventHasItsOwnNonMisleadingStatus() {
        assertEquals(
            "Received on this phone",
            DebugNearbyTextUiCopy.lineStatus(outgoing = false, acknowledged = false)
        )
        assertEquals(
            "Received on this phone",
            DebugNearbyTextUiCopy.lineStatus(outgoing = false, acknowledged = true)
        )
        assertEquals(
            "Written to encrypted test link here — no peer-app ACK observed",
            DebugNearbyTextUiCopy.lineStatus(outgoing = true, acknowledged = false)
        )
        assertEquals(
            "Other app returned ACK — not a read receipt",
            DebugNearbyTextUiCopy.lineStatus(outgoing = true, acknowledged = true)
        )
    }

    @Test fun queueResultDoesNotPretendThePeerHasReceivedOrReadAnything() {
        val queued = DebugNearbyTextUiCopy.queueFeedback(true)
        val refused = DebugNearbyTextUiCopy.queueFeedback(false)
        assertTrue(queued.contains("Queued on this phone"))
        assertTrue(queued.contains("Not yet a write or delivery receipt"))
        assertTrue(refused.contains("Not queued"))
        assertTrue(refused.contains("Draft kept"))
        assertFalse(queued.contains("delivered", ignoreCase = true))
        assertFalse(refused.contains("sent successfully", ignoreCase = true))
    }

    @Test fun inputUsesCodePointsAndRejectsInvalidOrOversizeDrafts() {
        assertEquals(280, DebugNearbyTextUiCopy.MAX_CODE_POINTS)
        val emoji = "🙂".repeat(280)
        assertTrue(DebugNearbyTextUiCopy.acceptsDraft(emoji))
        assertTrue(DebugNearbyTextUiCopy.canSubmit(emoji))
        assertFalse(DebugNearbyTextUiCopy.acceptsDraft(emoji + "A"))
        assertFalse(DebugNearbyTextUiCopy.canSubmit("   "))
        assertFalse(DebugNearbyTextUiCopy.acceptsDraft("A\u0000B"))
        assertFalse(DebugNearbyTextUiCopy.canSubmit("\uD800"))
        assertTrue(DebugNearbyTextUiCopy.canSubmit("Hello\nthere"))
    }
}
