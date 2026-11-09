"""M6-D: observed QR-stage guidance stays truthful and accessible.

These checks prove source UI wording/boundaries only. They do not simulate
physical radios, Android TalkBack focus, camera scanning or cryptography.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
DEBUG = ROOT / "app/src/debug/java/com/edenpractical/comms"


class M6DQrWalkthrough(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.bt = (DEBUG / "bluetooth/DebugBluetoothQrPanel.kt").read_text()
        cls.p2p = (DEBUG / "wifidirect/DebugWifiDirectInlineQrPanel.kt").read_text()
        cls.release = (ROOT / "app/src/main/java/com/edenpractical/comms/model/ReleaseCapabilities.kt").read_text()

    def test_bluetooth_two_code_steps_have_actionable_next_instruction(self):
        for expected in (
            "DebugBluetoothQrCoordinator.Stage.CHOOSE",
            "DebugBluetoothQrCoordinator.Stage.HOST_SHOW",
            "DebugBluetoothQrCoordinator.Stage.JOIN_SCAN_HOST",
            "DebugBluetoothQrCoordinator.Stage.HOST_SCAN_REPLY",
            "DebugBluetoothQrCoordinator.Stage.JOIN_SHOW_REPLY",
            "DebugBluetoothQrCoordinator.Stage.HOST_PREPARED",
            "DebugBluetoothQrCoordinator.Stage.JOIN_PREPARED",
            "QR STEP 1 OF 2", "QR STEP 2 OF 2",
            "QR CODES EXCHANGED", "The link is still unverified.",
            "The other phone", "after they have scanned it",
            "CREATE LOCAL DEVICE IDENTITY",
            "RETRY QR CAMERA", "CANCEL AND ERASE QR PREPARATION",
        ):
            self.assertIn(expected, self.bt, expected)

    def test_wifi_direct_three_code_steps_never_call_the_whole_flow_step_one(self):
        for expected in (
            "DebugWifiDirectQrCoordinator.Stage.CHOOSE",
            "DebugWifiDirectQrCoordinator.Stage.HOST_SHOW",
            "DebugWifiDirectQrCoordinator.Stage.JOIN_SCAN_HOST",
            "DebugWifiDirectQrCoordinator.Stage.HOST_SCAN_REPLY",
            "DebugWifiDirectQrCoordinator.Stage.JOIN_SHOW_REPLY",
            "DebugWifiDirectQrCoordinator.Stage.HOST_SHOW_ACK",
            "DebugWifiDirectQrCoordinator.Stage.JOIN_SCAN_ACK",
            "DebugWifiDirectQrCoordinator.Stage.JOIN_PREPARED",
            "QR STEP 1 OF 3", "QR STEP 2 OF 3",
            "QR STEP 3 OF 3", "QR CODES EXCHANGED",
            "both approvals", "Neither QR scanning",
            "SHOW HOST QR", "SCAN HOST QR", "RETRY SCANNER",
            "CANCEL AND CLEAR QR CODES",
        ):
            self.assertIn(expected, self.p2p, expected)
        self.assertNotIn('Text("Step 1 — Verify nearby person"', self.p2p)

    def test_accessible_status_and_no_permission_or_trust_bypass(self):
        for panel in (self.bt, self.p2p):
            for required in (
                "Modifier.semantics { heading() }",
                "liveRegion = LiveRegionMode.Polite",
                "LocalPairingQrScanner(",
                "LocalPairingQrDisplay(",
                ".heightIn(min = 52.dp)",
            ):
                self.assertIn(required, panel, required)
            for forbidden in (
                "SSLEngine", "approveForTest(", "startDiscovery(",
                "openFileOutput(", "SharedPreferences", "ProductionSessionAuthorization",
            ):
                self.assertNotIn(forbidden, panel)
        for flag in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {flag} = false", self.release)


if __name__ == "__main__":
    unittest.main()
