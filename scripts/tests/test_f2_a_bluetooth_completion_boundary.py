"""F2-A: Bluetooth approval completion must respect fresh live state.

These are source integration regressions, not Android hardware or cryptographic
validation. They do not enable user messages on Bluetooth.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
BT = ROOT / "app/src/debug/java/com/edenpractical/comms/bluetooth"
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"


class BluetoothCompletionBoundaryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.controller = (BT / "DebugRfcommLinkController.kt").read_text()
        cls.policy = (BT / "DebugRfcommCompletionPolicy.kt").read_text()
        cls.release = (MAIN / "model/ReleaseCapabilities.kt").read_text()

    def test_approved_worker_rechecks_lifecycle_before_terminal_success(self):
        self.assertIn("DebugRfcommTlsProbe.run(created, request)", self.controller)
        self.assertIn("stop(token, DebugRfcommCompletionPolicy.endAfterConsent(", self.controller)
        self.assertIn("isForeground(), hasConnectPermission()", self.controller)
        for part in (
            "!foreground -> DebugRfcommAttemptGate.End.BACKGROUND",
            "!hasConnectPermission -> DebugRfcommAttemptGate.End.PERMISSION_LOST",
            "else -> DebugRfcommAttemptGate.End.CONSENT_CONFIRMED",
        ):
            self.assertIn(part, self.policy)
        self.assertNotIn(
            "stop(token, DebugRfcommAttemptGate.End.CONSENT_CONFIRMED)",
            self.controller
        )

    def test_user_consent_buttons_recheck_live_app_and_permission(self):
        self.assertEqual(
            2, self.controller.count("DebugRfcommCompletionPolicy.mayChooseConsent(")
        )
        self.assertIn("phase == DebugRfcommAttemptGate.Phase.LINK_OPEN", self.policy)
        self.assertIn("foreground && hasConnectPermission", self.policy)

    def test_old_terminal_callback_cannot_rewrite_new_completed_attempt(self):
        self.assertIn("lastStoppedToken = token", self.controller)
        self.assertIn("lastStoppedToken !== token", self.controller)
        self.assertIn("if (active != null || lastStoppedToken !== token ||", self.controller)

    def test_unused_or_rejected_qr_attempts_are_zeroized(self):
        for text in (
            "if (!request.hostRole) { request.close(); return false }",
            "if (request.hostRole) { request.close(); return false }",
            "?: run { request.close(); return false }",
        ):
            self.assertIn(text, self.controller)
        self.assertGreaterEqual(
            self.controller.count("?: run { request.close(); return false }"), 2
        )

    def test_messaging_is_still_disabled_and_transport_still_closes(self):
        for cap in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {cap} = false", self.release)
        self.assertIn("closeQuietly(oldSocket)", self.controller)
        self.assertIn("DebugRfcommAttemptGate.End.CONSENT_CONFIRMED ->", self.controller)
        for unsafe in ("sendDebugText(", "TestPacketCodec", "AudioRecord",
                       "listenUsingInsecureRfcomm", "createInsecureRfcomm"):
            self.assertNotIn(unsafe, self.controller + self.policy)


if __name__ == "__main__":
    unittest.main()
