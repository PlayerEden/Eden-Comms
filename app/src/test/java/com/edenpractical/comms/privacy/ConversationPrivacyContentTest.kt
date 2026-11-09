package com.edenpractical.comms.privacy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationPrivacyContentTest {
    @Test fun onlySessionOnlyIsDescribedAsActiveAndNothingClaimsDiskHistory() {
        assertTrue(ConversationPrivacyContent.currentState.contains("Session only"))
        assertTrue(ConversationPrivacyContent.currentExplanation.contains(
            "does not intentionally save a conversation history"))
        assertTrue(ConversationPrivacyContent.futureHistoryState.contains("Not available"))
        assertTrue(ConversationPrivacyContent.futureTimerState.contains("Available"))
        assertTrue(ConversationPrivacyContent.futureTimerExplanation.contains("this phone only"))
        assertTrue(ConversationPrivacyContent.futureTimerExplanation.contains("never saved"))
        assertTrue(ConversationPrivacyContent.futureTrustState.contains("Not available"))
        assertTrue(ConversationPrivacyContent.futureHistoryExplanation.contains("30 days"))
        assertTrue(ConversationPrivacyContent.futureHistoryExplanation.contains(
            "no saving switch"))
        assertFalse(ConversationPrivacyContent.currentExplanation.contains(
            "deleted everywhere"))
    }

    @Test fun hideIsNotEraseAndClearCannotDeletePeerCopies() {
        val curtain = ConversationPrivacyContent.curtainExplanation
        assertTrue(curtain.contains("without disconnecting or deleting"))
        assertTrue(curtain.contains("continue"))
        val clear = ConversationPrivacyContent.clearExplanation
        assertTrue(clear.contains("this phone"))
        assertTrue(clear.contains("connection stays active"))
        assertTrue(clear.contains("newly arriving or pending"))
        assertTrue(clear.contains("cannot delete another person's"))
        val caution = ConversationPrivacyContent.privacyLimits
        assertTrue(caution.contains("not secure erasure"))
        assertTrue(caution.contains("unreviewed debug build"))
    }

    @Test fun ownKeystoreIdentityAndFuturePeerTrustAreSeparate() {
        assertTrue(ConversationPrivacyContent.futureTrustExplanation.contains(
            "Android Keystore identity"))
        assertTrue(ConversationPrivacyContent.futureTrustExplanation.contains(
            "does not save an approved peer"))
        assertTrue(ConversationPrivacyContent.futureTrustExplanation.contains(
            "separate from message history"))
    }
}
