"""Home visual-discovery regression for the actual Compose page.

A source guard complements (but does not replace) phone/emulator visual QA.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "app/src/main/java/com/edenpractical/comms/MainActivity.kt"
FLAGS = ROOT / "app/src/main/java/com/edenpractical/comms/model/ReleaseCapabilities.kt"
PREVIEW = ROOT / ".github/workflows/offline-preview-apk.yml"


class HomeScrollCueSourceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.ui = APP.read_text()

    def test_real_scroll_state_drives_bounded_home_only_hint(self):
        s = self.ui
        self.assertIn("val pageScroll = rememberScrollState()", s)
        self.assertIn(".verticalScroll(pageScroll)", s)
        self.assertIn('if (page == "home" &&', s)
        self.assertIn("pageScroll.canScrollForward &&", s)
        self.assertIn("pageScroll.value < 12", s)
        self.assertIn('"↓  SCROLL FOR MORE"', s)
        self.assertIn(".align(Alignment.BottomCenter)", s)
        self.assertIn(".heightIn(min = 48.dp)", s)
        self.assertIn("pageScroll.animateScrollTo(", s)
        self.assertIn(".coerceAtMost(pageScroll.maxValue)", s)
        self.assertNotIn(".verticalScroll(rememberScrollState())", s)

    def test_hint_cannot_start_connection_or_change_app_security(self):
        s = self.ui
        first = s.index('if (page == "home" &&\n                            pageScroll.canScrollForward')
        end = s.index("\n                    }\n                }\n            }", first)
        cue = s[first:end]
        for banned in (
            "navigate(", "startActivity(", "onConnect(", "onChatPreview(",
            "requestPermission", "offerText", "sendText", "connect(",
            "localVoice", "onApprovedIdentity",
        ):
            self.assertNotIn(banned, cue)
        flags = FLAGS.read_text()
        for name in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {name} = false", flags)

    def test_workflow_builds_actual_home_source_changes_for_owner_visual_review(self):
        workflow = PREVIEW.read_text()
        self.assertIn("- 'app/src/main/java/com/edenpractical/comms/MainActivity.kt'", workflow)
        self.assertIn("eden-comms-offline-preview", workflow)
        self.assertIn("actions/upload-artifact@v4", workflow)


if __name__ == "__main__":
    unittest.main()
