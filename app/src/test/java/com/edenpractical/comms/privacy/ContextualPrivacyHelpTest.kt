package com.edenpractical.comms.privacy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextualPrivacyHelpTest {
    @Test fun pairingExplainsSecondQrWithoutClaimingRealConsent() {
        val text = ContextualPrivacyHelp.PAIRING_BODY
        assertTrue(text.contains("both phones"))
        assertTrue(text.contains("first phone"))
        assertTrue(text.contains("second phone"))
        assertTrue(text.contains("development testing"))
        assertTrue(text.contains("not approval for real messaging or voice"))
        assertFalse(text.contains("guaranteed secure"))
    }

    @Test fun sessionOnlySeparatesCurrentDemoFromFutureTextAndLocalIdentity() {
        val text = ContextualPrivacyHelp.SESSION_ONLY_BODY
        assertTrue(text.contains("Today's development build"))
        assertTrue(text.contains("does not save a chat history"))
        assertTrue(text.contains("intended future default"))
        assertTrue(text.contains("Android Keystore device key"))
        assertTrue(text.contains("cannot erase their copy"))
        assertFalse(text.contains("automatically erase their messages"))
    }
}
