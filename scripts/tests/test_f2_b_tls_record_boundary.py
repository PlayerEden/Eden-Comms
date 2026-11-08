"""F2-B: strict post-handshake TLS 1.3 control record integrity.

These are static source checks only, not two-device RF/TLS interoperability
or independent cryptographic audit. They must not permit Bluetooth app text.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
BT = ROOT / "app/src/debug/java/com/edenpractical/comms/bluetooth"
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"


class F2BTlsRecordBoundary(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.boundary = (BT / "DebugTlsRecordBoundary.kt").read_text()
        cls.controls = (BT / "DebugTlsControlTransport.kt").read_text()
        cls.handshake = (BT / "DebugTlsEngineHandshake.kt").read_text()
        cls.probe = (BT / "DebugRfcommTlsProbe.kt").read_text()
        cls.release = (MAIN / "model/ReleaseCapabilities.kt").read_text()

    def test_exact_ciphertext_validation_is_both_send_and_receive(self):
        self.assertIn("MAX_CIPHERTEXT_BODY = 16_384 + 256", self.boundary)
        self.assertIn("record.size != HEADER_BYTES + length", self.boundary)
        self.assertIn("(record[0].toInt() and 0xff) != ENCRYPTED_TYPE", self.boundary)
        self.assertIn("(record[1].toInt() and 0xff) != 3", self.boundary)
        self.assertIn("(record[2].toInt() and 0xff) != 3", self.boundary)
        self.assertIn("DebugTlsRecordBoundary.requireSingleCiphertext(encoded)", self.controls)
        self.assertIn("DebugTlsRecordBoundary.requireSingleCiphertext(record)", self.controls)
        self.assertLess(self.controls.index("DebugTlsRecordBoundary.requireSingleCiphertext(encoded)"),
                        self.controls.index("engine.unwrap(ByteBuffer.wrap(encoded), clear)"))
        self.assertLess(self.controls.index("DebugTlsRecordBoundary.requireSingleCiphertext(record)"),
                        self.controls.index("wireOutput.write(record)"))

    def test_both_control_and_handshake_buffers_clear_on_exception(self):
        for part in (
            "clear.array().fill(0)",
            "encoded.fill(0)",
            "record.fill(0)",
            "encrypted.array().fill(0)",
        ):
            self.assertIn(part, self.controls)
        for part in (
            "body.fill(0)", "record.fill(0)", "packet.fill(0)",
            "outputRecord.array().fill(0)", "app.array().fill(0)",
        ):
            self.assertIn(part, self.handshake)
        self.assertGreaterEqual(self.controls.count("finally {"), 4)
        self.assertGreaterEqual(self.handshake.count("finally {"), 4)

    def test_existing_order_and_consent_and_release_denial_remain_intact(self):
        for part in ("DebugTlsEngineHandshake.negotiate(",
                     "PinnedTlsPeer.requirePinnedTls13(",
                     "DebugBluetoothConsentExchange.run("):
            self.assertIn(part, self.probe)
        self.assertIn("expected == Phase.APPROVAL && next == Phase.CONFIRM",
                      self.controls)
        self.assertIn("engine.session.protocol != \"TLSv1.3\"", self.controls)
        for cap in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {cap} = false", self.release)
        for disallowed in ("TestPacketCodec", "sendDebugText(", "AudioRecord",
                           "SSLSocketFactory", "createInsecureRfcomm",
                           "listenUsingInsecureRfcomm"):
            self.assertNotIn(disallowed, self.controls + self.boundary +
                             self.handshake + self.probe)


if __name__ == "__main__":
    unittest.main()
