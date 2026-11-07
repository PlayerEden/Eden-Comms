"""M3p permission and radio preflight cannot become discovery, user text or voice."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main"
DEBUG = ROOT / "app/src/debug"
WIFI = DEBUG / "java/com/edenpractical/comms/wifidirect"
A = "{http://schemas.android.com/apk/res/android}"


class WifiDirectPermissionGuard(unittest.TestCase):
    def test_sensitive_permissions_are_only_in_debug_with_correct_scope(self):
        main = ET.parse(MAIN / "AndroidManifest.xml").getroot()
        debug = ET.parse(DEBUG / "AndroidManifest.xml").getroot()
        def perms(root):
            return {x.attrib[A + "name"]: x for x in root.findall("uses-permission")}
        release = perms(main)
        scoped = perms(debug)
        self.assertEqual(set(release), {
            "android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.CAMERA",
        })
        for name in ("ACCESS_WIFI_STATE", "NEARBY_WIFI_DEVICES", "ACCESS_FINE_LOCATION"):
            self.assertIn("android.permission." + name, scoped)
        self.assertEqual(scoped["android.permission.NEARBY_WIFI_DEVICES"].get(
            A + "usesPermissionFlags"), "neverForLocation")
        self.assertEqual(scoped["android.permission.ACCESS_FINE_LOCATION"].get(
            A + "maxSdkVersion"), "32")
        self.assertNotIn("android.permission.CHANGE_WIFI_STATE", scoped)
        self.assertFalse((MAIN / "java/com/edenpractical/comms/wifidirect").exists())
        feature = next(x for x in debug.findall("uses-feature")
                       if x.get(A + "name") == "android.hardware.wifi.direct")
        self.assertEqual(feature.get(A + "required"), "false")

    def test_one_tap_permission_checks_do_not_open_radio_or_trust(self):
        activity = (WIFI / "DebugWifiDirectPreflightActivity.kt").read_text()
        gate = (WIFI / "DebugWifiDirectPermissionAttemptGate.kt").read_text()
        policy = (WIFI / "DebugWifiDirectReadinessPolicy.kt").read_text()
        for x in ("FLAG_DEBUGGABLE", "ActivityResultContracts.RequestPermission()",
                  "Manifest.permission.NEARBY_WIFI_DEVICES",
                  "Manifest.permission.ACCESS_FINE_LOCATION",
                  "Build.VERSION_CODES.TIRAMISU", "WifiManager",
                  "isWifiEnabled", "LocationManager", "isLocationEnabled",
                  "Settings.Secure.LOCATION_MODE",
                  "override fun onStop()", "permissionGate.cancel()",
                  "foreground = false", "!requestOutstanding"):
            self.assertIn(x, activity, x)
        self.assertIn("fun complete(granted: Boolean, foreground: Boolean)", gate)
        self.assertIn("if (!outstanding) return Outcome.IGNORED", gate)
        self.assertIn("if (outstanding) cancelled = true", gate)
        self.assertIn("discoveryAllowed = false", policy)
        for text in ("discoverPeers(", "discoverServices(", "requestPeers(",
                     "createGroup(", "initialize(", "requestDeviceInfo(",
                     "connect(", "startScan(", "getScanResults(", "Socket(",
                     "DatagramSocket(", "openFileOutput(", "SharedPreferences",
                     "getLastKnownLocation(", "latitude", "longitude"):
            self.assertNotIn(text, activity + gate + policy, text)
        flags = (MAIN / "java/com/edenpractical/comms/model/ReleaseCapabilities.kt").read_text()
        for key in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn("const val " + key + " = false", flags)


if __name__ == "__main__":
    unittest.main()
