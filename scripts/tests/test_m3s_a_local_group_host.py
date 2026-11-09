"""M3s-A: local debug group host must never become peer trust or chat."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
DEBUG = ROOT / "app/src/debug"
MAIN = ROOT / "app/src/main"
PKG = DEBUG / "java/com/edenpractical/comms/wifidirect"
NS = "{http://schemas.android.com/apk/res/android}"


class GroupHostSafetyBoundary(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.activity = (PKG / "DebugWifiDirectGroupHostActivity.kt").read_text()
        cls.gate = (PKG / "DebugWifiDirectGroupHostGate.kt").read_text()
        cls.preflight = (PKG / "DebugWifiDirectPreflightActivity.kt").read_text()

    def test_user_opt_in_existing_group_safety_and_scoped_radio(self):
        for fragment in (
            "FLAG_DEBUGGABLE", "Build.VERSION_CODES.Q",
            "CREATE TEMPORARY P2P GROUP", "STOP AND REMOVE TEST GROUP",
            "if (!foreground || !registered || active != null) return",
            "humanTap = true", "group != null",
            "requestP2pState(", "requestGroupInfo(", "m.createGroup(",
            "setNetworkName(", "setPassphrase(", "SecureRandom()",
            "enablePersistentMode(false)", "WifiP2pConfig.Builder()",
            "requestGroupInfo(c) { group ->", "group?.isGroupOwner == true",
            "active !== current", "gate.active(current)",
            "gate.submitCreation(current)", "gate.groupSeen(current, true)",
            "20_000L", "4_000L", "m.removeGroup(",
            "gate.cleaned(current, confirmed)", "override fun onStop()",
            "foreground = false", "ContextCompat.RECEIVER_EXPORTED",
            "Wi-Fi Direct system settings", "cleanup"
        ):
            self.assertIn(fragment, self.activity, fragment)
        for fragment in (
            "phase != Phase.IDLE", "api < 29", "humanTap",
            "phase != Phase.CHECKING_EXISTING", "groupExists",
            "phase != Phase.READY", "creationSubmitted = true",
            "current === token", "Phase.CLEANING", "phase == Phase.CLEANING",
            "phase = Phase.STOPPED", "generation not reused",
        ):
            self.assertIn(fragment, self.gate, fragment)

    def test_zero_peer_target_network_or_message_capability(self):
        # This diagnostic only hosts a temporary local group; it never selects
        # another device or uses the future encrypted communication stream.
        for bad in (
            "requestPeers(", "deviceAddress", "deviceName", "EXTRA_P2P_DEVICE_LIST",
            "connect(", "cancelConnect(", "getConnectionInfo(",
            "passphrase",
            "getPassphrase(", "getNetworkName(", "getClientList(",
            "Socket(", "DatagramSocket(", "ServerSocket(", "openFileOutput(",
            "SharedPreferences", "Log.d(", "Log.i(", "Log.e(",
            "println(", "DebugBluetooth", "sendDebugText(",
            "sendPacket(", "DebugApprovalGate", "selectedHandle(",
            "selectedOrdinal(", "candidateLedger", "WifiP2pDevice"
        ):
            if bad == "passphrase":
                # Config creation necessarily needs an ephemeral passphrase,
                # but it may not be accessed from group information or logged.
                self.assertNotIn("group.passphrase", self.activity)
                continue
            self.assertNotIn(bad, self.activity, bad)
        self.assertNotIn("onClick = { startHost() }", self.preflight)
        self.assertIn("DebugWifiDirectGroupHostActivity::class.java", self.preflight)

    def test_no_release_permission_component_or_capability_change(self):
        release = ET.parse(MAIN / "AndroidManifest.xml").getroot()
        debug = ET.parse(DEBUG / "AndroidManifest.xml").getroot()
        def perms(root):
            return {e.get(NS + "name") for e in root.findall("uses-permission")}
        self.assertEqual(perms(release), {
            "android.permission.CAMERA",
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE"
        })
        self.assertEqual(len(perms(debug)), 9)
        self.assertIn("android.permission.RECORD_AUDIO", perms(debug))
        activities = debug.findall("application/activity")
        group_activities = [a for a in activities
            if "DebugWifiDirectGroupHostActivity" in a.get(NS + "name", "")]
        self.assertEqual(len(group_activities), 1)
        self.assertEqual(group_activities[0].get(NS + "exported"), "false")
        self.assertFalse((MAIN / "java/com/edenpractical/comms/wifidirect").exists())
        release_codes = (MAIN / "java/com/edenpractical/comms/model/ReleaseCapabilities.kt").read_text()
        for flag in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn("const val " + flag + " = false", release_codes)


if __name__ == "__main__":
    unittest.main()
