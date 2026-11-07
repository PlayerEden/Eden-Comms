"""M3n-B2 encrypted fixed-control agreement, with no Bluetooth user messaging.

Automated contract checks DO NOT prove live Android TLS/RFCOMM interoperability
or constitute independent cryptographic protocol review.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main"
DEBUG = ROOT / "app/src/debug/java/com/edenpractical/comms/bluetooth"


class EncryptedBluetoothTranscriptContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.probe = (DEBUG / "DebugRfcommTlsProbe.kt").read_text()
        cls.control = (DEBUG / "DebugTlsControlTransport.kt").read_text()
        cls.commit = (DEBUG / "DebugBluetoothCommitmentFrame.kt").read_text()
        cls.input = (DEBUG / "DebugBluetoothTlsProbeInput.kt").read_text()
        cls.qr = (DEBUG / "DebugBluetoothQrCoordinator.kt").read_text()
        cls.link = (DEBUG / "DebugRfcommLinkController.kt").read_text()
        cls.gate = (DEBUG / "DebugRfcommAttemptGate.kt").read_text()
        cls.proof = (MAIN / "java/com/edenpractical/comms/security/SessionProofExchange.kt").read_text()
        cls.export = (MAIN / "java/com/edenpractical/comms/security/DebugExporterBindingFrame.kt").read_text()
        cls.consent = (MAIN / "java/com/edenpractical/comms/security/DebugApprovalCommitment.kt").read_text()
        cls.release = (MAIN / "java/com/edenpractical/comms/model/ReleaseCapabilities.kt").read_text()
        cls.main_manifest = (MAIN / "AndroidManifest.xml").read_text()
        cls.debug_manifest = (ROOT / "app/src/debug/AndroidManifest.xml").read_text()

    def test_exact_mutual_tls_precedes_encrypted_qr_proof_exporter_and_commit(self):
        ordered = [
            "DebugTlsEngineHandshake.negotiate(",
            "PinnedTlsPeer.requirePinnedTls13(engine.session, expectedPin)",
            "binding = request.freshBinding()",
            "transport = DebugTlsControlTransport(engine, wireIn, wireOut)",
            "SessionProofExchange.runHost(",
            "DebugExporterBindingFrame.exchange(",
            "DebugApprovalCommitment.derive(",
            "DebugBluetoothCommitmentFrame.exchange(",
            "val atEnd = request.freshBinding()"
        ]
        indices = [self.probe.index(piece) for piece in ordered]
        self.assertEqual(sorted(indices), indices)
        self.assertIn("SessionProofExchange.runJoin(", self.probe)
        self.assertIn("transport.advance(", self.probe)
        self.assertIn("DebugTlsControlTransport.Phase.EXPORTER", self.probe)
        self.assertIn("DebugTlsControlTransport.Phase.COMMITMENT", self.probe)
        for piece in ("exported.fill(0)", "commitment.fill(0)", "challenges?.clear()",
                      "binding?.let(::clearBinding)", "transport?.clear()"):
            self.assertIn(piece, self.probe)

    def test_only_fixed_size_tls_engine_control_frames_can_cross_rfcomm(self):
        for piece in (
            "PROOF_BYTES = 4 + 1 + 1 + 32 + 32 + 16 + 16 + 16 + 16",
            "QR_PROOF(PROOF_BYTES)",
            "EXPORTER(DebugExporterBindingFrame.RECORD_BYTES)",
            "COMMITMENT(COMMIT_BYTES)",
            "input.read(",  # intentional low-level read only inside TLS record parser
        ):
            if piece != "input.read(":
                self.assertIn(piece, self.control)
        for piece in (
            "DebugTlsEngineHandshake.readRecord(wireInput)",
            "engine.unwrap(ByteBuffer.wrap(encoded), clear)",
            "engine.wrap(ByteBuffer.wrap(b, off, len), encrypted)",
            "result.handshakeStatus != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING",
            "result.bytesConsumed() != encoded.size",
            "result.bytesProduced() != phase.bytes",
            "result.bytesConsumed() != len",
            "length != phase.bytes",
            "engine.session.protocol != \"TLSv1.3\"",
            "wireOutput.write(record)",
            "record.fill(0)",
            "clear.array().fill(0)",
            "encrypted.array().fill(0)"
        ):
            self.assertIn(piece, self.control)
        self.assertIn("val packetSize = engine.session.packetBufferSize", self.control)
        self.assertIn("if (closed ||", self.control)
        self.assertIn("expected == Phase.QR_PROOF", self.control)
        self.assertIn("expected == Phase.EXPORTER", self.control)
        for forbidden in (
            "sendDebugText(", "TestPacketCodec", "MessageBody", "DataStore",
            "SharedPreferences", "openFileOutput(", "AudioRecord", "Recorder",
            "listenUsingInsecureRfcomm", "createInsecureRfcomm"
        ):
            self.assertNotIn(forbidden, self.control + self.probe + self.commit)

    def test_both_role_ordered_commitments_match_complete_fresh_qr_transcript(self):
        for piece in (
            "MessageDigest.isEqual(incoming.copyOfRange(6, RECORD_BYTES), local)",
            "incoming[5].toInt() != expectedRole",
            "if (isHost) {",
            "verify(input, false, commitment)",
            "verify(input, true, commitment)",
            "write(output, false, commitment)",
            "bytes.fill(0)",
            "incoming.fill(0)"
        ):
            self.assertIn(piece, self.commit)
        for piece in (
            "binding.hostPin",
            "binding.joinPin",
            "binding.hostNonce",
            "binding.joinNonce",
            "challenges.hostChallenge",
            "challenges.joinChallenge",
            "tlsExporter to 32"
        ):
            self.assertIn(piece, self.consent)
        self.assertIn("runHost(", self.proof)
        self.assertIn("runJoin(", self.proof)
        self.assertIn("fun exchange(", self.export)

    def test_rapid_expiry_and_cancel_never_turn_the_proof_into_messaging(self):
        for piece in (
            "fun freshBinding(): SessionProofBinding",
            "now in (expiresAtSeconds - 345L)..expiresAtSeconds",
            "pinsAndNonces.hostNonce.fill(0)",
            "pinsAndNonces.joinNonce.fill(0)",
            "pinsAndNonces.hostPin.fill(0)",
            "pinsAndNonces.joinPin.fill(0)"
        ):
            self.assertIn(piece, self.input)
        self.assertIn("current.binding(now)", self.qr)
        self.assertIn("DebugRfcommAttemptGate.End.QR_CHANNEL_AGREEMENT_PASSED", self.link)
        self.assertIn("closeQuietly(oldSocket)", self.link)
        self.assertIn("request.close()", self.link)
        self.assertIn("QR_CHANNEL_AGREEMENT_PASSED", self.gate)
        for name in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn("const val " + name + " = false", self.release)
        for name in ("BLUETOOTH_SCAN", "BLUETOOTH_CONNECT", "BLUETOOTH_ADVERTISE"):
            self.assertIn("android.permission." + name, self.debug_manifest)
            self.assertNotIn("android.permission." + name, self.main_manifest)
        for file in ("DebugTlsControlTransport.kt", "DebugBluetoothCommitmentFrame.kt"):
            self.assertFalse((MAIN / "java/com/edenpractical/comms/bluetooth" / file).exists())


if __name__ == "__main__":
    unittest.main()
