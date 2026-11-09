"""M3s-B: in-person QR metadata preparation never binds an unverified P2P target."""
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
PKG = ROOT / "app/src/debug/java/com/edenpractical/comms/wifidirect"
MAIN = ROOT / "app/src/main"
DEBUG = ROOT / "app/src/debug"
A = "{http://schemas.android.com/apk/res/android}"


class M3sBQrPreparation(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.codec = (PKG / "DebugWifiDirectQrCodec.kt").read_text()
        cls.coordinator = (PKG / "DebugWifiDirectQrCoordinator.kt").read_text()
        cls.screen = (PKG / "DebugWifiDirectQrActivity.kt").read_text()
        cls.preflight = (PKG / "DebugWifiDirectPreflightActivity.kt").read_text()

    def test_strict_three_code_freshness_and_local_identity(self):
        for marker in (
            'HOST_TAG = "ECWDH1"', 'JOIN_TAG = "ECWDJ1"',
            'ACK_TAG = "ECWDA1"', 'MAX_AGE_SECONDS = 180L',
            'FUTURE_SKEW_SECONDS = 45L', 'MAX_LENGTH = 170',
            'SecureRandom()', 'MessageDigest.isEqual(',
            'require(!MessageDigest.isEqual(host.pin, ownPin))',
            'require(MessageDigest.isEqual(reply.pin, ownPin))',
            'fun decodeAck(', 'fun comparisonCode(',
            'fun clear() { pin.fill(0); nonce.fill(0) }',
            'return value.split',
            'value.all { it.code in 33..126 }'
        ):
            self.assertIn(marker, self.codec, marker)
        for marker in (
            'AndroidKeystoreIdentity()', 'repository.existing()',
            'repository.createWithConsent()', 'Stage.HOST_SCAN_REPLY',
            'Stage.JOIN_SCAN_HOST', 'Stage.JOIN_SCAN_ACK',
            'DebugWifiDirectQrCodec.decodeAck(', 'DebugWifiDirectQrCodec.makeAck(',
            'host?.clear()', 'reply?.clear()', 'ack?.clear()',
            'qrPayload = ""', 'comparison = ""',
            'fun expireIfNeeded()', 'scannerGeneration++'
        ):
            self.assertIn(marker, self.coordinator, marker)

    def test_screen_is_explicit_debug_only_qr_and_erasable(self):
        for marker in (
            'FLAG_DEBUGGABLE', 'WindowManager.LayoutParams.FLAG_SECURE',
            'LocalPairingQrDisplay(', 'LocalPairingQrScanner(',
            'ADVANCED', 'IN-PERSON COMPARISON',
            'onPayload = preparation::scanned',
            'preparation.clear()', 'override fun onStop()',
            'handler.removeCallbacks(expireCheck)',
            'CREATE MY LOCAL DEVICE IDENTITY',
            '3 — SCAN THE HOST\'S ACKNOWLEDGEMENT QR'
        ):
            # ADVANCED QR check is in preflight, not the QR scanner screen.
            target = self.preflight if marker == "ADVANCED" else self.screen
            self.assertIn(marker, target, marker)
        self.assertIn('DebugWifiDirectQrActivity::class.java', self.preflight)
        self.assertIn('ADVANCED • QR CHECK WITHOUT RADIO', self.preflight)

    def test_zero_radio_target_or_communication_authority(self):
        for source in (self.codec, self.coordinator, self.screen):
            for bad in (
                'WifiP2pManager', 'requestDeviceInfo(', 'requestPeers(',
                'discoverPeers(', 'createGroup(', 'removeGroup(', 'connect(',
                'deviceAddress', 'deviceName', 'LOCAL_MAC_ADDRESS',
                'Socket(', 'ServerSocket(', 'DatagramSocket(', 'SharedPreferences',
                'openFileOutput(', 'Log.', 'println(', 'sendDebugText(',
                'sendPacket(', 'selectedHandle(',
                'selectedOrdinal(', 'WifiP2pConfig', 'groupOwnerAddress'
            ):
                self.assertNotIn(bad, source, bad)
        # F3-C carries ONLY a role/pin/nonce expectation to the live TLS
        # owner. No actual radio, socket, or send API is available here.
        self.assertNotIn("DebugBluetoothSocket", self.coordinator)
        self.assertNotIn("DebugBluetoothApprovedTextChannel", self.coordinator)
        self.assertIn("DebugBluetoothTlsProbeInput(", self.coordinator)
        self.assertNotIn("DebugBluetooth", self.codec + self.screen)
        manifest = ET.parse(DEBUG / "AndroidManifest.xml").getroot()
        activities = [
            x for x in manifest.findall("application/activity")
            if "DebugWifiDirectQrActivity" in x.get(A + "name", "")
        ]
        self.assertEqual(len(activities), 1)
        self.assertEqual(activities[0].get(A + "exported"), "false")
        self.assertFalse((MAIN / "java/com/edenpractical/comms/wifidirect").exists())
        release = (MAIN / "AndroidManifest.xml").read_text()
        self.assertNotIn('DebugWifiDirectQrActivity', release)
        for flag in ('CAN_CONNECT', 'CAN_SEND_MESSAGES', 'CAN_TRANSMIT_VOICE'):
            self.assertIn(
                'const val ' + flag + ' = false',
                (MAIN / "java/com/edenpractical/comms/model/ReleaseCapabilities.kt").read_text()
            )


if __name__ == "__main__":
    unittest.main()
