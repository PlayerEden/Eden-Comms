package com.edenpractical.comms.privacy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationPrivacyContentTest {
    @Test fun sessionOnlyDefaultAndDebugLocalHistoryAreAccuratelySeparated() {
        assertTrue(ConversationPrivacyContent.currentState.contains("Session only"))
        assertTrue(ConversationPrivacyContent.currentExplanation.contains(
            "does not intentionally save a conversation history"))
        assertTrue(ConversationPrivacyContent.futureHistoryState.contains("Opt-in test"))
        assertTrue(ConversationPrivacyContent.futureHistoryState.contains("LAN debug only"))
        assertTrue(ConversationPrivacyContent.futureTimerState.contains("Available"))
        assertTrue(ConversationPrivacyContent.futureTimerExplanation.contains("this phone only"))
        assertTrue(ConversationPrivacyContent.futureTimerExplanation.contains("never saved"))
        assertTrue(ConversationPrivacyContent.futureTrustState.contains("Opt-in test"))
        assertTrue(ConversationPrivacyContent.futureHistoryExplanation.contains("30 days"))
        assertTrue(ConversationPrivacyContent.futureHistoryExplanation.contains(
            "Session-only is the default"))
        assertTrue(ConversationPrivacyContent.futureHistoryExplanation.contains(
            "unavailable in release"))
        assertTrue(ConversationPrivacyContent.futureHistoryExplanation.contains(
            "delete that phone's saved history"))
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
        assertTrue(clear.contains("DOES NOT erase"))
        assertTrue(clear.contains("Delete saved history"))
        assertTrue(clear.contains("other phone's"))
        val caution = ConversationPrivacyContent.privacyLimits
        assertTrue(caution.contains("not secure erasure"))
        assertTrue(caution.contains("unreviewed debug build"))
    }

    @Test fun savedPublicPeerIdentityAndMessageHistoryRequireSeparateConsent() {
        assertTrue(ConversationPrivacyContent.futureTrustExplanation.contains(
            "Android Keystore"))
        assertTrue(ConversationPrivacyContent.futureTrustExplanation.contains(
            "PUBLIC certificate fingerprint"))
        assertTrue(ConversationPrivacyContent.futureTrustExplanation.contains(
            "separate from message history"))
        assertTrue(ConversationPrivacyContent.futureTrustExplanation.contains(
            "Future chats STILL require fresh QR"))
        assertTrue(ConversationPrivacyContent.futureTrustExplanation.contains(
            "unavailable in release"))
    }
}
