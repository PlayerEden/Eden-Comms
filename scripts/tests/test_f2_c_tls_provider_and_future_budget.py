"""F2-C: source regressions for live provider gate and unwired future budget.

This is NOT security certification, phone interoperability, Bluetooth chat,
a cryptographic identity validator, or an extended live RFCOMM connection.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
BT = ROOT / "app/src/debug/java/com/edenpractical/comms/bluetooth"
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"


class F2CProviderBoundaryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.provider = (BT / "DebugTlsProviderReadiness.kt").read_text()
        cls.control = (BT / "DebugTlsControlTransport.kt").read_text()
        cls.probe = (BT / "DebugRfcommTlsProbe.kt").read_text()
        cls.controller = (BT / "DebugRfcommLinkController.kt").read_text()
        cls.future = (BT / "DebugBluetoothContinuationBudget.kt").read_text()
        cls.release = (MAIN / "model/ReleaseCapabilities.kt").read_text()

    def test_provider_checks_are_attached_to_real_authenticated_probe_and_controls(self):
        self.assertLess(
            self.probe.index("PinnedTlsPeer.requirePinnedTls13("),
            self.probe.index("DebugTlsProviderReadiness.requireControlReady(engine)")
        )
        self.assertLess(
            self.probe.index("DebugTlsProviderReadiness.requireControlReady(engine)"),
            self.probe.index("SessionProofExchange.runHost(")
        )
        self.assertIn("DebugTlsProviderReadiness.requireControlReady(engine)", self.control)
        self.assertIn("DebugTlsProviderReadiness.requireExactControlResult(", self.control)
        self.assertIn("engine.unwrap(ByteBuffer.wrap(encoded), clear)", self.control)
        self.assertIn("engine.wrap(ByteBuffer.wrap(b, off, len), encrypted)", self.control)
        self.assertIn("DebugTlsRecordBoundary.requireSingleCiphertext(encoded)", self.control)
        self.assertIn("DebugTlsRecordBoundary.requireSingleCiphertext(record)", self.control)
        self.assertIn("DebugTlsEngineHandshake.readRecord(wireInput)", self.control)

    def test_provider_rejects_closed_half_closed_post_handshake_or_oversized(self):
        for required in (
            'protocol != "TLSv1.3"',
            "handshake != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING",
            "inboundDone || outboundDone",
            "packetBytes !in 1..DebugTlsEngineHandshake.MAX_ENGINE_PACKET",
            "applicationBytes !in 1..DebugTlsEngineHandshake.MAX_ENGINE_PACKET",
            "result.status != SSLEngineResult.Status.OK",
            "result.handshakeStatus != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING",
            "result.bytesConsumed() != consumed",
            "result.bytesProduced() != produced",
            "engine.isInboundDone",
            "engine.isOutboundDone",
        ):
            self.assertIn(required, self.provider, required)

    def test_future_budget_is_one_shot_not_connected_to_live_socket(self):
        for required in (
            "MAX_FUTURE_LIFETIME_MS = 120_000L",
            "maxLifetimeMs: Long = 90_000L",
            "Phase.NOT_STARTED",
            "Phase.OBSERVING",
            "Phase.TERMINATED",
            "!observations.allObserved()",
            "elapsedMs > Long.MAX_VALUE - maxLifetimeMs",
            "elapsedMs < startedMs || elapsedMs < lastObservedMs",
            "elapsedMs >= deadlineMs",
            "lastObservedMs = elapsedMs",
            "!foreground || !connectPermission",
            "attemptId != generation",
        ):
            self.assertIn(required, self.future, required)
        self.assertNotIn("DebugBluetoothContinuationBudget", self.controller)
        self.assertNotIn("DebugBluetoothContinuationBudget", self.probe)
        self.assertIn("stop(token, DebugRfcommCompletionPolicy.endAfterConsent(", self.controller)
        self.assertIn("closeQuietly(oldSocket)", self.controller)
        self.assertIn("LINK_LIFETIME_MS = 45_000L", self.controller)
        for unsafe in ("BluetoothSocket", "TestPacketCodec", "sendDebugText(",
                       "AudioRecord", "SharedPreferences", "ReleaseCapabilities"):
            self.assertNotIn(unsafe, self.future)

    def test_release_flags_still_disabled_and_no_new_bluetooth_messages(self):
        for flag in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {flag} = false", self.release)
        for disallowed in ("sendDebugText(", "listenUsingInsecureRfcomm",
                           "createInsecureRfcomm", "TestPacketCodec"):
            self.assertNotIn(disallowed, self.control + self.probe +
                             self.controller + self.provider)


if __name__ == "__main__":
    unittest.main()
