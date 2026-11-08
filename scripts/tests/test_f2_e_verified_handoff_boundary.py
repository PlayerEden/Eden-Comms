"""F2-E: verified-path diagnostic receipt, not Bluetooth chat authority.

Static source checks are regressions, not proof of real Android TLS behavior or
external security assessment. The socket must close after every outcome.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
BT = ROOT / "app/src/debug/java/com/edenpractical/comms/bluetooth"
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"
TEST = ROOT / "app/src/test/java/com/edenpractical/comms/bluetooth"


class F2ECompletionHandoffBoundary(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.receipt = (BT / "DebugBluetoothProbeCompletion.kt").read_text()
        cls.probe = (BT / "DebugRfcommTlsProbe.kt").read_text()
        cls.controller = (BT / "DebugRfcommLinkController.kt").read_text()
        cls.consent = (BT / "DebugBluetoothConsentExchange.kt").read_text()
        cls.release = (MAIN / "model/ReleaseCapabilities.kt").read_text()

    def test_only_real_verified_probe_emits_receipt_after_final_controls(self):
        sequence = [
            "DebugTlsEngineHandshake.negotiate(",
            "PinnedTlsPeer.requirePinnedTls13(",
            "SessionProofExchange.runHost(",
            "DebugExporterBindingFrame.exchange(",
            "DebugBluetoothCommitmentFrame.exchange(",
            "DebugBluetoothConsentExchange.run(",
            "val atEnd = request.freshBinding()",
            "return DebugBluetoothProbeCompletion.afterVerifiedDiagnostic(",
        ]
        locations = [self.probe.index(marker) for marker in sequence]
        self.assertEqual(sorted(locations), locations)
        self.assertIn("return DebugBluetoothProbeCompletion.afterVerifiedDiagnostic(", self.probe)
        self.assertIn("DebugApprovalGate.State.CONFIRMED", self.consent)
        self.assertIn("request.expiresAtSeconds", self.probe)
        self.assertIn("transport?.clear()", self.probe)
        self.assertIn("expectedPin.fill(0)", self.probe)
        self.assertIn("engine?.closeOutbound()", self.probe)

    def test_only_live_current_socket_token_consumes_receipt_before_closure(self):
        for marker in (
            "val completion = DebugRfcommTlsProbe.run(\n                created, request,",
            "onVerifiedStage = { step -> verified(token, step) }",
            ") { decision ->",
            "active === token",
            "gate.snapshot().phase == DebugRfcommAttemptGate.Phase.LINK_OPEN",
            "completion.acknowledgeOnce(",
            "expectedHostRole = token.role == DebugRfcommAttemptGate.Role.HOST",
            "System.currentTimeMillis() / 1000",
            "foreground = isForeground()",
            "connectPermission = hasConnectPermission()",
            "DebugRfcommAttemptGate.End.FAILED",
            "DebugRfcommCompletionPolicy.endAfterConsent(",
            "completion.close()",
            "closeQuietly(oldSocket)",
            "closeQuietly(oldServer)",
        ):
            self.assertIn(marker, self.controller, marker)
        self.assertLess(
            self.controller.index("completion.acknowledgeOnce("),
            self.controller.index("stop(token, DebugRfcommCompletionPolicy.endAfterConsent(")
        )
        self.assertIn("LINK_LIFETIME_MS = 45_000L", self.controller)
        self.assertNotIn("DebugBluetoothContinuationBudget", self.controller)

    def test_receipt_one_use_short_lived_and_no_secrets_or_socket(self):
        for marker in (
            "private constructor(", "MAX_HANDOFF_AGE_SECONDS = 3L",
            "private var consumed = false",
            "private var closed = false",
            "if (consumed || closed) return false",
            "consumed = true",
            "observedAtSeconds >= issuedAtSeconds",
            "observedAtSeconds <= qrExpiresAtSeconds",
            "observedAtSeconds - issuedAtSeconds <= MAX_HANDOFF_AGE_SECONDS",
            "foreground && connectPermission",
            "override fun close()",
        ):
            self.assertIn(marker, self.receipt, marker)
        for forbidden in (
            "BluetoothSocket", "SSLEngine", "ByteArray", "TestPacketCodec",
            "sendDebugText(", "AudioRecord", "SharedPreferences", "DataStore",
            "OutputStream", "InputStream", "LocalTlsIdentity", "ProductionSessionAuthorization"
        ):
            # Class header comments may name socket/engine, so only scrutinize
            # source excluding comments in these specific cases.
            if forbidden in ("BluetoothSocket", "SSLEngine"):
                source = "\n".join(line for line in self.receipt.splitlines()
                                   if not line.strip().startswith("*") and
                                   not line.strip().startswith("//"))
                self.assertNotIn(forbidden, source)
            else:
                self.assertNotIn(forbidden, self.receipt, forbidden)
        self.assertTrue((TEST / "DebugBluetoothProbeCompletionTest.kt").is_file())

    def test_release_permissions_and_cryptographic_primitives_untouched(self):
        for name in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {name} = false", self.release)
        for api in ("sendDebugText(", "TestPacketCodec", "openFileOutput(",
                    "listenUsingInsecureRfcomm", "createInsecureRfcomm"):
            self.assertNotIn(api, self.receipt + self.probe + self.controller)
        self.assertFalse((MAIN / "bluetooth/DebugBluetoothProbeCompletion.kt").exists())


if __name__ == "__main__":
    unittest.main()
