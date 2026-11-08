"""M4-UI08 responsive Home choices and release boundary source checks."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "app/src/main/java/com/edenpractical/comms"


class M4Ui08Home(unittest.TestCase):
    def test_home_small_width_and_large_font_stacks_modes(self):
        main = (SRC / "MainActivity.kt").read_text()
        home = main.split("private fun HomeScreen(", 1)[1].split(
            "private fun NearbyScreen(", 1)[0]
        self.assertIn("HomeModeLayout.shouldStack(", home)
        self.assertIn("maxWidth.value, LocalDensity.current.fontScale", home)
        self.assertIn("if (stackModes)", home)
        self.assertIn("modifier = Modifier.fillMaxWidth()", home)
        self.assertIn("modifier = Modifier.weight(1f)", home)
        self.assertEqual(4, home.count("CompactModeTile("))
        self.assertEqual(2, home.count('detail = "Development test only"'))
        self.assertEqual(2, home.count('detail = "Not available"'))
        self.assertEqual(2, home.count("onClick = onPtt"))
        self.assertEqual(2, home.count("onClick = onChatPreview"))
        self.assertIn('Text("CONNECT NEARBY"', home)
        self.assertIn("DEVELOPMENT TEST  /  NO PRIVATE MESSAGING", home)

    def test_pure_layout_policy_never_grants_authority(self):
        policy = (SRC / "ui/HomeModeLayout.kt").read_text()
        self.assertIn("availableWidthDp < 360f || fontScale >= 1.3f", policy)
        for forbidden in ("import android.", "import androidx.", "Socket(",
                          "sendDebugText(", "approveForTest(", "SharedPreferences"):
            self.assertNotIn(forbidden, policy)
        flags = (SRC / "model/ReleaseCapabilities.kt").read_text()
        for key in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn("const val " + key + " = false", flags)


if __name__ == "__main__":
    unittest.main()
