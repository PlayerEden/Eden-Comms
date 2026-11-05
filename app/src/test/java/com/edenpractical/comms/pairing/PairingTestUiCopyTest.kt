package com.edenpractical.comms.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingTestUiCopyTest {
    private val copy = PairingTestUiCopy

    @Test fun progressDoesNotPretendOneQrIsEnough() {
        assertEquals("PAIRING • STEP 1 OF 2", copy.progress("showHost"))
        assertEquals("PAIRING • STEP 1 OF 2", copy.progress("scanHost"))
        assertEquals("PAIRING • STEP 2 OF 2", copy.progress("showReply"))
        assertEquals("PAIRING • STEP 2 OF 2", copy.progress("scanReply"))
    }

    @Test fun connectionProgressSeparatesLiveTestFromVerification() {
        assertEquals("LOCAL CONNECTION • VERIFYING", copy.progress("connecting"))
        assertEquals("LOCAL CONNECTION • TEST ACTIVE", copy.progress("demo"))
        assertEquals("CONNECTION TEST • SUMMARY", copy.progress("result"))
        assertNull(copy.progress("checking"))
        assertNull(copy.progress("choose"))
        assertNull(copy.progress("identityError"))
    }

    @Test fun approvalIsASeparateStageAndMayEndAfterSuccessfulTls() {
        assertEquals("LOCAL CONNECTION • TWO-PERSON TEST APPROVAL",
            copy.progress("approval"))
        assertEquals("Test link ended before final agreement",
            copy.resultHeading(false, tlsAndQrReady = true))
        val summary = copy.resultSummary(
            false, sent = 0, received = 0, tlsAndQrReady = true
        )
        assertTrue(summary.contains("TLS/QR/channel-binding test passed"))
        assertTrue(summary.contains("final two-way confirmations were not completed"))
        assertFalse(summary.contains("No personal messages"))
    }

    @Test fun narrowOrLargeTextUsesOneColumnActions() {
        assertTrue(copy.useStackedActions(320f, 1f))
        assertTrue(copy.useStackedActions(359f, 1f))
        assertTrue(copy.useStackedActions(400f, 1.3f))
        assertTrue(copy.useStackedActions(500f, 2f))
        assertFalse(copy.useStackedActions(360f, 1f))
        assertFalse(copy.useStackedActions(600f, 1.2f))
    }

    @Test fun summaryNeverClaimsDeliveryOrRealMessagingPermission() {
        val summary = copy.resultSummary(true, sent = 3, received = 2)
        assertEquals("Test connection established", copy.resultHeading(true))
        assertTrue(summary.contains("wrote 3 preset phrase(s) to TLS"))
        assertTrue(summary.contains("received 2"))
        assertTrue(summary.contains("does not mean delivered or read"))
        assertFalse(summary.contains("end-to-end encrypted messaging ready"))
        assertFalse(summary.contains("approved"))
    }

    @Test fun failureSummaryReportsIncompleteTestWithoutFalseReceipt() {
        val summary = copy.resultSummary(false, sent = 0, received = 0)
        assertEquals("Connection not established", copy.resultHeading(false))
        assertTrue(summary.contains("two fresh codes"))
        assertFalse(summary.contains("delivered"))
    }

    @Test fun invalidCountersCannotBecomeAValidTestSummary() {
        assertThrows(IllegalArgumentException::class.java) {
            copy.resultSummary(true, -1, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            copy.resultSummary(true, 0, -1)
        }
    }
}
