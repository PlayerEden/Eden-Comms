"""M4-UI09 source guard: adaptive top bar and unchanged Back/security authority.
This does not substitute for emulator screenshots, physical TalkBack, or UX QA.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = (ROOT / "app/src/main/java/com/edenpractical/comms/MainActivity.kt").read_text()
RULE = (ROOT / "app/src/main/java/com/edenpractical/comms/ui/TopBarLayout.kt").read_text()
FLAGS = (ROOT / "app/src/main/java/com/edenpractical/comms/model/ReleaseCapabilities.kt").read_text()


class M4Ui09TopBar(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.header = MAIN.split("private fun TopBar(", 1)[1].split(
            "private fun HomeScreen(", 1)[0]

    def test_title_reflows_on_narrow_or_enlarged_font(self):
        self.assertIn("BoxWithConstraints(modifier = Modifier.fillMaxWidth())", self.header)
        self.assertIn("TopBarLayout.maxTitleLines(", self.header)
        self.assertIn("maxWidth.value, LocalDensity.current.fontScale", self.header)
        self.assertEqual(2, self.header.count("maxLines = titleLines"))
        self.assertEqual(2, self.header.count("Modifier.weight(1f)"))
        self.assertIn(".heightIn(min = 64.dp)", self.header)
        self.assertIn("softWrap = true", self.header)
        self.assertIn("TEST CONVERSATION", self.header)

    def test_back_touch_and_session_confirmation_preserved(self):
        self.assertIn("onClick = onBack", self.header)
        self.assertIn('Modifier.size(48.dp).semantics { contentDescription = "Back" }', self.header)
        self.assertIn("BackHandler(enabled = history.size > 1) { back() }", MAIN)
        self.assertIn("PairingSessionUi.needsLeaveWarning(pairingStage)", MAIN)
        self.assertIn('Text("Leave and disconnect")', MAIN)
        self.assertIn("NavigationHistory.pop(history)", MAIN)

    def test_rule_is_pure_and_production_messaging_stays_off(self):
        self.assertIn("availableWidthDp < 360f || fontScale >= 1.3f", RULE)
        for blocked in ("android.", "androidx.", "Socket(", "approveForTest(",
                        "sendDebugText(", "SharedPreferences", "rememberSaveable"):
            self.assertNotIn(blocked, RULE)
        for flag in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn("const val " + flag + " = false", FLAGS)


if __name__ == "__main__":
    unittest.main()
