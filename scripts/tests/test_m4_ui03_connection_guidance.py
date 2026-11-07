"""M4-UI03 read-only pairing state cues, visible recovery and optional QA details."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"


class M4Ui03ConnectionGuidance(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.cues = (MAIN / "pairing/NearbyConnectionCues.kt").read_text()
        cls.pairing = (MAIN / "pairing/LocalPairingTestScreen.kt").read_text()
        cls.recovery = (MAIN / "pairing/NearbyRecoveryGuide.kt").read_text()
        cls.release = (MAIN / "model/ReleaseCapabilities.kt").read_text()

    def test_cue_is_pure_and_not_an_authorization_or_transport(self):
        for text in (
            'internal object NearbyConnectionCues',
            'fun forStage(',
            'data class Cue(',
            '"showHost" -> Cue(',
            '"scanHost" -> Cue(',
            '"scanReply" -> Cue(',
            '"showReply" -> Cue(',
            '"connecting" -> Cue(',
            '"approval" -> when',
            '"demo" -> Cue(',
            '"TEST CONVERSATION • ACTIVE"',
            'else -> null',
            'localApprovalObserved && peerApprovalObserved',
            'localConfirmationObserved || peerConfirmationObserved',
            'Neither person is approved yet',
            'This is not reviewed secure messaging.',
        ):
            self.assertIn(text, self.cues, text)
        for forbidden in (
            "import android.", "import androidx.", "import java.net.",
            "Socket(", "WifiP2pManager", "BluetoothAdapter",
            "SharedPreferences", "DataStore", "openFileOutput(", "System.currentTimeMillis",
            "requestPermissions(", "rememberSaveable", "LanDemoSession",
            "sendDebugText(", "approveForTest(", "connect(", "Log."
        ):
            self.assertNotIn(forbidden, self.cues, forbidden)

    def test_live_stage_guidance_uses_observed_approval_only(self):
        for s in (
            "NearbyConnectionCues.forStage(",
            "localApprovalObserved = qaLocalApprovalWritten ||",
            "approvalStage == DebugApprovalGate.State.LOCAL_ONLY",
            "peerApprovalObserved = qaPeerApprovalObserved ||",
            "approvalStage == DebugApprovalGate.State.PEER_ONLY",
            "localConfirmationObserved = qaLocalConfirmationWritten",
            "peerConfirmationObserved = qaPeerConfirmationObserved",
            "if (stage != \"demo\") {",
            "guidance.heading",
            "guidance.next",
            "liveRegion = LiveRegionMode.Polite"
        ):
            self.assertIn(s, self.pairing, s)
        self.assertIn('LocalPairingQrDisplay(qrText)', self.pairing)
        self.assertIn('LocalPairingQrScanner(', self.pairing)
        self.assertIn('Text("Show a code")', self.pairing)
        self.assertIn('Text("Scan a code")', self.pairing)
        self.assertIn('onClick = { reset() }', self.pairing)
        self.assertIn('probe?.approveForTest()', self.pairing)
        self.assertIn('ending?.declineForTest()', self.pairing)
        self.assertIn('ending?.endByUser()', self.pairing)

    def test_result_prioritizes_next_step_and_fresh_reset_not_qa_diagnostics(self):
        result = self.pairing.split('"result" -> {', 1)[1].split('"identityError" -> {', 1)[0]
        for s in (
            "NearbyRecoveryGuide.forResult(",
            "recovery.title", "recovery.nextStep",
            "Production", "best effort", "voice remain disabled",
            "if (recovery.wifiCheckHelpful) {",
            "onClick = { openWifiSettings() }",
            "refreshWifiStatus()",
            "Start over with fresh QR codes",
            "showResultDetails = !showResultDetails",
            "if (showResultDetails) {",
            "PairingTestUiCopy.resultSummary(",
            "LocalQaReport.format(observation)",
            "Copy this phone's test summary",
            "Clipboard content can be visible to the OS and other apps."
        ):
            self.assertIn(s, result, s)
        self.assertLess(result.index("recovery.nextStep"),
                        result.index("Start over with fresh QR codes"))
        self.assertLess(result.index("Start over with fresh QR codes"),
                        result.index("if (showResultDetails) {"))
        self.assertLess(result.index("if (showResultDetails) {"),
                        result.index("LocalQaReport.format(observation)"))
        self.assertNotIn("runProbe(", result)
        self.assertNotIn("sendDebugText(", result)
        self.assertNotIn("decodeHost(", result)
        self.assertIn("showResultDetails = false", self.pairing)

    def test_failure_copy_does_not_reveal_identifiers_or_auto_retry(self):
        self.assertIn("The connection took too long", self.recovery)
        self.assertIn("Device verification did not match", self.recovery)
        self.assertIn("Do not bypass verification", self.recovery)
        self.assertIn("Keep both screens open", self.recovery)
        for forbidden in (
            "autoReconnect(", "autoApprove(", "PairingQrCodec.newHostInvite(",
            "rememberSaveable { mutableStateOf", "sharedPreferences.edit()"
        ):
            self.assertNotIn(forbidden, self.cues, forbidden)
        for flag in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn("const val " + flag + " = false", self.release)


if __name__ == "__main__":
    unittest.main()
