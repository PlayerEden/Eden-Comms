"""F2 Bluetooth diagnostic UI is QR-first, staged, and non-authoritative."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
DIR = ROOT / "app/src/debug/java/com/edenpractical/comms/bluetooth"
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"


class BluetoothGuideSourceContract(unittest.TestCase):
    def test_ui_puts_person_first_and_discovery_after_local_qr(self):
        activity = (DIR / "DebugBluetoothDiscoveryActivity.kt").read_text()
        self.assertEqual(1, activity.count("DebugBluetoothQrPanel(qrCoordinator)"))
        self.assertLess(activity.index("DebugBluetoothJourneyCue.current("),
                        activity.index("DebugBluetoothQrPanel(qrCoordinator)"))
        self.assertLess(activity.index("DebugBluetoothQrPanel(qrCoordinator)"),
                        activity.index('Text("START 30-SECOND TEST SEARCH")'))
        self.assertIn("if (showRadioSearch)", activity)
        self.assertIn("DebugBluetoothQrCoordinator.Stage.JOIN_PREPARED ||", activity)
        self.assertIn("showRawScanner", activity)
        self.assertIn("ADVANCED • RAW RADIO SCAN", activity)
        self.assertIn("linkPhase !in setOf(", activity)
        self.assertIn("DebugRfcommAttemptGate.Phase.STOPPED",
                      (DIR / "DebugBluetoothJourneyCue.kt").read_text())
        self.assertIn('Text("HOST ONE-TIME BLUETOOTH TEST LINK")', activity)
        self.assertIn('Text("JOIN STAGED UNVERIFIED SIGNAL")', activity)
        self.assertEqual(1, activity.count("HOST ONE-TIME BLUETOOTH TEST LINK"))
        self.assertEqual(1, activity.count("JOIN STAGED UNVERIFIED SIGNAL"))
        self.assertLess(activity.index("DebugBluetoothQrPanel(qrCoordinator)"),
                        activity.index("HOST ONE-TIME BLUETOOTH TEST LINK"))
        self.assertIn("approvalPending &&", activity)
        self.assertIn("linkController.approve()", activity)
        self.assertIn("linkController.decline()", activity)
        self.assertIn('Text("DISCONNECT BLUETOOTH TEST LINK")', activity)

    def test_terminal_clears_qr_and_untrusted_radio_candidate(self):
        activity = (DIR / "DebugBluetoothDiscoveryActivity.kt").read_text()
        self.assertIn("if (phase == DebugRfcommAttemptGate.Phase.STOPPED) {", activity)
        stop = activity.split("if (phase == DebugRfcommAttemptGate.Phase.STOPPED) {", 1)[1][:400]
        self.assertIn("qrCoordinator.clear()", stop)
        self.assertIn("clearCandidateMemory()", stop)
        self.assertIn("linkController.background()", activity)
        self.assertIn("onClick = { beginHostLink() }", activity)
        self.assertIn("onClick = { beginJoinLink() }", activity)
        self.assertIn("qrCoordinator.readyForDebugLink(hostRole = true)", activity)
        self.assertIn("qrCoordinator.readyForDebugLink(hostRole = false)", activity)

    def test_guidance_does_not_authorize_transports_or_release_messages(self):
        guide = (DIR / "DebugBluetoothJourneyCue.kt").read_text()
        for marker in ('STEP 1', 'STEP 2', 'STEP 3', 'TEST ENDED',
                       'DebugRfcommAttemptGate.Phase.STOPPED',
                       'DebugBluetoothQrCoordinator.Stage.JOIN_PREPARED'):
            self.assertIn(marker, guide)
        for forbidden in ("BluetoothSocket(", "SSLEngine", "Socket(",
                          "SharedPreferences", "sendDebugText(", "approveForTest(",
                          "startActivity(", "BluetoothAdapter", "createRfcommSocket"):
            self.assertNotIn(forbidden, guide)
        controller = (DIR / "DebugRfcommLinkController.kt").read_text()
        self.assertIn("stop(token, DebugRfcommCompletionPolicy.endAfterConsent(", controller)
        self.assertIn("closeQuietly(oldSocket)", controller)
        self.assertNotIn("DebugBluetoothFutureTlsTextLab", controller)
        release = (MAIN / "model/ReleaseCapabilities.kt").read_text()
        for flag in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {flag} = false", release)


if __name__ == "__main__":
    unittest.main()
