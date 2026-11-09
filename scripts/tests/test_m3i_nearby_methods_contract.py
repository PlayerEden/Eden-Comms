"""M3i/M6-A: real debug connection-method chooser, never release authority.

Static regressions only. Choosing an entrypoint is NOT a verified peer.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
ANDROID = ROOT / "app/src/main"
SRC = ANDROID / "java/com/edenpractical/comms"
DEBUG = ROOT / "app/src/debug"


class NearbyMethodSurfaceContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.main = (SRC / "MainActivity.kt").read_text()
        cls.catalog = (SRC / "model/NearbyMethodCatalog.kt").read_text()
        cls.release = (SRC / "model/ReleaseCapabilities.kt").read_text()
        cls.manifest = (ANDROID / "AndroidManifest.xml").read_text()
        cls.debug_manifest = (DEBUG / "AndroidManifest.xml").read_text()
        cls.pairing = (SRC / "pairing/LocalPairingTestScreen.kt").read_text()

    def test_home_connect_and_modes_go_to_one_user_method_chooser(self):
        self.assertEqual(2, self.main.count('onConnect = { navigate("connect") }'))
        self.assertEqual(2, self.main.count('onMethods = { navigate("connect") }'))
        self.assertEqual(2, self.main.count('onChatPreview = { navigate("connect") }'))
        self.assertEqual(2, self.main.count('onPtt = { navigate("connect") }'))
        self.assertIn('Text("Choose how phones connect")', self.main)
        self.assertIn('"connect" -> NearbyScreen(', self.main)
        self.assertIn('"wifi" -> NearbyScreen(', self.main)
        self.assertNotIn('private fun LocalWifiScreen(', self.main)
        self.assertNotIn("PAIRING COMING SOON", self.main)

    def test_chooser_has_three_debug_only_actions_and_no_fake_connection(self):
        picker = self.main.split("private fun NearbyScreen(", 1)[1].split(
            "private fun PushToTalkScreen()", 1
        )[0]
        self.assertEqual(3, picker.count("NearbyMethodCard(") - 1)  # definition
        for method, selection in (
            ("LAN", "LaunchDebugLanQr"),
            ("BLUETOOTH", "LaunchDebugBluetooth"),
            ("WIFI_DIRECT", "LaunchDebugWifiDirectPreflight"),
        ):
            self.assertIn(f"ConnectionMethod.{method}, debuggable", picker)
            self.assertIn(f"NearbyMethodCatalog.Selection.{selection}", picker)
        self.assertIn("onLocalWifi", picker)
        self.assertIn("onBluetooth()", picker)
        self.assertIn("onWifiDirect()", picker)
        self.assertIn('ConnectionStatus("Not connected"', picker)
        self.assertIn("Only after those checks can you use Text", picker)
        self.assertIn("A radio signal is not proof", picker)
        self.assertIn("No saved peer record can skip", picker)
        self.assertIn("unreviewed", picker.lower())
        self.assertNotIn("LocalPairingQrCodec", picker)
        self.assertNotIn("LanDemoSession(", picker)
        self.assertNotIn("startActivity(", picker)

    def test_pure_catalog_never_grants_release_or_network_trust(self):
        for marker in (
            "if (!debuggable) return Selection.NotAvailable",
            "Selection.LaunchDebugLanQr",
            "Selection.LaunchDebugBluetooth",
            "Selection.LaunchDebugWifiDirectPreflight",
            "Availability.DEBUG_TEST_ONLY",
            "Selection.NotAvailable",
            "No production discovery",
        ):
            self.assertIn(marker, self.catalog)
        for forbidden in ("BluetoothAdapter", "WifiP2pManager", "Socket(",
                          "discoverPeers(", "startDiscovery(", "startActivity(",
                          "approveForTest("):
            self.assertNotIn(forbidden, self.catalog)
        for flag in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn("const val " + flag + " = false", self.release)
        self.assertIn("FLAG_DEBUGGABLE", self.pairing)

    def test_root_never_gets_debug_radio_permissions_or_dynamic_socket(self):
        for blocked in (
            "android.permission.BLUETOOTH",
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.NEARBY_WIFI_DEVICES",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.RECORD_AUDIO",
        ):
            self.assertNotIn(blocked, self.manifest)
        for blocked in ("BluetoothAdapter", "WifiP2pManager",
                        "startDiscovery(", "discoverPeers(", "startScan("):
            self.assertNotIn(blocked, self.main + self.catalog)
        self.assertIn("FLAG_DEBUGGABLE", self.main)
        self.assertIn('android.permission.CAMERA', self.manifest)
        self.assertIn('android:exported="false"', self.debug_manifest)
        for name in ("DebugBluetoothDiscoveryActivity",
                     "DebugWifiDirectPreflightActivity"):
            self.assertIn(name, self.debug_manifest)
            self.assertNotIn(name, self.manifest)


if __name__ == "__main__":
    unittest.main()
