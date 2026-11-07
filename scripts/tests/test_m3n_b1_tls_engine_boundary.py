"""M3n-B1: strict debug TLS 1.3 SSLEngine handshake over RFCOMM.

The live probe only exchanges TLS-protected, fixed-width protocol controls.
It does not authorize text or voice, saved peers or bilateral human approval.
Physical Android interoperability and independent security review are open.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main"
BT = ROOT / "app/src/debug/java/com/edenpractical/comms/bluetooth"


class BluetoothTlsEngineBoundaryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.context = (MAIN / "java/com/edenpractical/comms/security/Tls13PeerContext.kt").read_text()
        cls.probe = (BT / "DebugRfcommTlsProbe.kt").read_text()
        cls.handshake = (BT / "DebugTlsEngineHandshake.kt").read_text()
        cls.controller = (BT / "DebugRfcommLinkController.kt").read_text()
        cls.activity = (BT / "DebugBluetoothDiscoveryActivity.kt").read_text()
        cls.qr = (BT / "DebugBluetoothQrCoordinator.kt").read_text()
        cls.input = (BT / "DebugBluetoothTlsProbeInput.kt").read_text()
        cls.release = (MAIN / "java/com/edenpractical/comms/model/ReleaseCapabilities.kt").read_text()
        cls.manifest = (MAIN / "AndroidManifest.xml").read_text()
        cls.debug_manifest = (ROOT / "app/src/debug/AndroidManifest.xml").read_text()

    def test_tls_context_is_pinned_reused_and_has_no_downgrade(self):
        for marker in (
            "VerifiedPeerTrustManager(expectedPeerSha256)",
            "fun unconnectedEngine(client: Boolean): SSLEngine",
            'context.createSSLEngine().apply',
            "useClientMode = client",
            'enabledProtocols = arrayOf("TLSv1.3")',
            "if (!client) needClientAuth = true",
        ):
            self.assertIn(marker, self.context)
        for insecure in ("SSLContext.getInstance(\"TLS\")", "TrustAll",
                         "InsecureTrustManager", 'enabledProtocols = arrayOf("TLSv1.2")'):
            self.assertNotIn(insecure, self.context)

    def test_real_rfccomm_worker_runs_pinned_tls_before_closing_and_never_messages(self):
        for marker in (
            "val wireIn = socket.inputStream",
            "val wireOut = socket.outputStream",
            "DebugTlsEngineHandshake.negotiate(",
            "PinnedTlsPeer.requirePinnedTls13(engine.session, expectedPin)",
            "SSLEngines.isSupportedEngine(engine)",
            "SSLEngines.exportKeyingMaterial(",
            "EXPORTER-Channel-Binding",
            "exported.fill(0)",
            "expectedPin.fill(0)",
        ):
            self.assertIn(marker, self.probe, marker)
        for marker in (
            "DebugRfcommTlsProbe.run(created, request) { decision ->",
            "stop(token, DebugRfcommAttemptGate.End.CONSENT_CONFIRMED)",
            "stop(token, DebugRfcommAttemptGate.End.FAILED)",
            "request.close()",
            "closeQuietly(oldSocket)",
            "closeQuietly(oldServer)",
            "if (!gate.stop(token, why) &&",
            "No app messages.",
        ):
            self.assertIn(marker, self.controller, marker)
        for insecure in ("sendDebugText(", "TestPacketCodec", "DataOutputStream",
                         "listenUsingInsecureRfcomm", "createInsecureRfcomm",
                         "writeText(", "readText(",
                         "onAuthenticatedRemoteApproval(", "ReleaseCapabilities.CAN_SEND_MESSAGES = true"):
            self.assertNotIn(insecure, self.controller + self.probe + self.handshake)

    def test_handshake_has_hard_wire_and_step_bounds_and_never_app_plaintext(self):
        for marker in (
            "MAX_RECORD_BODY = 18_432",
            "MAX_ENGINE_PACKET = 32_768",
            "MAX_STEPS = 256",
            "input.read(target, offset, target.size - offset)",
            "engine.beginHandshake()",
            "engine.wrap(EMPTY.duplicate(), outputRecord)",
            "engine.unwrap(ByteBuffer.wrap(record), app)",
            "NEED_TASK",
            "result.bytesProduced() != 0",
            "result.bytesConsumed() != recordLength",
            "output.write(packet)",
            "throw SSLException",
        ):
            self.assertIn(marker, self.handshake, marker)
        for unsafe in ("output.write(raw", "output.write(message", "applicationData",
                       "sendMessage", "getApplicationData", ".toString(Charsets.UTF_8)"):
            self.assertNotIn(unsafe, self.handshake)

    def test_bluetooth_qr_identity_copied_to_per_attempt_input_and_no_authorization(self):
        self.assertIn("fun newTlsProbeInput(hostRole: Boolean)", self.qr)
        self.assertIn("readyForDebugLink(hostRole)", self.qr)
        self.assertIn("current.peerFingerprint(now)", self.qr)
        self.assertIn("current.binding(now)", self.qr)
        self.assertIn("private val peerPin = expectedPeerPin.copyOf()", self.input)
        self.assertIn("peerPin.fill(0)", self.input)
        self.assertIn("DebugBluetoothTlsProbeInput", self.qr)
        self.assertIn("qrCoordinator.newTlsProbeInput(hostRole = true)", self.activity)
        self.assertIn("linkController.host(hostAdapter, request)", self.activity)
        self.assertIn("linkController.join(target, joinAdapter, request)", self.activity)
        self.assertIn("qrCoordinator.readyForDebugLink(hostRole = true)", self.activity)
        self.assertIn("qrCoordinator.readyForDebugLink(hostRole = false)", self.activity)
        self.assertIn("connectPermissionPromptOpen = true", self.activity)
        for flag in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn("const val " + flag + " = false", self.release)
        for permission in ("BLUETOOTH_SCAN", "BLUETOOTH_CONNECT",
                           "BLUETOOTH_ADVERTISE", "RECORD_AUDIO", "NEARBY_WIFI_DEVICES"):
            self.assertNotIn("android.permission." + permission, self.manifest)
        for permission in ("BLUETOOTH_SCAN", "BLUETOOTH_CONNECT", "BLUETOOTH_ADVERTISE"):
            self.assertIn("android.permission." + permission, self.debug_manifest)
        for file in ("DebugRfcommTlsProbe.kt", "DebugTlsEngineHandshake.kt",
                     "DebugBluetoothTlsProbeInput.kt"):
            self.assertFalse((MAIN / "java/com/edenpractical/comms/bluetooth" / file).exists())


if __name__ == "__main__":
    unittest.main()
