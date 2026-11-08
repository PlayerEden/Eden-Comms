"""F2-D guards: real JVM-engine fixture must stay unshipped and untrusted.

The hardware gate and independent protocol assessment are not satisfied by
this automated JVM simulation. No credentials may be added to the repository.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
TEST = ROOT / "app/src/test/java/com/edenpractical/comms/bluetooth/DebugPairedTlsEngineInteropTest.kt"
MAIN = ROOT / "app/src/main"
DEBUG = ROOT / "app/src/debug/java/com/edenpractical/comms/bluetooth"
RELEASE = MAIN / "java/com/edenpractical/comms/model/ReleaseCapabilities.kt"


class F2DInteropBoundary(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.code = TEST.read_text()
        cls.probe = (DEBUG / "DebugRfcommTlsProbe.kt").read_text()
        cls.controller = (DEBUG / "DebugRfcommLinkController.kt").read_text()
        cls.control = (DEBUG / "DebugTlsControlTransport.kt").read_text()
        cls.release = RELEASE.read_text()

    def test_two_real_engines_and_ephemeral_keytool_not_stubbed_mocks(self):
        for marker in (
            'Files.createTempDirectory("eden-f2d-test-only-")',
            '"-genkeypair"', '"-storetype", "PKCS12"',
            '"EC"', '"-keysize", "256"', '"-validity", "2"',
            'KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())',
            'Tls13PeerContext(hostIdentity.manager, hostExpected).unconnectedEngine(client = false)',
            'Tls13PeerContext(joinIdentity.manager, joinExpected).unconnectedEngine(client = true)',
            'DebugTlsEngineHandshake.negotiate(host, pipe.hostInput, pipe.hostOutput)',
            'DebugTlsEngineHandshake.negotiate(join, pipe.joinInput, pipe.joinOutput)',
            'PinnedTlsPeer.requirePinnedTls13(host.session, joinIdentity.pin)',
            'PinnedTlsPeer.requirePinnedTls13(join.session, hostIdentity.pin)',
            'DebugTlsProviderReadiness.requireControlReady(host)',
            'DebugTlsProviderReadiness.requireControlReady(join)',
            'DebugTlsControlTransport(host, pipe.hostInput, pipe.hostOutput)',
            'DebugTlsControlTransport(join, pipe.joinInput, pipe.joinOutput)',
            'DebugTlsControlTransport.Phase.values()',
            'DebugTlsControlTransport.Phase.CONFIRM',
        ):
            # CONFIRM comes from enum iteration, not a single hardcoded check;
            # keep this assertion on the real enum in the debug transport.
            if marker == 'DebugTlsControlTransport.Phase.CONFIRM':
                self.assertIn('CONFIRM(DebugBluetoothConsentFrame.RECORD_BYTES)',
                              (DEBUG / "DebugTlsControlTransport.kt").read_text())
            else:
                self.assertIn(marker, self.code, marker)

    def test_zero_plaintext_tls_records_are_strictly_bounded(self):
        self.assertIn("MAX_NO_PLAINTEXT_RECORDS = 2", self.control)
        self.assertIn("for (skipped in 0..MAX_NO_PLAINTEXT_RECORDS)", self.control)
        self.assertIn("if (result.bytesProduced() == 0)", self.control)
        self.assertIn("result, consumed = encoded.size, produced = 0", self.control)
        self.assertIn("result, consumed = encoded.size, produced = phase.bytes", self.control)
        self.assertIn("DebugTlsProviderReadiness.requireControlReady(engine)", self.control)
        self.assertIn("DebugTlsRecordBoundary.requireSingleCiphertext(encoded)", self.control)
        self.assertIn("if (skipped == MAX_NO_PLAINTEXT_RECORDS)", self.control)
        self.assertIn('throw SSLException("Too many TLS-only control-prefix records")',
                      self.control)
        # No decrypted data may be silently treated as a provider-only record.
        self.assertLess(
            self.control.index("if (result.bytesProduced() == 0)"),
            self.control.index("result, consumed = encoded.size, produced = 0")
        )

    def test_failure_coverage_and_disposable_key_cleanup(self):
        for marker in (
            'wrongOutOfBandPeerPinCannotCompleteMutualTlsHandshake()',
            'assertThrows(ExecutionException::class.java) { await(b) }',
            'incorrect.fill(0)', 'Files.deleteIfExists(',
            'process.waitFor(15, TimeUnit.SECONDS)',
            'future.get(15, TimeUnit.SECONDS)', 'workers.shutdownNow()',
            'pipe.close()', 'isDaemon = true',
            'h.clear()', 'j.clear()',
        ):
            self.assertIn(marker, self.code, marker)

    def test_fixture_absent_from_installed_android_and_release_flags_disabled(self):
        self.assertFalse((MAIN / "java/com/edenpractical/comms/bluetooth/DebugPairedTlsEngineInteropTest.kt").exists())
        self.assertFalse((DEBUG / "DebugPairedTlsEngineInteropTest.kt").exists())
        self.assertNotIn("DebugPairedTlsEngineInteropTest", self.probe + self.controller)
        self.assertIn("closeQuietly(oldSocket)", self.controller)
        self.assertIn("DebugRfcommCompletionPolicy.endAfterConsent(", self.controller)
        for cap in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {cap} = false", self.release)
        for blocked in ("sendDebugText(", "BluetoothSocket(", "SharedPreferences",
                        "MediaRecorder", "DataStore", "ProductionSessionAuthorization"):
            self.assertNotIn(blocked, self.code)


if __name__ == "__main__":
    unittest.main()
