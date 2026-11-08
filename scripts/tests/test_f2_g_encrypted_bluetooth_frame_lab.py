"""F2-G: real paired JVM TLS transport is lab-only and never RFCOMM-enabled.

This source contract checks that the virtual paired-engine test covers
actual encrypted frame exchange, not just a plaintext codec round-trip.
No Android radios, identity enrollment, consent or secure release claimed.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
BT = ROOT / "app/src/debug/java/com/edenpractical/comms/bluetooth"
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"
TEST = ROOT / "app/src/test/java/com/edenpractical/comms/bluetooth"


class F2GEncryptedLabBoundary(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.lab = (BT / "DebugBluetoothFutureTlsTextLab.kt").read_text()
        cls.paired = (TEST / "DebugPairedTlsEngineInteropTest.kt").read_text()
        cls.probe = (BT / "DebugRfcommTlsProbe.kt").read_text()
        cls.controller = (BT / "DebugRfcommLinkController.kt").read_text()
        cls.release = (MAIN / "model/ReleaseCapabilities.kt").read_text()

    def test_actual_encrypt_decrypt_and_record_envelope_without_raw_fallback(self):
        required = (
            "engine.wrap(ByteBuffer.wrap(frame), encrypted)",
            "engine.unwrap(ByteBuffer.wrap(record), plaintext)",
            "DebugTlsRecordBoundary.requireSingleCiphertext(record)",
            "DebugTlsEngineHandshake.readRecord(wireInput)",
            "DebugTlsProviderReadiness.requireControlReady(engine)",
            "DebugTlsProviderReadiness.requireExactControlResult(",
            "DebugTlsProviderReadiness.requireEmptyTlsOnlyResult(",
            "ByteBuffer.allocate(engine.session.applicationBufferSize)",
            "ByteBuffer.allocate(engine.session.packetBufferSize)",
            "DebugBluetoothTextFrameCodec.decode(frame)",
            "DebugBluetoothTextFrameCodec.HEADER_BYTES",
            "DebugBluetoothTextFrameCodec.MAX_FRAME_BYTES",
            "DebugTlsControlTransport.MAX_NO_PLAINTEXT_RECORDS",
            "if (closed) throw SSLException(",
            "catch (failure: Exception)",
            "record.fill(0)", "plaintext.array().fill(0)",
            "encrypted.array().fill(0)",
        )
        for key in required:
            self.assertIn(key, self.lab, key)
        self.assertNotIn("engine.session.protocol ==", self.lab)  # true provider gate applies
        self.assertNotIn("sendDebugText(", self.lab)
        self.assertNotIn("TestPacketCodec", self.lab)
        self.assertNotIn("BluetoothSocket", self.lab.split("internal class ")[1])
        self.assertNotIn("ProductionSessionAuthorization", self.lab)

    def test_verified_two_endpoint_tls_fixture_includes_real_text_ack_and_forgery(self):
        for key in (
            "pairedTlsEnginesExchangeEncryptedTextAndPeerAppAckBothDirections()",
            "pairedTlsLabRejectsMalformedEncryptedRecordAndClosesItself()",
            "DebugTlsEngineHandshake.negotiate(host, pipe.hostInput, pipe.hostOutput)",
            "DebugTlsEngineHandshake.negotiate(join, pipe.joinInput, pipe.joinOutput)",
            "PinnedTlsPeer.requirePinnedTls13(host.session, joinIdentity.pin)",
            "PinnedTlsPeer.requirePinnedTls13(join.session, hostIdentity.pin)",
            "DebugBluetoothFutureTlsTextLab(host, pipe.hostInput, pipe.hostOutput)",
            "DebugBluetoothFutureTlsTextLab(join, pipe.joinInput, pipe.joinOutput)",
            'hostMessages.prepareText("Host says hello 👋")',
            'joinMessages.prepareText("Join replies — hej!")',
            "hostLab.transmit(fromHost)",
            "joinLab.receive()",
            "joinLab.transmit(accepted.ack)",
            "hostLab.receive()",
            "hostMessages.acceptAck(hostAck)",
            "joinMessages.acceptAck(joinAck)",
            "pipe.hostOutput.write(byteArrayOf(23, 3, 3, 0, 1, 0))",
            "hostLab.transmit(byteArrayOf(1, 2, 3))",
            "hostLab.clear()",
            "joinLab.clear()",
            "pipe.close()",
        ):
            self.assertIn(key, self.paired, key)

    def test_no_new_production_rights_or_live_probe_integration(self):
        for live in (self.probe, self.controller):
            self.assertNotIn("DebugBluetoothFutureTlsTextLab", live)
            self.assertNotIn("DebugBluetoothTextLedger", live)
            self.assertNotIn("DebugBluetoothTextFrameCodec", live)
        self.assertIn("closeQuietly(oldSocket)", self.controller)
        self.assertIn("stop(token, DebugRfcommCompletionPolicy.endAfterConsent(",
                      self.controller)
        self.assertFalse((MAIN / "bluetooth/DebugBluetoothFutureTlsTextLab.kt").exists())
        for flag in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {flag} = false", self.release)
        for disallowed in (
            "android.bluetooth", "BluetoothDevice", "BluetoothSocket(",
            "listenUsingInsecureRfcomm", "createInsecureRfcomm",
            "SharedPreferences", "openFileOutput(", "AudioRecord",
        ):
            self.assertNotIn(disallowed, self.lab)


if __name__ == "__main__":
    unittest.main()
