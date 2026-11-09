"""F2-L: real Bluetooth diagnostic completion must be monotonic and non-authorizing.

This verifies the Android worker stamps its actual short-lived completion
receipt with elapsedRealtime, and the real controller consumes it with the
same monotonic source INSIDE the current active socket-token check.
No chat transport, consent bypass, retained RFCOMM socket, or production flag.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
BT = ROOT / "app/src/debug/java/com/edenpractical/comms/bluetooth"
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"


class F2LMonotonicCompletion(unittest.TestCase):
    def test_real_probe_uses_monotonic_clock_after_final_consent(self):
        src = (BT / "DebugRfcommTlsProbe.kt").read_text()
        for marker in (
            "DebugBluetoothConsentExchange.run(",
            "val atEnd = request.freshBinding()",
            "return DebugBluetoothProbeCompletion.afterVerifiedDiagnostic(",
            "issuedAtSeconds = nowSeconds",
            "qrExpiresAtSeconds = request.expiresAtSeconds",
            "issuedAtElapsedMs = SystemClock.elapsedRealtime()",
            "transport?.clear()",
            "engine?.closeOutbound()",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, src)
        self.assertLess(src.index("DebugBluetoothConsentExchange.run("),
                        src.index("issuedAtElapsedMs = SystemClock.elapsedRealtime()"))

    def test_real_controller_still_fences_live_token_and_immediately_closes(self):
        src = (BT / "DebugRfcommLinkController.kt").read_text()
        for marker in (
            "active === token",
            "gate.snapshot().phase == DebugRfcommAttemptGate.Phase.LINK_OPEN",
            "completion.acknowledgeOnce(",
            "observedAtSeconds = System.currentTimeMillis() / 1000",
            "observedAtElapsedMs = SystemClock.elapsedRealtime()",
            "foreground = isForeground()",
            "connectPermission = hasConnectPermission()",
            "stop(token, DebugRfcommCompletionPolicy.endAfterConsent(",
            "completion.close()",
            "closeQuietly(oldSocket)",
            "LINK_LIFETIME_MS = 45_000L",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, src)
        self.assertLess(src.index("completion.acknowledgeOnce("),
                        src.index("stop(token, DebugRfcommCompletionPolicy.endAfterConsent("))
        for forbidden in ("sendDebugText(", "TestPacketCodec", "retainVerifiedSocket(",
                          "DebugBluetoothFutureOwnedSession"):
            self.assertNotIn(forbidden, src)

    def test_receipt_rejects_stale_wall_clock_resets_and_has_no_send_api(self):
        src = (BT / "DebugBluetoothProbeCompletion.kt").read_text()
        for marker in (
            "MAX_HANDOFF_AGE_SECONDS = 3L",
            "MAX_HANDOFF_AGE_MS = MAX_HANDOFF_AGE_SECONDS * 1_000L",
            "issuedAtElapsedMs: Long",
            "observedAtElapsedMs: Long",
            "observedAtSeconds <= qrExpiresAtSeconds",
            "observedAtSeconds - issuedAtSeconds <= MAX_HANDOFF_AGE_SECONDS",
            "observedAtElapsedMs >= issuedAtElapsedMs",
            "observedAtElapsedMs - issuedAtElapsedMs <= MAX_HANDOFF_AGE_MS",
            "if (consumed || closed) return false",
            "consumed = true",
            "foreground && connectPermission",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, src)
        for forbidden in ("ByteArray", "OutputStream", "InputStream", "sendText(",
                          "sendDebugText(", "BluetoothSocket(", "SSLEngine(", "SharedPreferences"):
            self.assertNotIn(forbidden, src)

    def test_development_only_no_new_authority(self):
        flags = (MAIN / "model/ReleaseCapabilities.kt").read_text()
        for cap in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {cap} = false", flags)
        self.assertNotIn("android.permission.BLUETOOTH_CONNECT",
                         (ROOT / "app/src/main/AndroidManifest.xml").read_text())
        self.assertNotIn("DebugBluetoothProbeCompletion",
                         (MAIN / "transport/LanDemoSession.kt").read_text())


if __name__ == "__main__":
    unittest.main()
