"""M4-UI01 progressive help: visibility and disclosure boundaries (not screenshot QA)."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"


class M4Ui01ProgressiveHelp(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.ui = (MAIN / "ui/FieldDisclosures.kt").read_text()
        cls.main = (MAIN / "MainActivity.kt").read_text()
        cls.pairing = (MAIN / "pairing/LocalPairingTestScreen.kt").read_text()
        cls.privacy = (MAIN / "privacy/PrivacyPreviewContent.kt").read_text()
        cls.capabilities = (MAIN / "model/ReleaseCapabilities.kt").read_text()

    def test_tooltips_are_visible_tappable_and_work_without_hover(self):
        for s in (
            "internal fun FieldHelp(",
            "internal fun FieldDisclosureCard(",
            "rememberSaveable { mutableStateOf(false) }",
            ".heightIn(min = 48.dp)",
            "stateDescription = if (expanded) \"Expanded\" else \"Collapsed\"",
            "onClick = { expanded = !expanded }",
            "if (expanded) {",
        ):
            self.assertIn(s, self.ui, s)
        for bad in ("onLongClick", "onHover", "TooltipBox(",
                    "SharedPreferences", "ClipboardManager",
                    "WifiP2pManager", "LanDemoSession", "Socket("):
            self.assertNotIn(bad, self.ui, bad)

    def test_primary_path_remains_clear_and_safety_remains_visible(self):
        self.assertIn('Text("CONNECT NEARBY"', self.main)
        self.assertIn('onChatPreview = { navigate("connect") }', self.main)
        self.assertIn('question = "What can this preview do?"', self.main)
        self.assertIn("Avoid private or emergency conversations.", self.main)
        for s in (
            "FieldSectionHeader(\"TRANSPARENCY\", \"Current preview\")",
            "do not use it for sensitive conversations.",
            "FieldHelp(", "answer = PrivacyPreviewContent.introduction",
            "PrivacyPreviewContent.sections.forEach",
            "FieldDisclosureCard(",
            "fullText = section.description",
            "PUBLIC POLICY NOT YET PUBLISHED",
        ):
            self.assertIn(s, self.main, s)
        self.assertIn("PrivacyPreviewContent.policyNote", self.main)
        self.assertIn("No production messaging or voice", self.main)

    def test_long_settings_and_future_method_details_are_optional(self):
        for s in (
            'title = "Conversation privacy"',
            "futureHistoryExplanation", "futureTimerExplanation",
            "futureTrustExplanation", 'title = "Why codes and approvals?"',
            "NearbyDiscoveryPolicy.privacyStep",
        ):
            self.assertIn(s, self.main, s)
        self.assertIn('summary = "Live LAN chat offers separate optional encrypted history and remembered public peer identity in DEBUG only. No auto-pairing."', self.main)

    def test_pairing_buttons_first_not_technical_signer_identifiers(self):
        section = self.pairing.split('Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {', 1)[1]
        self.assertIn('if (stage != "demo") {', section)
        self.assertIn('if (showBuildEvidence) {', section)
        self.assertIn('?  Advanced: build details', section)
        self.assertIn('BuildEvidenceReport.brief(installedBuildEvidence)', section)
        self.assertIn('BuildEvidenceReport.format(installedBuildEvidence)', section)
        self.assertIn('Text("Show a code")', section)
        self.assertIn('Text("Scan a code")', section)
        self.assertIn('?  Why scan two codes?', section)
        self.assertNotIn("Current method: same-Wi-Fi test. Bluetooth and Wi-Fi Direct are planned", section)
        self.assertIn("showBuildEvidence = false", self.pairing)
        self.assertIn("var showBuildEvidence by remember { mutableStateOf(false) }", self.pairing)

    def test_privacy_accuracy_and_release_flags(self):
        self.assertIn("Debug Wi-Fi Direct tests may", self.privacy)
        self.assertIn("permission on older Android versions", self.privacy)
        self.assertIn("no coordinates are", self.privacy)
        self.assertIn("Bluetooth and Wi-Fi Direct", self.privacy)
        self.assertIn("debug encrypted TEXT/ACK", self.privacy)
        self.assertIn("per-conversation choice", self.privacy)
        self.assertIn("Production secure messaging remains disabled", self.privacy)
        for flag in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn("const val " + flag + " = false", self.capabilities)


if __name__ == "__main__":
    unittest.main()
