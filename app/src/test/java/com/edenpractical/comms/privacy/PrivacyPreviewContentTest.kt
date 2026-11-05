package com.edenpractical.comms.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyPreviewContentTest {
    @Test fun disclosureIsClearlyDevelopmentOnlyNotFinalPlayPolicy() {
        assertEquals("DEVELOPMENT PREVIEW", PrivacyPreviewContent.status)
        assertTrue(PrivacyPreviewContent.introduction.contains("cannot send your own messages"))
        assertTrue(PrivacyPreviewContent.policyNote.contains("not the published"))
        assertTrue(PrivacyPreviewContent.policyNote.contains("verified publisher contact"))
    }

    @Test fun currentDataFlowsArePresentAndUnderstandable() {
        val text = PrivacyPreviewContent.sections.joinToString(" ") { it.description }
        assertEquals(6, PrivacyPreviewContent.sections.size)
        assertTrue("The preview must be disclosed as on-device UI only",
            text.contains("local") && text.contains("keyboard") &&
                text.contains("does not intentionally save or transmit"))
        assertTrue("Peer messaging must still be unavailable",
            text.contains("real messaging") &&
                text.contains("exchanged with another phone"))
        listOf(
            "Android Keystore",
            "appearance setting",
            "Wi-Fi address",
            "public certificate",
            "Camera",
            "INTERNET",
            "ACCESS_NETWORK_STATE",
            "microphone",
            "location",
            "photograph",
            "operating system"
        ).forEach { word -> assertTrue("Missing privacy disclosure: $word", text.contains(word)) }
    }

    @Test fun noFalseClaimsAboutSecrecyOrReleasedFeatures() {
        val text = PrivacyPreviewContent.introduction + " " +
            PrivacyPreviewContent.sections.joinToString(" ") { it.description }
        assertTrue(text.contains("cannot protect") || text.contains("does not protect"))
        assertFalse(text.contains("anonymous and untraceable", ignoreCase = true))
        assertFalse(text.contains("secure for sensitive conversations", ignoreCase = true))
        assertFalse(text.contains("all data deleted permanently", ignoreCase = true))
    }
}
