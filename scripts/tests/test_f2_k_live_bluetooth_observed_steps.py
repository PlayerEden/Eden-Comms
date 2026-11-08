"""F2-K: source checks for actual Bluetooth worker verification steps.

These are *observations*, never authorization to retain an RFCOMM socket.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
BT = ROOT / "app/src/debug/java/com/edenpractical/comms/bluetooth"
RELEASE = ROOT / "app/src/main/java/com/edenpractical/comms/model/ReleaseCapabilities.kt"


class RealBluetoothVerificationProgress(unittest.TestCase):
    def test_probe_milestones_come_only_after_actual_cryptographic_checks(self):
        probe = (BT / "DebugRfcommTlsProbe.kt").read_text()
        pairs = [
            ("DebugTlsProviderReadiness.requireControlReady(engine)\n"
             "            onVerifiedStage(DebugBluetoothVerifiedStage.TLS_PIN)",
             "DebugBluetoothVerifiedStage.TLS_PIN"),
            ("DebugTlsControlTransport.Phase.EXPORTER\n"
             "            )\n            onVerifiedStage(DebugBluetoothVerifiedStage.QR_CHALLENGES)",
             "DebugBluetoothVerifiedStage.QR_CHALLENGES"),
            ("DebugTlsControlTransport.Phase.COMMITMENT\n"
             "                )\n                onVerifiedStage(DebugBluetoothVerifiedStage.EXPORTER)",
             "DebugBluetoothVerifiedStage.EXPORTER"),
            ("request.hostRole, commitment\n                    )\n"
             "                    onVerifiedStage(DebugBluetoothVerifiedStage.COMMITMENT)",
             "DebugBluetoothVerifiedStage.COMMITMENT"),
            ("clearBinding(atEnd)\n"
             "            onVerifiedStage(DebugBluetoothVerifiedStage.HUMAN_CONFIRMATIONS)",
             "DebugBluetoothVerifiedStage.HUMAN_CONFIRMATIONS")
        ]
        for context, _ in pairs:
            self.assertIn(context, probe)
        positions = [probe.index("onVerifiedStage(" + stage + ")") for _, stage in pairs]
        self.assertEqual(positions, sorted(positions))
        self.assertIn("DebugBluetoothConsentExchange.run(", probe)
        self.assertIn("return DebugBluetoothProbeCompletion.afterVerifiedDiagnostic(", probe)
        self.assertIn("engine?.closeOutbound()", probe)
        self.assertIn("transport?.clear()", probe)

    def test_last_progress_belongs_to_current_active_attempt_not_old_socket(self):
        controller = (BT / "DebugRfcommLinkController.kt").read_text()
        activity = (BT / "DebugBluetoothDiscoveryActivity.kt").read_text()
        self.assertIn("onVerifiedStage = { step -> verified(token, step) }", controller)
        self.assertIn("if (active !== token || !gate.isLive(token) ||", controller)
        self.assertIn("!isForeground() || !hasConnectPermission() ||", controller)
        self.assertIn("(lastVerifiedStage?.step ?: 0) >= step.step", controller)
        self.assertIn("lastVerifiedStage = null", controller)
        self.assertIn("onVerificationStage(null)", controller)
        self.assertIn("lastStoppedToken !== token", controller)
        self.assertIn("onVerificationStage(lastProof)", controller)
        self.assertIn("onVerificationStage = { step -> verifiedProof = step }", activity)
        self.assertIn("step.display()", activity)
        self.assertNotIn("SharedPreferences", (BT / "DebugBluetoothVerifiedStage.kt").read_text())

    def test_diagnostic_still_closes_and_releases_remain_off(self):
        controller = (BT / "DebugRfcommLinkController.kt").read_text()
        stage = (BT / "DebugBluetoothVerifiedStage.kt").read_text()
        self.assertIn("stop(token, DebugRfcommCompletionPolicy.endAfterConsent(", controller)
        self.assertIn("closeQuietly(oldSocket)", controller)
        self.assertIn("oldConsent?.close()", controller)
        self.assertNotIn("DebugBluetoothFutureTlsTextLab", controller)
        for forbidden in ("Socket(", "SSLEngine", "sendText(", "transmit(",
                          "HostInvite(", "approvalGate", "messageId",
                          "peerFingerprint", "BluetoothDevice"):
            self.assertNotIn(forbidden, stage)
        for capability in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {capability} = false", RELEASE.read_text())


if __name__ == "__main__":
    unittest.main()
