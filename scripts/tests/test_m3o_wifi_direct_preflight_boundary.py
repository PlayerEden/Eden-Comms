"""M3o read-only Wi-Fi Direct preflight must never become a scanner or live connection."""
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main"
DEBUG = ROOT / "app/src/debug"
PKG = DEBUG / "java/com/edenpractical/comms/wifidirect"


class WifiDirectPreflightBoundary(unittest.TestCase):
    def test_debug_only_and_no_runtime_radio_or_peer_actions(self):
        activity = (PKG / "DebugWifiDirectPreflightActivity.kt").read_text()
        policy = (PKG / "DebugWifiDirectPreflightPolicy.kt").read_text()
        main = (MAIN / "java/com/edenpractical/comms/MainActivity.kt").read_text()
        manifest = (DEBUG / "AndroidManifest.xml").read_text()
        release_manifest = (MAIN / "AndroidManifest.xml").read_text()
        self.assertIn("FLAG_DEBUGGABLE", activity)
        self.assertIn("FEATURE_WIFI_DIRECT", activity)
        self.assertIn("Context.WIFI_P2P_SERVICE", activity)
        self.assertIn("DebugWifiDirectPreflightActivity", main)
        self.assertIn("DebugWifiDirectPreflightActivity", manifest)
        self.assertFalse((MAIN / "java/com/edenpractical/comms/wifidirect").exists())
        self.assertNotIn("WifiDirectPreflightActivity", release_manifest)
        self.assertNotIn("NEARBY_WIFI_DEVICES", release_manifest)
        self.assertNotIn("ACCESS_FINE_LOCATION", release_manifest)
        self.assertIn("discoveryAllowed = false", policy)
        for word in (
            "discoverPeers(", "discoverServices(", "connect(", "createGroup(",
            "requestPeers(", "initialize(", "startScan(", "requestPermissions(",
            "requestDeviceInfo(", "Socket(", "DatagramSocket(",
            "registerReceiver(", "send(", "write(", "openFileOutput(", "SharedPreferences"
        ):
            self.assertNotIn(word, activity + policy, word)
        # M3p introduces debug-only permission prompts but still no P2P calls.
        for word in ("NEARBY_WIFI_DEVICES", "ACCESS_FINE_LOCATION", "ACCESS_WIFI_STATE"):
            self.assertIn('android:name="android.permission.' + word + '"', manifest)
        self.assertIn('android:name="android.permission.CHANGE_WIFI_STATE"', manifest)
        self.assertIn('android:name="android.permission.CHANGE_NETWORK_STATE"', manifest)
        self.assertIn('android:maxSdkVersion="32"', manifest)
if __name__ == "__main__":
    unittest.main()
