"""M3l privacy-safe opaque signal staging is debug-only and never peer auth.

Static checks complement the JVM candidate-ledger tests and Android build.
They do not prove real Bluetooth candidate integrity or hardware correctness.
"""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
DEBUG = ROOT / "app/src/debug"
MAIN = ROOT / "app/src/main"
SRC = DEBUG / "java/com/edenpractical/comms/bluetooth"
APP = SRC / "DebugBluetoothDiscoveryActivity.kt"
LEDGER = SRC / "DebugBluetoothCandidateLedger.kt"
NS = "{http://schemas.android.com/apk/res/android}"


class BluetoothCandidateSelectionBoundaryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.activity = APP.read_text()
        cls.ledger = LEDGER.read_text()
        cls.manifest = (DEBUG / "AndroidManifest.xml").read_text()
        cls.main_manifest = (MAIN / "AndroidManifest.xml").read_text()
        cls.release = (
            MAIN / "java/com/edenpractical/comms/model/ReleaseCapabilities.kt"
        ).read_text()

    def test_debug_only_bounded_memory_with_no_names_addresses_or_radio_connect(self):
        self.assertFalse((MAIN / "java/com/edenpractical/comms/bluetooth"
                          / LEDGER.name).exists())
        self.assertIn("MAX_CANDIDATES = 12", self.ledger)
        self.assertIn("handles.any { it == handle }", self.ledger)
        self.assertIn("handles.clear()", self.ledger)
        self.assertIn("token !== current", self.ledger)
        self.assertIn("phase = Phase.REVIEW", self.ledger)
        self.assertIn("phase = Phase.SELECTED", self.ledger)
        self.assertIn("Unverified signal", self.ledger)
        self.assertNotIn("android.bluetooth", self.ledger)
        for forbidden in (
            "getName(", "getAddress(", ".name", ".address",
            ".createRfcommSocket", ".createInsecureRfcommSocket",
            "createBond(", "socket.connect(", "getBondState(", "bondedDevices",
            "SharedPreferences", "DataStore", "openFileOutput(",
            "Log.", "println(", "Socket(", "sendDebugText("
        ):
            self.assertNotIn(forbidden, self.activity + self.ledger, forbidden)
        for name in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn("const val " + name + " = false", self.release)

    def test_actual_broadcast_extras_held_only_after_active_bounded_scan(self):
        for required in (
            "discoveryState.candidateObserved(",
            "if (accepted) {",
            "discoveredDevice(intent)",
            "BluetoothDevice.EXTRA_DEVICE",
            "candidateLedger.observe(current, handle)",
            "candidateRows = candidateLedger.rows(current)",
            "finishCandidatesForReview()",
            "candidateLedger.finish(current)",
            "candidateLedger.select(token, ordinal)",
            "stopHardware()",
            "candidateLedger.selectedOrdinal()",
            "selectedOrdinal = candidateLedger.selectedOrdinal()",
            "if (!foreground || !hasScanPermission())",
            "override fun onStop()",
            "clearCandidateMemory()",
            "candidateLedger.clear()",
            "candidateToken = null",
            "candidateRows = emptyList()",
            "selectedOrdinal = null"
        ):
            self.assertIn(required, self.activity, required)
        self.assertIn("STAGE", self.activity)
        self.assertIn("FORGET SELECTED SIGNAL", self.activity)
        self.assertIn("CLEAR ALL SIGNAL RESULTS", self.activity)
        self.assertIn("STOP SEARCH AND CLEAR RESULTS", self.activity)
        self.assertIn("Verified Bluetooth messaging is not implemented",
                      self.activity)
        # Users cannot stage the same handles via any current production activity.
        self.assertFalse((MAIN / "java/com/edenpractical/comms/bluetooth"
                          / "DebugBluetoothDiscoveryActivity.kt").exists())

    def test_permissions_remain_scan_only_and_activity_is_not_exported(self):
        root = ET.fromstring(self.manifest)
        permissions = [e.get(NS + "name") for e in root.findall("uses-permission")]
        self.assertEqual(permissions, ["android.permission.BLUETOOTH_SCAN",
                                       "android.permission.BLUETOOTH_CONNECT",
                                       "android.permission.BLUETOOTH_ADVERTISE",
                                       "android.permission.ACCESS_WIFI_STATE",
                                       "android.permission.NEARBY_WIFI_DEVICES",
                                       "android.permission.ACCESS_FINE_LOCATION"])
        self.assertEqual(root.find("application/activity").get(NS + "exported"), "false")
        self.assertIn("android.permission.BLUETOOTH_CONNECT", self.manifest)
        self.assertIn("android.permission.BLUETOOTH_ADVERTISE", self.manifest)
        # These are only needed for explicitly requested debug RFCOMM links.
        for forbidden in ("BLUETOOTH_SCAN", "BLUETOOTH_CONNECT", "NEARBY_WIFI_DEVICES",
                          "ACCESS_FINE_LOCATION", "RECORD_AUDIO"):
            self.assertNotIn("android.permission." + forbidden, self.main_manifest)


if __name__ == "__main__":
    unittest.main()
