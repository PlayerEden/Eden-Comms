"""M3q must remain bounded, debug-only discovery with zero peer identity."""
import pathlib
import unittest
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[2]
DEBUG = ROOT / "app/src/debug"
MAIN = ROOT / "app/src/main"
WIFI = DEBUG / "java/com/edenpractical/comms/wifidirect"
NS = "{http://schemas.android.com/apk/res/android}"


class M3qSourceBoundary(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.activity = (WIFI / "DebugWifiDirectDiscoveryActivity.kt").read_text()
        cls.gate = (WIFI / "DebugWifiDirectDiscoveryGate.kt").read_text()
        cls.preflight = (WIFI / "DebugWifiDirectPreflightActivity.kt").read_text()
        cls.release = (MAIN / "java/com/edenpractical/comms/model/ReleaseCapabilities.kt").read_text()

    def test_user_start_forward_only_platform_and_bounded_cancellation(self):
        for phrase in (
            "FLAG_DEBUGGABLE", "START 30-SECOND SEARCH", "STOP SEARCH",
            "WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION",
            "WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION",
            "WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION",
            "m.initialize(", "m.requestP2pState(c)", "m.discoverPeers(c,",
            "activeChannel.close()", "stopPeerDiscovery(", "override fun onStop()",
            "foreground = false", "permissionName()", "PERMISSION_GRANTED",
            "PackageManager.FEATURE_WIFI_DIRECT", "isLocationEnabled",
            "isWifiEnabled", "onRadioReported(", "token !== active",
            "gate.requestSent(active)", "gate.discoveryAccepted(active)",
            "handler.postDelayed(it, DebugWifiDirectDiscoveryGate.MAX_MS)",
            "ContextCompat.RECEIVER_EXPORTED",
        ):
            # Verify the call/guard exists, not that actual radio QA passed.
            self.assertIn(phrase, self.activity, phrase)
        self.assertIn("DebugWifiDirectDiscoveryActivity::class.java", self.preflight)
        for phrase in ("MAX_MS = 30_000L", "MAX_EVENTS = 32",
                       "phase != Phase.IDLE", "current === token",
                       "phase != Phase.STARTING", "Phase.SCANNING",
                       "generation == Long.MAX_VALUE", "generation intentionally never resets"):
            self.assertIn(phrase, self.gate, phrase)

    def test_no_peers_or_message_channel_and_no_new_release_surface(self):
        activity = self.activity
        for bad in (
            "requestPeers(", "EXTRA_P2P_DEVICE_LIST", "deviceAddress", "deviceName",
            "getDeviceAddress(", "createGroup(", "removeGroup(", "connect(",
            "getConnectionInfo(", "requestConnectionInfo(", "Socket(",
            "DatagramSocket(", "TestPacketCodec", "DebugApprovalGate",
            "sendDebugText(", "sendPacket(", "SharedPreferences",
            "openFileOutput(", "getLastKnownLocation(", "latitude", "longitude"
        ):
            self.assertNotIn(bad, activity, bad)
        self.assertNotIn("m.discoverPeers(", self.preflight)
        self.assertNotIn("DebugWifiDirectDiscoveryActivity", (MAIN / "AndroidManifest.xml").read_text())
        self.assertFalse((MAIN / "java/com/edenpractical/comms/wifidirect").exists())
        for flag in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn("const val " + flag + " = false", self.release)

    def test_only_reviewed_normal_permissions_added_to_debug(self):
        main = ET.parse(MAIN / "AndroidManifest.xml").getroot()
        debug = ET.parse(DEBUG / "AndroidManifest.xml").getroot()
        def permission_names(root):
            return [item.get(NS + "name") for item in root.findall("uses-permission")]
        self.assertEqual(set(permission_names(main)), {
            "android.permission.INTERNET", "android.permission.CAMERA",
            "android.permission.ACCESS_NETWORK_STATE"
        })
        for name in ("CHANGE_WIFI_STATE", "CHANGE_NETWORK_STATE"):
            self.assertIn("android.permission." + name, permission_names(debug))
        activities = debug.findall("application/activity")
        target = [x for x in activities if x.get(NS + "name", "").endswith(
            ".DebugWifiDirectDiscoveryActivity")]
        self.assertEqual(len(target), 1)
        self.assertEqual(target[0].get(NS + "exported"), "false")
        self.assertEqual(len(permission_names(debug)), len(set(permission_names(debug))))


if __name__ == "__main__":
    unittest.main()
