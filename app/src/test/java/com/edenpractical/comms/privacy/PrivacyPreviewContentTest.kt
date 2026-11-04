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
        assertEquals(5, PrivacyPreviewContent.sections.size)
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
