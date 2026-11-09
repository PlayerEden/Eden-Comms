package com.edenpractical.comms.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyPreviewContentTest {
    @Test fun disclosureIsClearlyDevelopmentOnlyAndReflectsDebugTypedText() {
        assertEquals("DEVELOPMENT PREVIEW", PrivacyPreviewContent.status)
        assertTrue(PrivacyPreviewContent.introduction.contains("short typed TEST messages"))
        assertTrue(PrivacyPreviewContent.introduction.contains("both phones approve and confirm"))
        assertTrue(PrivacyPreviewContent.introduction.contains("Debug LAN push-to-talk"))
        assertTrue(PrivacyPreviewContent.introduction.contains("not a production feature"))
        assertTrue(PrivacyPreviewContent.introduction.contains("unreviewed"))
        assertFalse(PrivacyPreviewContent.introduction.contains("cannot send your own messages"))
        assertTrue(PrivacyPreviewContent.policyNote.contains("not the published"))
        assertTrue(PrivacyPreviewContent.policyNote.contains("verified publisher contact"))
    }

    @Test fun currentDataFlowsAreCompleteAndSeparateSandboxFromActualTestChat() {
        val text = PrivacyPreviewContent.sections.joinToString(" ") { it.description }
        assertEquals(6, PrivacyPreviewContent.sections.size)
        listOf(
            "Android Keystore",
            "appearance setting",
            "Wi-Fi address",
            "public certificate",
            "TLS channel-binding",
            "approval controls",
            "short typed test messages",
            "other phone receives",
            "local-only",
            "sandbox",
            "per-conversation choice",
            "Keystore AES-GCM",
            "no-backup storage",
            "no saved peer",
            "keyboard",
            "Camera",
            "INTERNET",
            "ACCESS_NETWORK_STATE",
            "microphone",
            "location",
            "photograph",
            "operating system",
            "no"
        ).forEach { word ->
            assertTrue("Missing current privacy disclosure: $word", text.contains(word))
        }
    }

    @Test fun unavailableFeaturesAreNotRepresentedAsActive() {
        val future = PrivacyPreviewContent.sections.last().description
        listOf("no Eden Comms accounts", "remembered", "encrypted test text",
            "disappearing", "Bluetooth", "voice", "Production secure messaging remains disabled"
        ).forEach { assertTrue("Missing future limitation: $it", future.contains(it)) }
        val all = PrivacyPreviewContent.introduction + " " +
            PrivacyPreviewContent.sections.joinToString(" ") { it.description }
        assertFalse(all.contains("anonymous and untraceable", ignoreCase = true))
        assertFalse(all.contains("secure for sensitive conversations", ignoreCase = true))
        assertFalse(all.contains("all data deleted permanently", ignoreCase = true))
        assertFalse(all.contains("encrypted messages are guaranteed private"))
    }
}
