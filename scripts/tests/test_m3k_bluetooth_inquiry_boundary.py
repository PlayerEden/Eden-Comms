"""M3k real debug-only Bluetooth inquiry boundary checks.

Source checks alone cannot prove Android permission behavior or real scans.
Physical handset checks remain separate until the owner requests them.
"""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main"
DEBUG = ROOT / "app/src/debug"
AND = "{http://schemas.android.com/apk/res/android}"
ACTIVITY = DEBUG / "java/com/edenpractical/comms/bluetooth/DebugBluetoothDiscoveryActivity.kt"


class DebugBluetoothInquiryContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.scanner = ACTIVITY.read_text()
        cls.main_manifest = (MAIN / "AndroidManifest.xml").read_text()
        cls.debug_manifest = (DEBUG / "AndroidManifest.xml").read_text()
        cls.app = (MAIN / "java/com/edenpractical/comms/MainActivity.kt").read_text()
        cls.model = (MAIN / "java/com/edenpractical/comms/transport/NearbyDiscoverySession.kt").read_text()
        cls.policy = (MAIN / "java/com/edenpractical/comms/model/NearbyDiscoveryPolicy.kt").read_text()
        cls.release = (MAIN / "java/com/edenpractical/comms/model/ReleaseCapabilities.kt").read_text()
        cls.audit = (ROOT / "scripts/audit_play_surface.py").read_text()

    def test_only_debug_manifest_has_bluetooth_scan_and_private_activity(self):
        self.assertNotIn("android.permission.BLUETOOTH", self.main_manifest)
        self.assertNotIn("android.permission.NEARBY_WIFI_DEVICES", self.main_manifest)
        self.assertNotIn("android.permission.ACCESS_FINE_LOCATION", self.main_manifest)
        self.assertNotIn("android.permission.RECORD_AUDIO", self.main_manifest)
        root = ET.fromstring(self.debug_manifest)
        permissions = [x.get(AND + "name") for x in root if x.tag == "uses-permission"]
        self.assertEqual([
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.BLUETOOTH_CONNECT",
            "android.permission.BLUETOOTH_ADVERTISE",
            "android.permission.ACCESS_WIFI_STATE",
            "android.permission.NEARBY_WIFI_DEVICES",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.CHANGE_WIFI_STATE",
            "android.permission.CHANGE_NETWORK_STATE"
        ], permissions)
        scan = next(x for x in root if x.tag == "uses-permission")
        self.assertEqual(scan.get(AND + "usesPermissionFlags"), "neverForLocation")
        activity = root.find("application/activity")
        self.assertEqual(activity.get(AND + "exported"), "false")
        self.assertIn("DebugBluetoothDiscoveryActivity", activity.get(AND + "name"))
        self.assertIn("DEBUG_ONLY_PERMISSIONS", self.audit)
        self.assertIn("approved_debug_only_permissions", self.audit)

    def test_scan_requires_explicit_user_action_and_os_grant(self):
        for marker in (
            "ActivityResultContracts.RequestPermission()",
            "scanPermission.launch(Manifest.permission.BLUETOOTH_SCAN)",
            "hasScanPermission()",
            "ContextCompat.checkSelfPermission(",
            "NearbyDiscoverySession()",
            "discoveryState.begin(ConnectionMethod.BLUETOOTH, foreground)",
            "discoveryState.personConsented(token, foreground)",
            "discoveryState.platformPermissionResult(token, foreground, granted = true)",
            "discoveryState.radioScanStarted(",
            "local.startDiscovery()",
            "registerReceiver(",
            "BluetoothDevice.ACTION_FOUND",
            "BluetoothAdapter.ACTION_DISCOVERY_FINISHED",
            "observations = discoveryState.snapshot().observations"
        ):
            self.assertIn(marker, self.scanner, marker)
        self.assertIn("START 30-SECOND TEST SEARCH", self.scanner)
        self.assertIn("STOP SEARCH", self.scanner)
        self.assertIn("enabled = !busy && pendingLink == PendingLink.NONE", self.scanner)

    def test_stop_scanning_and_stale_results_are_bounded_without_leaking_peer_identity(self):
        for marker in (
            "override fun onStop()",
            "discoveryState.onBackground(token)",
            "handler.removeCallbacks(timeout)",
            "adapter?.cancelDiscovery()",
            "unregisterReceiver(receiver)",
            "discoveryState.candidateObserved(",
            "discoveryState.complete(token)",
            "discoveryState.permissionRevoked(token)",
            "NearbyDiscoverySession.MAX_SCAN_MS",
            "discoveryState.resetAfterStop()",
            "radioRunning = started"
        ):
            self.assertIn(marker, self.scanner, marker)
        # M3l may hold EXTRA_DEVICE objects temporarily in debug memory, but
        # it must never query/display their names, MACs, bond information or
        # open a connection. No persistent radio results are allowed.
        self.assertIn("BluetoothDevice.EXTRA_DEVICE", self.scanner)
        for forbidden in (
            ".address", ".name", ".getAddress(", ".getName(",
            ".createRfcommSocket", "bondedDevices", "sendDebugText(",
            "DataStore", "SharedPreferences", "openFileOutput(", "Socket("
        ):
            self.assertNotIn(forbidden, self.scanner, forbidden)
        self.assertIn("MAX_OBSERVATIONS = 32", self.model)
        self.assertIn("MAX_SCAN_MS = 30_000L", self.model)
        self.assertIn("SCAN_FINISHED", self.model)

    def test_production_policies_remain_closed_and_debug_entry_is_hidden_in_release(self):
        for name in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn("const val " + name + " = false", self.release)
        self.assertIn("onBluetoothScan = {", self.app)
        self.assertIn("if (debuggable) {", self.app)
        self.assertIn('title = "Bluetooth discovery test"', self.app)
        self.assertIn("ApplicationInfo.FLAG_DEBUGGABLE != 0", self.app)
        self.assertIn("Automatic Eden Comms peer discovery is OFF and NOT IMPLEMENTED", self.policy)
        self.assertIn("Not scanning or connecting", self.app)
        # Debug-only scanner must not exist in the release source set.
        self.assertFalse((MAIN / "java/com/edenpractical/comms/bluetooth/DebugBluetoothDiscoveryActivity.kt").exists())


if __name__ == "__main__":
    unittest.main()
