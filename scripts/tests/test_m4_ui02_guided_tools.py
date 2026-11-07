"""M4-UI02: novice-friendly Developer Tools and QR guidance, zero radio authority."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"


class M4Ui02GuidedDeveloperTools(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.main = (MAIN / "MainActivity.kt").read_text()
        cls.pairing = (MAIN / "pairing/LocalPairingTestScreen.kt").read_text()
        cls.recovery = (MAIN / "pairing/NearbyRecoveryGuide.kt").read_text()
        cls.flags = (MAIN / "model/ReleaseCapabilities.kt").read_text()
        cls.developer = cls.main.split("private fun DeveloperScreen(", 1)[1].split(
            "private fun HelpScreen(", 1)[0]
        cls.help = cls.main.split("private fun HelpScreen(", 1)[1].split(
            "private fun PrivacyInfoScreen(", 1)[0]

    def test_developer_page_is_task_first_and_radio_tests_are_optional(self):
        for text in (
            'FieldSectionHeader("OPTIONAL TOOLS", "Testing and setup")',
            'SectionLabel("ON THIS PHONE")',
            'title = "Local device identity"',
            'title = "Text layout sandbox"',
            "does not send messages",
            'question = "What is a device identity?"',
            'SectionLabel("ADVANCED CONNECTION CHECKS")',
            'title = "Same-Wi-Fi connection check"',
            'var showRadioExperiments by rememberSaveable { mutableStateOf(false) }',
            'if (debuggable) {',
            'onClick = { showRadioExperiments = !showRadioExperiments }',
            'if (showRadioExperiments) {',
            'title = "Bluetooth discovery test"',
            'title = "Wi-Fi Direct device preflight"',
            'question = "What is a connection handshake?"',
            "DEVELOPMENT TEST ONLY",
            "sensitive",
        ):
            self.assertIn(text, self.developer, text)
        self.assertLess(self.developer.index('title = "Local device identity"'),
                        self.developer.index('if (showRadioExperiments) {'))
        self.assertIn("onWifiDirectPreflight", self.developer)
        self.assertIn("onBluetoothScan", self.developer)
        self.assertIn("onLanTest", self.developer)
        for forbidden in (
            "WifiP2pManager", "BluetoothAdapter",
            "discoverPeers(", "startDiscovery(", "connect(",
            "rememberCoroutineScope()", "requestPermissions(",
            "sendDebugText(", "approveForTest(",
        ):
            self.assertNotIn(forbidden, self.developer, forbidden)

    def test_offline_help_is_three_plain_steps_with_specific_optional_answers(self):
        for fragment in (
            "GETTING STARTED", "StepLine(\"01\", \"Use the same Wi-Fi\"",
            "StepLine(\"02\", \"Check the other phone\"",
            "StepLine(\"03\", \"Try a test conversation\"",
            'question = "Do we need internet?"',
            'question = "Why do both people approve?"',
            'question = "Will the app save my conversations?"',
            "not guaranteed memory erasure",
            "production messaging",
            "independent security review",
            "onPrivacy",
        ):
            self.assertIn(fragment.lower(), self.help.lower(), fragment)
        self.assertNotIn("LanDemoSession", self.help)

    def test_connection_buttons_stay_primary_and_build_evidence_remains_optional(self):
        p = self.pairing
        self.assertIn('Text("Show a code")', p)
        self.assertIn('Text("Scan a code")', p)
        self.assertIn('question = "Which button should I choose?"', p)
        self.assertIn('Text("?  Why scan two codes?")', p)
        self.assertIn("showBuildEvidence = !showBuildEvidence", p)
        self.assertIn("if (showBuildEvidence) {", p)
        self.assertIn("BuildEvidenceReport.format(installedBuildEvidence)", p)
        self.assertLess(p.index("FieldFrame(accent = true)"),
                        p.index('Text(if (showBuildEvidence)'))
        self.assertLess(p.index('Text("Show a code")'),
                        p.index('Text(if (showBuildEvidence)'))
        self.assertIn("Wi-Fi appears available on this phone.", self.recovery)
        self.assertIn("Both phones still need the same Wi-Fi.", self.recovery)

    def test_approval_warning_does_not_understate_debug_text_transmission(self):
        approval = self.pairing.split('"approval" -> {', 1)[1].split('"demo" -> {', 1)[0]
        for fragment in (
            "Approve this test connection?",
            "the person holding the other phone",
            "UNREVIEWED DEBUG TEST",
            "short typed TEST messages and preset phrases can be exchanged",
            "Never send secrets",
            "Voice stays disabled",
            'question = "What did Eden Comms check?"',
            "pinned TLS",
            "Approve this debug test",
            "Decline and disconnect",
        ):
            self.assertIn(fragment, approval, fragment)
        self.assertNotIn("Approval allows ONLY four fixed", approval)
        self.assertNotIn("never typed messages", approval)
        self.assertIn("probe?.approveForTest()", approval)
        self.assertIn("ending?.declineForTest()", approval)
        for flag in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn("const val " + flag + " = false", self.flags)


if __name__ == "__main__":
    unittest.main()
