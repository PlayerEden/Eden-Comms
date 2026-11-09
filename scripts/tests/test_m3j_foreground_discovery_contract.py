"""M3j discovery model isolation and permission-scope contracts.

Static assertions complement JVM state-space tests. They do not constitute
Production Bluetooth/Wi-Fi Direct discovery, Android permission QA or security review.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"
ANDROID = ROOT / "app/src/main/AndroidManifest.xml"


class M3jDiscoveryBoundaryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.discovery = (MAIN / "transport/NearbyDiscoverySession.kt").read_text()
        cls.copy = (MAIN / "model/NearbyDiscoveryPolicy.kt").read_text()
        cls.screen = (MAIN / "MainActivity.kt").read_text()
        cls.catalog = (MAIN / "model/NearbyMethodCatalog.kt").read_text()
        cls.release = (MAIN / "model/ReleaseCapabilities.kt").read_text()
        cls.manifest = ANDROID.read_text()
        cls.guard = (ROOT / "scripts/check_security_review_boundary.sh").read_text()

    def test_explicit_opt_in_permission_and_foreground_fences(self):
        for marker in (
            "WAITING_FOR_PERSON_CONSENT",
            "WAITING_FOR_OS_PERMISSION",
            "READY_FOR_RADIO",
            "SCANNING",
            "APP_BACKGROUNDED",
            "PERMISSION_DENIED",
            "PERMISSION_REVOKED",
            "fun personConsented(",
            "fun platformPermissionResult(",
            "fun radioScanStarted(",
            "fun candidateObserved(",
            "fun onBackground(",
            "fun permissionRevoked(",
            "fun resetAfterStop(",
            "current === attempt",
            "MAX_SCAN_MS = 30_000L",
            "MAX_OBSERVATIONS = 32"
        ):
            self.assertIn(marker, self.discovery, marker)

    def test_model_is_not_connected_to_live_ui_or_transport_or_platform_apis(self):
        self.assertIn("NearbyDiscoverySession", self.discovery)
        for file in MAIN.rglob("*.kt"):
            if file.name == "NearbyDiscoverySession.kt":
                continue
            self.assertNotIn(
                "NearbyDiscoverySession",
                file.read_text(),
                "Future discovery model was wired into live app: " + str(file)
            )
        for forbidden in (
            "import android.", "import java.net.", "import javax.net.",
            "BluetoothAdapter", "WifiP2pManager", "startDiscovery(",
            "discoverPeers(", "requestPermissions(", "Socket(",
            "DataStore", "SharedPreferences", "openFileOutput("
        ):
            self.assertNotIn(forbidden, self.discovery)
        self.assertIn("model-only discovery state reached a live app codepath", self.guard)

    def test_product_method_chooser_does_not_start_auto_discovery_or_release_radio(self):
        picker = self.screen.split("private fun NearbyScreen(", 1)[1].split(
            "private fun PushToTalkScreen()", 1
        )[0]
        self.assertIn('FieldSectionHeader("CONNECT", "Choose how the phones connect")', picker)
        self.assertIn('title = "Why codes and approvals?"', picker)
        self.assertIn("FieldDisclosureCard(", picker)
        for field in ("trustStep", "privacyStep"):
            self.assertIn("NearbyDiscoveryPolicy." + field, picker)
        self.assertIn("Automatic Eden Comms peer discovery is OFF and NOT IMPLEMENTED", self.copy)
        # A button opens only an existing debug setup entry; this non-radio
        # chooser still has no auto scan, socket, approval or stored trust.
        self.assertIn("NearbyMethodCatalog.Selection.LaunchDebugLanQr", picker)
        self.assertIn("NearbyMethodCatalog.Selection.LaunchDebugBluetooth", picker)
        self.assertIn("NearbyMethodCatalog.Selection.LaunchDebugWifiDirectPreflight", picker)
        self.assertIn("ConnectionMethod.LAN, debuggable", picker)
        self.assertIn("ConnectionMethod.BLUETOOTH, debuggable", picker)
        self.assertIn("ConnectionMethod.WIFI_DIRECT, debuggable", picker)
        self.assertIn("Availability.DEBUG_TEST_ONLY", self.catalog)
        self.assertIn("if (!debuggable) return Selection.NotAvailable", self.catalog)
        self.assertNotIn("startDiscovery(", picker)
        self.assertNotIn("discoverPeers(", picker)
        self.assertNotIn("startActivity(", picker)
    def test_android_permissions_and_release_flags_remain_unchanged(self):
        for forbidden in (
            "android.permission.BLUETOOTH",
            "android.permission.NEARBY_WIFI_DEVICES",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.RECORD_AUDIO",
            "android.permission.ACCESS_COARSE_LOCATION"
        ):
            self.assertNotIn(forbidden, self.manifest)
        for name in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn("const val " + name + " = false", self.release)
        self.assertIn("android.permission.CAMERA", self.manifest)


if __name__ == "__main__":
    unittest.main()
