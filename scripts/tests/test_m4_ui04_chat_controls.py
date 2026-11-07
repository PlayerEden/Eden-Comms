"""M4-UI04: accessible temporary chat with honest local events and reachable exit."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"

class M4Ui04ConversationContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.panel = (MAIN / "pairing/DebugConversationPanel.kt").read_text()
        cls.copy = (MAIN / "pairing/DebugConversationUiCopy.kt").read_text()
        cls.parent = (MAIN / "pairing/LocalPairingTestScreen.kt").read_text()
        cls.record = (MAIN / "pairing/DebugTextTranscript.kt").read_text()
        cls.flags = (MAIN / "model/ReleaseCapabilities.kt").read_text()

    def test_readable_real_message_labels_and_safety_not_fabricated_receipts(self):
        for fragment in (
            "DebugConversationUiCopy.participant(entry.direction)",
            "DebugConversationUiCopy.observation(entry.direction)",
            "entry.text", "transcript.visible.forEach",
            "writtenHere", "Arrangement.End", "Arrangement.Start",
            "material", "DEV TEST", "Not a delivery or read receipt",
            "Do not send sensitive information", "Voice is disabled",
            "Session-only", "Your keyboard or screenshots",
        ):
            if fragment != "material":
                self.assertIn(fragment, self.panel, fragment)
        self.assertIn('"Written to test link"', self.copy)
        self.assertIn('"Received on this phone"', self.copy)
        self.assertIn('"You"', self.copy)
        self.assertIn('"Other phone"', self.copy)
        self.assertNotIn('"Delivered"', self.copy)
        self.assertNotIn('"Read"', self.copy)

    def test_scroll_does_not_auto_steal_reading_position(self):
        for fragment in (
            ".height(220.dp)", ".verticalScroll(scroll)",
            "snapshotFlow { Triple(scroll.value, scroll.maxValue, scroll.isScrollInProgress) }",
            "if (moving && !autoScrolling)",
            "followNewest = maximum - position <= 64",
            "LaunchedEffect(transcript.writtenHere, transcript.receivedHere, hidden)",
            "if (!hidden && followNewest)",
            "withFrameNanos { }", "scroll.animateScrollTo(scroll.maxValue)",
            'Text("Jump to latest")', "followNewest = true",
        ):
            self.assertIn(fragment, self.panel, fragment)
        self.assertNotIn("SharedPreferences", self.panel)
        self.assertNotIn("rememberSaveable", self.panel)
        self.assertNotIn("openFileOutput(", self.panel)
        self.assertNotIn("Socket(", self.panel)

    def test_private_curtain_hides_both_bubbles_and_draft_composer(self):
        self.assertIn("if (hidden) {", self.panel)
        self.assertIn("Conversation hidden on this screen", self.panel)
        self.assertIn("} else {\n            Box(", self.panel)
        self.assertLess(self.panel.index("} else {\n            Box("),
                        self.panel.index("transcript.visible.forEach"))
        self.assertIn("if (DebugTextDraftPolicy.accepts(value)) onDraftChange(value)",
                      self.panel)
        self.assertIn("enabled = DebugTextDraftPolicy.maySend(draft)", self.panel)
        self.assertIn("onClick = onSend", self.panel)
        self.assertIn("onToggleHidden", self.panel)
        self.assertIn("onClearRequested", self.panel)

    def test_buttons_are_large_font_aware_and_exit_is_before_debug_diagnostics(self):
        self.assertIn("PairingTestUiCopy.useStackedActions(", self.panel)
        self.assertIn("LocalDensity.current.fontScale", self.panel)
        self.assertIn("if (stacked) {", self.panel)
        self.assertIn(".heightIn(min = 48.dp)", self.panel)
        self.assertIn('Text("Clear my screen")', self.panel)
        self.assertIn("Show conversation", self.panel)
        self.assertIn("Hide conversation", self.panel)
        active = self.parent.split('"demo" -> {', 1)[1].split('"result" -> {', 1)[0]
        self.assertLess(active.index("DebugConversationPanel("),
                        active.index('Text("End chat and disconnect")'))
        self.assertLess(active.index('Text("End chat and disconnect")'),
                        active.index('if (showTestTools) {'))
        self.assertIn("ending?.endByUser()", active)
        self.assertIn("clearTemporaryPairing()", active)
        self.assertIn("debugText = debugText.finish()", active)
        self.assertIn('textDraft = ""', active)
        self.assertIn("the other phone may still keep a copy", active)

    def test_unchanged_temporary_message_and_release_boundaries(self):
        self.assertIn("MAX_VISIBLE = 12", self.record)
        self.assertIn("State.ACTIVE", self.record)
        self.assertIn("fun clearVisible()", self.record)
        self.assertIn("fun finish()", self.record)
        self.assertIn("lastWrittenId, lastReceivedId", self.record)
        for blocked in (
            "SharedPreferences", "DataStore", "Socket(", "LanDemoSession",
            "sendDebugText(", "approveForTest(", "requestPermission",
            "ClipboardManager", "System.currentTimeMillis", "getLastKnownLocation"
        ):
            self.assertNotIn(blocked, self.panel + self.copy, blocked)
        for flag in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn("const val " + flag + " = false", self.flags)

if __name__ == "__main__":
    unittest.main()
