"""M3m: debug-only user-consented secure RFCOMM socket, not trusted text.

Verifies source/permission boundaries and protocol absence. CI success does
NOT prove two phones can connect; OEM permissions and pairing remain untested.
"""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "app/src/debug/java/com/edenpractical/comms/bluetooth"
MAIN = ROOT / "app/src/main"
NAMESPACE = "{http://schemas.android.com/apk/res/android}"


class DebugRfcommContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.controller = (SRC / "DebugRfcommLinkController.kt").read_text()
        cls.gate = (SRC / "DebugRfcommAttemptGate.kt").read_text()
        cls.screen = (SRC / "DebugBluetoothDiscoveryActivity.kt").read_text()
        cls.ledger = (SRC / "DebugBluetoothCandidateLedger.kt").read_text()
        cls.manifest = ET.parse(ROOT / "app/src/debug/AndroidManifest.xml").getroot()
        cls.release = (MAIN / "java/com/edenpractical/comms/model/ReleaseCapabilities.kt").read_text()
        cls.main_manifest = (MAIN / "AndroidManifest.xml").read_text()
        cls.audit = (ROOT / "scripts/audit_play_surface.py").read_text()

    def test_real_secure_classic_host_and_join_without_app_payloads(self):
        for item in (
            "listenUsingRfcommWithServiceRecord(",
            "device.createRfcommSocketToServiceRecord(SERVICE_UUID)",
            "created.accept(HOST_WAIT_MS.toInt())",
            "created.connect()",
            "adapter.cancelDiscovery()",
            "closeQuietly(oldServer)",
            "closeQuietly(oldSocket)",
            "Thread({",
            "HOST_WAIT_MS = 60_000L",
            "JOIN_TIMEOUT_MS = 18_000L",
            "LINK_LIFETIME_MS = 45_000L",
            "private fun claimSocket(",
            "gate.opened(",
            "active === token",
            "closeQuietly(created)",
        ):
            self.assertIn(item, self.controller, item)
        for forbidden in (
            "listenUsingInsecureRfcomm", "createInsecureRfcomm",
            ".inputStream", ".outputStream", "getInputStream(",
            "getOutputStream(", ".write(", ".read(", "DataOutputStream",
            "sendDebugText(", "TestPacketCodec", "HttpURLConnection",
            "SharedPreferences", "DataStore", "openFileOutput("
        ):
            self.assertNotIn(forbidden, self.controller, forbidden)
        self.assertIn("isForeground()", self.controller)
        self.assertIn("hasConnectPermission()", self.controller)
        self.assertIn("gate.stop(token, why)", self.controller)

    def test_foreground_permission_and_user_actions_are_separate(self):
        for item in (
            "ActivityResultContracts.RequestMultiplePermissions()",
            "Manifest.permission.BLUETOOTH_CONNECT",
            "Manifest.permission.BLUETOOTH_ADVERTISE",
            "ActivityResultContracts.StartActivityForResult()",
            "BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE",
            "BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120",
            "result.resultCode > 0",
            "beginHostLink()",
            "beginJoinLink()",
            "linkController.host(hostAdapter, request)",
            "linkController.join(target, joinAdapter, request)",
            "candidateLedger.selectedHandle()",
            "linkController.disconnect()",
            "linkController.background()",
            "linkController.permissionLost()",
            "onStop()",
            "watchLinkPermission()",
            "linkPermissionMonitor",
            "HOST ONE-TIME BLUETOOTH TEST LINK",
            "JOIN STAGED UNVERIFIED SIGNAL",
            "DISCONNECT BLUETOOTH TEST LINK",
        ):
            self.assertIn(item, self.screen, item)
        self.assertNotIn("data class Peer", self.controller)
        self.assertIn("verified", self.screen.lower())
        self.assertIn("no messages", (self.controller + self.screen).lower())

    def test_debug_only_exact_permissions_and_no_release_capabilities(self):
        declared = [e.get(NAMESPACE + "name")
                    for e in self.manifest.findall("uses-permission")]
        self.assertEqual(declared, [
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.BLUETOOTH_CONNECT",
            "android.permission.BLUETOOTH_ADVERTISE"
        ])
        self.assertEqual(self.manifest.find("application/activity").get(
            NAMESPACE + "exported"), "false")
        self.assertEqual(self.audit.count('"android.permission.BLUETOOTH_CONNECT"'), 1)
        for forbidden in (
            "android.permission.BLUETOOTH", "android.permission.NEARBY_WIFI_DEVICES",
            "android.permission.RECORD_AUDIO", "android.permission.ACCESS_FINE_LOCATION"
        ):
            self.assertNotIn(forbidden, self.main_manifest)
        for flag in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn("const val " + flag + " = false", self.release)
        for file in ("DebugRfcommLinkController.kt", "DebugRfcommAttemptGate.kt"):
            self.assertFalse((MAIN / "java/com/edenpractical/comms/bluetooth" / file).exists())

    def test_callback_anti_replay_and_no_auto_retry(self):
        for item in (
            "token === current", "Phase.CONNECTING",
            "Phase.LINK_OPEN", "Phase.STOPPED", "generation == Long.MAX_VALUE",
            "fun opened(current: Token, foreground: Boolean, permitted: Boolean)",
            "fun stop(current: Token, why: End): Boolean",
            "fun reset(): Boolean"
        ):
            self.assertIn(item, self.gate, item)
        self.assertIn("if (active !== token) return",
                      self.controller)
        self.assertIn("if (active != null || gate.snapshot().phase !=",
                      self.controller)
        self.assertNotIn("autoReconnect", self.controller)
        self.assertNotIn("reconnect(", self.controller)


if __name__ == "__main__":
    unittest.main()
