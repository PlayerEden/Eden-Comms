package com.edenpractical.comms.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyConnectionCuesTest {
    private val cues = NearbyConnectionCues

    @Test fun allQrRolesSayWhatToDoWithoutGrantingTrust() {
        val variants = mapOf(
            "showHost" to "Show this code",
            "scanHost" to "Scan the code",
            "scanReply" to "Scan the reply",
            "showReply" to "Keep this code visible"
        )
        for ((stage, expected) in variants) {
            val cue = requireNotNull(cues.forStage(stage))
            assertTrue(cue.phase, cue.phase.startsWith("STEP 1 OF 3"))
            assertTrue(cue.heading, cue.heading.contains(expected))
            assertFalse(cue.next.contains("authenticated", ignoreCase = true))
            assertFalse(cue.next.contains("trusted", ignoreCase = true))
        }
    }

    @Test fun requestingAndCheckingNeverPretendPeerIsApproved() {
        val connecting = requireNotNull(cues.forStage("connecting"))
        assertEquals("STEP 2 OF 3 • CHECK THE LINK", connecting.phase)
        assertTrue(connecting.next.contains("Neither person is approved"))
        assertNull(cues.forStage("checking"))
        assertNull(cues.forStage("choose"))
        assertNull(cues.forStage("result"))
        assertNull(cues.forStage("identityError"))
        assertNull(cues.forStage("unknown"))
    }

    @Test fun approvalProgressReflectsOnlyObservedFlagsNotGuesses() {
        val none = requireNotNull(cues.forStage("approval"))
        assertTrue(none.heading.contains("Both people"))
        assertTrue(none.next.contains("Only approve"))
        val local = requireNotNull(cues.forStage("approval", localApprovalObserved = true))
        assertTrue(local.heading.contains("Waiting for the other"))
        assertFalse(local.next.contains("ready", ignoreCase = true))
        val peer = requireNotNull(cues.forStage("approval", peerApprovalObserved = true))
        assertTrue(peer.heading.contains("other phone approved"))
        assertTrue(peer.next.contains("still need to decide"))
        val both = requireNotNull(cues.forStage("approval",
            localApprovalObserved = true, peerApprovalObserved = true))
        assertTrue(both.heading.contains("Both test approvals"))
        assertTrue(both.next.contains("final checks"))
        val final = requireNotNull(cues.forStage("approval",
            localApprovalObserved = true, peerApprovalObserved = true,
            peerConfirmationObserved = true))
        assertTrue(final.heading.contains("Waiting for both phones"))
        assertTrue(final.next.contains("Do not send"))
        // A final confirmation flag in isolation cannot upgrade local/peer
        // approvals, even if an upstream diagnostic has an inconsistent state.
        val impossible = requireNotNull(cues.forStage("approval",
            localConfirmationObserved = true))
        assertEquals(none, impossible)
    }

    @Test fun onlyActualDemoStageShowsActiveTestChat() {
        val demo = requireNotNull(cues.forStage("demo"))
        assertEquals("TEST CONVERSATION • ACTIVE", demo.phase)
        assertTrue(demo.next.contains("not reviewed secure messaging"))
        for (stage in listOf("showHost", "scanHost", "showReply", "scanReply",
                "connecting", "approval")) {
            val hint = requireNotNull(cues.forStage(stage))
            assertFalse(hint.phase.contains("ACTIVE"))
            assertFalse(hint.heading.contains("chat is available"))
        }
    }
}
