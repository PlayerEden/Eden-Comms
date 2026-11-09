"""M3g guardrails: code-level UI/privacy requirements, not device security QA."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"


class SessionPrivacyContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.chat = (MAIN / "pairing/DebugConversationPanel.kt").read_text()
        cls.pairing = (MAIN / "pairing/LocalPairingTestScreen.kt").read_text()
        cls.record = (MAIN / "pairing/DebugTextTranscript.kt").read_text()
        cls.settings = (MAIN / "MainActivity.kt").read_text()
        cls.disclosure = (MAIN / "privacy/PrivacyPreviewContent.kt").read_text()
        cls.future = (MAIN / "privacy/ConversationPrivacyContent.kt").read_text()
        cls.flags = (MAIN / "model/ReleaseCapabilities.kt").read_text()

    def test_privacy_curtain_does_not_render_message_bubbles_or_composer_when_hidden(self):
        self.assertIn("if (hidden) {", self.chat)
        self.assertIn("Conversation hidden on this screen", self.chat)
        self.assertIn("Hide conversation", self.chat)
        self.assertIn("Show conversation", self.chat)
        self.assertIn("onToggleHidden: () -> Unit", self.chat)
        self.assertIn("onClearRequested: () -> Unit", self.chat)
        self.assertLess(self.chat.index("} else {\n            Box("),
                        self.chat.index("transcript.visible.forEach"))
        self.assertNotIn("SharedPreferences", self.chat)
        self.assertNotIn("rememberSaveable", self.chat)

    def test_clear_is_confirmed_local_in_memory_and_does_not_close_socket(self):
        self.assertIn('if (showClearConfirmation && stage == "demo")', self.pairing)
        self.assertIn('Text("Clear this phone")', self.pairing)
        self.assertIn('Text("Cancel")', self.pairing)
        self.assertIn("debugText = debugText.clearVisible()", self.pairing)
        self.assertIn('textDraft = ""', self.pairing)
        self.assertIn("onClearRequested = { showClearConfirmation = true }", self.pairing)
        self.assertIn("fun clearVisible(): DebugTextTranscript", self.record)
        self.assertIn("lastWrittenId, lastReceivedId", self.record)
        self.assertIn("id <= lastId", self.record)
        clear_action = self.pairing.split('Text("Clear this phone")', 1)[0][-500:]
        self.assertNotIn("probe?.close()", clear_action)
        self.assertNotIn("endByUser()", clear_action)

    def test_settings_future_retention_status_is_not_an_enabled_persistence_switch(self):
        self.assertIn('SectionLabel("PRIVACY & CONVERSATIONS")', self.settings)
        self.assertIn("ConversationPrivacyContent.currentState", self.settings)
        for future in ("futureHistoryState", "futureTimerState", "futureTrustState"):
            self.assertIn("ConversationPrivacyContent." + future, self.settings)
        self.assertIn('const val currentState = "Session only • currently active"', self.future)
        self.assertEqual(2, self.future.count('State = "Not available yet"'))
        self.assertIn('const val futureTimerState = "Available in nearby test chat"', self.future)
        self.assertIn("onDisappearAfterChange = { newDuration ->", self.pairing)
        self.assertIn(".pruneExpired(SystemClock.elapsedRealtime())", self.pairing)
        self.assertIn("LaunchedEffect(stage, debugText)", self.pairing)
        self.assertIn("shortenVisibleExpiry(newDuration)", self.pairing)
        self.assertIn("disappearAfterMs = disappearAfterMs", self.pairing)
        self.assertIn("onDisappearAfterChange: (Long) -> Unit", self.chat)
        self.assertIn("DebugLocalDisappearingPolicy.choices.forEach", self.chat)
        self.assertNotIn("openFileOutput", self.chat + self.pairing)
        self.assertNotIn("SQLiteDatabase", self.chat + self.pairing)

    def test_privacy_disclosure_describes_real_debug_transmission_not_old_local_only_claim(self):
        self.assertIn("short typed TEST messages can", self.disclosure)
        self.assertIn("travel between the phones", self.disclosure)
        self.assertIn("local-only text layout sandbox never transmits", self.disclosure.replace('" +\n        "', ""))
        self.assertNotIn("You cannot send your own messages", self.disclosure)
        self.assertNotIn("There are no Eden Comms accounts, user-written messages", self.disclosure)
        for key in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn("const val " + key + " = false", self.flags)


if __name__ == "__main__":
    unittest.main()
