"""M5-C source guard: real debug TLS socket boundary observes shutdown.

Not a hardware test or a proof that an already-in-flight network write can
be unwritten. The hard-close timer/foreground-only limits remain independent.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "app/src/main/java/com/edenpractical/comms"
TESTS = ROOT / "app/src/test/java/com/edenpractical/comms"


class M5CTeardown(unittest.TestCase):
    def test_real_tls_packet_boundary_checks_shutdown(self):
        session = (SRC / "transport/LanDemoSession.kt").read_text()
        send = session.split("private fun sendPacket(packet:", 1)[1].split(
            "private fun startHeartbeat()", 1
        )[0]
        self.assertIn("synchronized(outputLock)", send)
        self.assertIn("DebugSessionTeardownPolicy.mayWrite(ending.get(), packet.type)", send)
        self.assertLess(send.index("DebugSessionTeardownPolicy.mayWrite"),
                        send.index("TestPacketCodec.write("))
        self.assertIn("sockets.currentSocket() as? SSLSocket", send)
        self.assertIn("SocketException", send)

    def test_end_can_still_send_close_or_decline_and_hard_close(self):
        session = (SRC / "transport/LanDemoSession.kt").read_text()
        policy = (SRC / "transport/DebugSessionTeardownPolicy.kt").read_text()
        self.assertIn("type == TestPacketCodec.Type.CLOSE", policy)
        self.assertIn("type == TestPacketCodec.Type.DECLINE", policy)
        self.assertIn("stopWithControl(TestPacketCodec.close())", session)
        self.assertIn("stopWithControl(TestPacketCodec.decline(commitment))", session)
        self.assertIn("LanGracefulStop.begin(", session)
        self.assertIn("hardClose = { close() }", session)
        self.assertIn("sockets.close()", session)

    def test_stale_send_status_and_cancelled_error_are_suppressed(self):
        session = (SRC / "transport/LanDemoSession.kt").read_text()
        self.assertIn("DebugSessionTeardownPolicy.mayReportActivity(", session)
        self.assertIn("DebugSessionTeardownPolicy.mayReportUnexpectedEnd(", session)
        self.assertEqual(2, session.count(
            "DebugSessionTeardownPolicy.mayReportUnexpectedEnd("))
        policy = (SRC / "transport/DebugSessionTeardownPolicy.kt").read_text()
        self.assertIn("!stopped && !terminal && !ending", policy)
        self.assertIn("!stopped && !ending", policy)
        test = (TESTS / "transport/DebugSessionTeardownPolicyTest.kt").read_text()
        for name in ("allOrdinaryDebugPacketsAreRejectedAfterEndBegins",
                     "endingSuppressesStaleProgressAndUnexpectedDisconnect",
                     "queuedWriterRechecksAfterConcurrentUserEndInsteadOfTrustingOldCheck"):
            self.assertIn(name, test)

    def test_no_release_or_crypto_bypass(self):
        flags = (SRC / "model/ReleaseCapabilities.kt").read_text()
        for name in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn("const val " + name + " = false", flags)
        session = (SRC / "transport/LanDemoSession.kt").read_text()
        for requirement in (
            "ApplicationInfo.FLAG_DEBUGGABLE",
            "PinnedTlsPeer.requirePinnedTls13",
            "SessionProofExchange.runHost",
            "SessionProofExchange.runJoin",
            "requireCurrentConsentCommitment(packet)",
            "approvalGate.maySendPreset()",
            "sockets.close()",
        ):
            self.assertIn(requirement, session)
        policy = (SRC / "transport/DebugSessionTeardownPolicy.kt").read_text()
        for forbidden in ("import android.", "Socket(", "SSLContext(", "SharedPreferences",
                          "approveForTest(", "CAN_CONNECT = true"):
            self.assertNotIn(forbidden, policy)


if __name__ == "__main__":
    unittest.main()
