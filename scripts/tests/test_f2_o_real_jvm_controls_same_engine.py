"""F2-O: same pinned JVM TLS engines run actual debug QR/proof/consent code.

This test asserts code-path continuity and negative coverage, NOT actual
Android Bluetooth/Conscrypt, TLS channel exporter, or human approval.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
TEST = ROOT / "app/src/test/java/com/edenpractical/comms/bluetooth"
BT = ROOT / "app/src/debug/java/com/edenpractical/comms/bluetooth"
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"


class F2ORealControlsJvm(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = (TEST / "DebugPairedTlsEngineInteropTest.kt").read_text()
        cls.helper = cls.source.split("private fun fullJvmControlSequence(", 1)[1].split(
            "fun actualQrExporterCommitmentAndDualConfirmThenTextAckOnSameJdkTlsEngine()", 1
        )[0]

    def test_real_protocol_order_not_just_synthetic_fixed_size(self):
        markers = (
            "PinnedTlsPeer.requirePinnedTls13(",
            "SessionProofExchange.runHost(",
            "SessionProofExchange.runJoin(",
            "DebugExporterBindingFrame.exchange(",
            "DebugApprovalCommitment.derive(",
            "DebugBluetoothCommitmentFrame.exchange(",
            "DebugBluetoothConsentExchange.run(",
            "onTestApprovalPrompt()",
            "localChoice.approve()",
            "return commitment.copyOf()",
        )
        for marker in markers:
            with self.subTest(marker=marker):
                self.assertIn(marker, self.helper)
        self.assertLess(self.helper.index("SessionProofExchange.runHost("),
                        self.helper.index("DebugExporterBindingFrame.exchange("))
        self.assertLess(self.helper.index("DebugExporterBindingFrame.exchange("),
                        self.helper.index("DebugBluetoothCommitmentFrame.exchange("))
        self.assertLess(self.helper.index("DebugBluetoothCommitmentFrame.exchange("),
                        self.helper.index("DebugBluetoothConsentExchange.run("))
        self.assertIn("control.clear()", self.helper)
        self.assertIn("commitment.fill(0)", self.helper)
        self.assertIn("challenges.clear()", self.helper)
        self.assertIn("binding.hostNonce.fill(0)", self.helper)
        self.assertIn("binding.joinNonce.fill(0)", self.helper)

    def test_same_engine_after_real_control_uses_encrypted_text_ack(self):
        src = self.source.split(
            "fun actualQrExporterCommitmentAndDualConfirmThenTextAckOnSameJdkTlsEngine()", 1
        )[1].split(
            "fun mismatchedTestExporterAbortsActualControlBeforeAnyApproval()", 1
        )[0]
        for marker in (
            "val (host, join) = connect()",
            "val pipe = PairPipes()",
            "DebugTlsEngineHandshake.negotiate(host, pipe.hostInput, pipe.hostOutput)",
            "DebugTlsEngineHandshake.negotiate(join, pipe.joinInput, pipe.joinOutput)",
            "fullJvmControlSequence(",
            "assertEquals(2, approvals.get())",
            "assertArrayEquals(hostTranscript, joinTranscript)",
            "DebugBluetoothFutureTlsTextLab(\n                    host, pipe.hostInput, pipe.hostOutput",
            "DebugBluetoothFutureTlsTextLab(\n                    join, pipe.joinInput, pipe.joinOutput",
            "DebugBluetoothFutureChannelBundle(",
            "hostOwner.begin(101, 500, testClaims, hostBundle, hostContext)",
            "joinOwner.begin(102, 500, testClaims, joinBundle, joinContext)",
            "joinText.transmit(accepted.ack)",
            "hostText.transmit(received.ack)",
            "hostOwner.stop(101)",
            "joinOwner.observe(102, 520, true, true, joinContext)",
            "hostBundle.isClosed()",
            "joinBundle.isClosed()",
            "testExporterHost.fill(0)",
            "testExporterJoin.fill(0)",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, src)
        self.assertEqual(1, src.count("val (host, join) = connect()"))
        self.assertEqual(1, src.count("val pipe = PairPipes()"))
        self.assertNotIn("BluetoothSocket(", src)
        self.assertNotIn("DebugRfcommLinkController(", src)
        self.assertNotIn("SSLEngines.exportKeyingMaterial", src)

    def test_exporter_mismatch_rejects_before_any_approval(self):
        src = self.source.split(
            "fun mismatchedTestExporterAbortsActualControlBeforeAnyApproval()", 1
        )[1].split(
            "fun wrongOutOfBandPeerPinCannotCompleteMutualTlsHandshake()", 1
        )[0]
        for marker in (
            "wrongJoinExporter",
            "assertThrows(ExecutionException::class.java) { await(b) }",
            "pipe.close()",
            "assertEquals(0, approvals.get())",
        ):
            self.assertIn(marker, src)

    def test_real_bluetooth_remains_unwired_and_closed_after_consent(self):
        probe = (BT / "DebugRfcommTlsProbe.kt").read_text()
        controller = (BT / "DebugRfcommLinkController.kt").read_text()
        for src in (probe, controller):
            self.assertNotIn("DebugBluetoothFutureChannelBundle", src)
            self.assertNotIn("DebugBluetoothFutureTlsTextLab", src)
            self.assertNotIn("DebugBluetoothFutureOwnedSession", src)
        self.assertIn("DebugBluetoothConsentExchange.run(", probe)
        self.assertIn("transport?.clear()", probe)
        self.assertIn("engine?.closeOutbound()", probe)
        self.assertIn("stop(token, DebugRfcommCompletionPolicy.endAfterConsent(", controller)
        self.assertIn("closeQuietly(oldSocket)", controller)
        flags = (MAIN / "model/ReleaseCapabilities.kt").read_text()
        for name in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {name} = false", flags)
        self.assertNotIn("android.permission.BLUETOOTH_CONNECT",
                         (ROOT / "app/src/main/AndroidManifest.xml").read_text())


if __name__ == "__main__":
    unittest.main()
