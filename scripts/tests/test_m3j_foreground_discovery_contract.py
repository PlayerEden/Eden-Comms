"""M3j discovery model isolation and permission-scope contracts.

Static assertions complement JVM state-space tests. They do not constitute
Bluetooth/Wi-Fi Direct discovery, Android permission QA or security review.
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

    def test_future_ui_is_informational_and_current_wifi_remains_available(self):
        picker = self.screen.split("private fun NearbyScreen(", 1)[1].split(
            "private fun PushToTalkScreen()", 1
        )[0]
        self.assertIn('SectionLabel("HOW FUTURE DISCOVERY WILL WORK")', picker)
        for field in ("currentStatus", "optInStep", "permissionStep",
                      "foregroundStep", "trustStep", "privacyStep"):
            self.assertIn("NearbyDiscoveryPolicy." + field, picker)
        self.assertIn("Nearby device search is OFF and NOT IMPLEMENTED", self.copy)
        future = picker.split('SectionLabel("FUTURE METHODS • NOT AVAILABLE")', 1)[1]
        self.assertNotIn("Button(", future)
        self.assertNotIn("onClick", future)
        self.assertIn("NearbyMethodCatalog.canEnterDebugLan(debuggable)", picker)
        self.assertIn("NearbyMethodCatalog.Selection.LaunchDebugLanQr", picker)
        self.assertIn("Availability.PLANNED", self.catalog)

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
