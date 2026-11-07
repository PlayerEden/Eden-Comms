"""Source-level M3f regression assertions for user-facing routing and boundaries.

These are intentionally *not* Android screenshot tests or proof of two-phone
connection. They catch accidental regressions in our simple Compose nav
wiring, presentation order and explicit debug-only constraints.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]


class ChatFirstUiContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.main = (ROOT / "app/src/main/java/com/edenpractical/comms/MainActivity.kt").read_text()
        cls.pairing = (ROOT / "app/src/main/java/com/edenpractical/comms/pairing/LocalPairingTestScreen.kt").read_text()
        cls.panel = (ROOT / "app/src/main/java/com/edenpractical/comms/pairing/DebugConversationPanel.kt").read_text()
        cls.session = (ROOT / "app/src/main/java/com/edenpractical/comms/transport/LanDemoSession.kt").read_text()

    def test_home_text_goes_to_nearby_chat_and_sandbox_remains_in_developer_tools(self):
        self.assertEqual(2, self.main.count('onChatPreview = { navigate("pairing-test") }'))
        self.assertIn('detail = "Nearby test chat"', self.main)
        self.assertIn('onChatSandbox = { navigate("chat-preview") }', self.main)
        self.assertIn('title = "Text layout sandbox"', self.main)
        self.assertIn('onPairingTest = { navigate("pairing-test") }', self.main)

    def test_chat_precedes_collapsed_presets_and_debug_detail(self):
        active = self.pairing.split('"demo" -> {', 1)[1].split('"result" -> {', 1)[0]
        self.assertLess(active.index("DebugConversationPanel("),
                        active.index("if (showTestTools) {"))
        self.assertIn('if (showTestTools) {', active)
        self.assertIn('TestPacketCodec.phrases.chunked(columns)', active)
        self.assertIn('probe?.sendDebugText(textDraft)', active)
        self.assertIn("debugText = debugText.finish()", active)
        self.assertIn('textDraft = ""', active)
        self.assertIn("ending?.endByUser()", active)
        self.assertIn("End chat and disconnect", active)

    def test_temporary_chat_remains_bounded_and_non_receipted(self):
        self.assertIn(".height(220.dp)", self.panel)
        self.assertIn(".verticalScroll(scroll)", self.panel)
        self.assertIn("DebugTextDraftPolicy.accepts(value)", self.panel)
        self.assertIn("DebugTextDraftPolicy.maySend(draft)", self.panel)
        self.assertIn("Not a delivery or read receipt", self.panel)
        # M4-UI04 separates the readable participant and observation;
        # neither ever claims a remotely delivered/read message.
        copy = (ROOT / "app/src/main/java/com/edenpractical/comms/pairing/DebugConversationUiCopy.kt").read_text()
        self.assertIn('"Other phone"', copy)
        self.assertIn('"Received on this phone"', copy)
        self.assertIn('"Written to test link"', copy)
        self.assertIn("DebugConversationUiCopy.observation(entry.direction)", self.panel)
        self.assertNotIn("rememberSaveable", self.panel)
        self.assertNotIn("SharedPreferences", self.panel)
        self.assertNotIn("LanDemoSession", self.panel)
        self.assertIn("FLAG_DEBUGGABLE", self.session)
        self.assertIn("approvalGate.maySendPreset()", self.session)

    def test_terminal_paths_erase_debug_text_and_draft(self):
        self.assertIn("if (event == Lifecycle.Event.ON_STOP", self.pairing)
        for marker in ('LanDemoSession.Phase.ENDED -> {',
                       'LanDemoSession.Phase.FAILED -> {',
                       'ending?.declineForTest()',
                       'ending?.endByUser()',
                       'onDispose {'):
            region = self.pairing.split(marker, 1)[1][:1000]
            self.assertIn("debugText = debugText.finish()", region, marker)
            self.assertIn('textDraft = ""', region, marker)


if __name__ == "__main__":
    unittest.main()
