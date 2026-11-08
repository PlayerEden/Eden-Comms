"""M5-A source boundary checks. JVM test coverage, NOT physical or TLS proof."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"
JVM = ROOT / "app/src/test/java/com/edenpractical/comms"


class M5ARehearsalContracts(unittest.TestCase):
    def test_dual_virtual_peers_have_independent_approval_and_late_events(self):
        src = (JVM / "transport/TwoPeerFailureRehearsalTest.kt").read_text()
        for required in (
            "ScriptedNearbyTransportAdapter", "DebugApprovalGate",
            "bothSidesRequireTheirOwnApprovalsAndRemoteObservation",
            "cancelBackgroundAndTimeoutBeforeReadyRejectDelayedSignals",
            "oldSessionCallbacksCannotTerminateFreshTwoPeerAttempt",
            "independentNetworkChangeAndVerificationRejectAreTerminal",
            "transport.lifecycle.isPreviewActive(attempt)",
            "assertFalse(ReleaseCapabilities.CAN_SEND_MESSAGES)",
        ):
            self.assertIn(required, src)
        for forbidden in ("import android.", "androidx.", "WifiP2pManager",
                          "BluetoothAdapter", "SSLSocket", "Thread.sleep("):
            self.assertNotIn(forbidden, src)

    def test_real_socket_ownership_but_loopback_only(self):
        src = (JVM / "transport/LocalTwoPeerSocketCancellationTest.kt").read_text()
        for required in (
            "InetAddress.getLoopbackAddress()", "SessionSocketRegistry",
            "listener.accept()", "joiner.close()", "host.close()"
        ):
            self.assertIn(required, src)
        for forbidden in ("SSLSocket", "KeyStore", "android.", "sendDebugText("):
            self.assertNotIn(forbidden, src)

    def test_recovery_receives_safe_classes_not_raw_secrets(self):
        src = (JVM / "pairing/M5FailureToRecoveryMatrixTest.kt").read_text()
        self.assertIn("LanProbeDiagnostics.classify(", src)
        self.assertIn("NearbyRecoveryGuide.forResult(", src)
        self.assertIn('assertFalse(userVisible.contains("PRIVATE-TEST-MARKER"))', src)

    def test_production_flags_still_off_and_session_cleanup_kept(self):
        flags = (MAIN / "model/ReleaseCapabilities.kt").read_text()
        for flag in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn("const val " + flag + " = false", flags)
        runtime = (MAIN / "transport/LanDemoSession.kt").read_text()
        for required in (
            "ApplicationInfo.FLAG_DEBUGGABLE",
            "SessionSocketRegistry()", "DebugApprovalGate()",
            "sockets.close()", "approvalGate.close()"
        ):
            self.assertIn(required, runtime)


if __name__ == "__main__":
    unittest.main()
