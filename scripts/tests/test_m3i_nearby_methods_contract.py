"""M3i live routing/discovery guardrails. These are static checks, NOT handset QA."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
ANDROID = ROOT / "app/src/main"
SRC = ANDROID / "java/com/edenpractical/comms"


class NearbyMethodSurfaceContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.main = (SRC / "MainActivity.kt").read_text()
        cls.catalog = (SRC / "model/NearbyMethodCatalog.kt").read_text()
        cls.release = (SRC / "model/ReleaseCapabilities.kt").read_text()
        cls.manifest = (ANDROID / "AndroidManifest.xml").read_text()
        cls.pairing = (SRC / "pairing/LocalPairingTestScreen.kt").read_text()

    def test_one_tap_home_connect_and_separate_discoverable_method_chooser(self):
        self.assertIn('onConnect = { navigate("pairing-test") }', self.main)
        self.assertIn('onMethods = { navigate("connect") }', self.main)
        self.assertIn('Text("Other connection options")', self.main)
        self.assertIn('"connect" -> NearbyScreen(onLocalWifi = { navigate("pairing-test") })',
                      self.main)
        self.assertIn('"wifi" -> NearbyScreen(onLocalWifi = { navigate("pairing-test") })',
                      self.main)
        self.assertNotIn('private fun LocalWifiScreen(', self.main)
        self.assertNotIn("PAIRING COMING SOON", self.main)
        self.assertNotIn("Text and voice are still disabled", self.main)
        self.assertIn('"connect" -> "Connection methods"', self.main)

    def test_only_lan_is_clickable_and_debug_gated(self):
        picker = self.main.split("private fun NearbyScreen(", 1)[1].split(
            "private fun PushToTalkScreen()", 1
        )[0]
        self.assertIn("NearbyMethodCatalog.current(ConnectionMethod.LAN)", picker)
        self.assertIn("NearbyMethodCatalog.select(", picker)
        self.assertIn("NearbyMethodCatalog.Selection.LaunchDebugLanQr", picker)
        self.assertIn("NearbyMethodCatalog.canEnterDebugLan(debuggable)", picker)
        self.assertIn("enabled = NearbyMethodCatalog.canEnterDebugLan(debuggable)", picker)
        future = picker.split('title = "Other connection methods"', 1)[1]
        self.assertIn("FieldDisclosureCard(", picker)
        self.assertNotIn("onClick", future)
        self.assertNotIn("Button(", future)
        self.assertIn("Bluetooth and Wi-Fi Direct — planned, not available for normal use.", future)
        self.assertIn("Discovery alone never proves a phone is trusted or permits messaging.", future)
        self.assertIn("unreviewed", picker.lower())
        self.assertIn("No account, internet service or manual IP entry", picker)
        self.assertNotIn("LocalPairingQrCodec", picker)
        self.assertNotIn("LanDemoSession(", picker)

    def test_method_catalog_never_claims_discovery_or_release_authorization(self):
        self.assertIn("if (debuggable && method == ConnectionMethod.LAN)", self.catalog)
        self.assertIn("Selection.LaunchDebugLanQr", self.catalog)
        self.assertIn("Selection.NotAvailable", self.catalog)
        self.assertIn("Availability.PLANNED", self.catalog)
        for flag in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn("const val " + flag + " = false", self.release)
        self.assertIn("FLAG_DEBUGGABLE", self.pairing)

    def test_no_new_radio_or_location_permissions_or_scanner_was_inserted(self):
        for blocked in (
            "android.permission.BLUETOOTH",
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.NEARBY_WIFI_DEVICES",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.RECORD_AUDIO"
        ):
            self.assertNotIn(blocked, self.manifest)
        for blocked in ("BluetoothAdapter", "WifiP2pManager",
                        "startDiscovery(", "discoverPeers(", "startScan("):
            self.assertNotIn(blocked, self.main + self.catalog)
        self.assertIn('android.permission.CAMERA', self.manifest)


if __name__ == "__main__":
    unittest.main()
