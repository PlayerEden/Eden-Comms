"""M3s-C1: one opt-in, debug-only UNVERIFIED Wi-Fi Direct target-link probe."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main"
DEBUG = ROOT / "app/src/debug"
PKG = DEBUG / "java/com/edenpractical/comms/wifidirect"
NS = "{http://schemas.android.com/apk/res/android}"


class M3sC1TargetLinkBoundary(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.link = (PKG / "DebugWifiDirectTargetLinkController.kt").read_text()
        cls.gate = (PKG / "DebugWifiDirectTargetLinkGate.kt").read_text()
        cls.discovery = (PKG / "DebugWifiDirectDiscoveryActivity.kt").read_text()
        cls.ledger = (PKG / "DebugWifiDirectCandidateLedger.kt").read_text()
        cls.reader = (PKG / "DebugWifiDirectCandidateReader.kt").read_text()

    def test_one_strictly_scoped_platform_target_connection(self):
        # Only this separate debug adapter can invoke Android OS connection.
        callers = [
            p.name for p in PKG.glob("*.kt")
            if re.search(r"(?<!cancel)\b(?:m|manager)\.connect\(", p.read_text())
        ]
        self.assertEqual(callers, ["DebugWifiDirectTargetLinkController.kt"])
        for marker in (
            "m.connect(c, config", "MacAddress.fromString(target.deviceAddress)",
            "WifiP2pConfig.Builder()", "enablePersistentMode(false)",
            "m.requestP2pState(c)", "m.requestGroupInfo(c)", "group != null",
            "gate.checkedExisting(token, group != null)",
            "gate.submit(token)", "gate.requestAccepted(token)",
            "gate.observedGroup(token, info.groupFormed)",
            "m.requestConnectionInfo(c)", "m.cancelConnect(c, object",
            "m.removeGroup(c, object", "finish(group == null)",
            "DebugWifiDirectTargetLinkGate.MAX_LINK_MS",
            "DebugWifiDirectTargetLinkGate.MAX_CLEANUP_MS",
            "current !== token", "readyAndForeground()",
            "current = null", "closeChannel(c)", "onFinished()",
            "connectionInfoOutstanding",
        ):
            self.assertIn(marker, self.link, marker)
        self.assertEqual(self.link.count("target.deviceAddress"), 1)
        self.assertNotIn("Log.", self.link)
        self.assertNotIn("println(", self.link)

    def test_explicit_second_action_and_one_shot_transient_object(self):
        for marker in (
            "if (targetController != null || !foreground || !registered || !prerequisites()",
            "Build.VERSION.SDK_INT < Build.VERSION_CODES.Q",
            "candidateLedger.selectedOrdinal(selectedToken) == null",
            "candidateLedger.consumeForOneShotLink(selectedToken)",
            "finishAttempt(", "DebugWifiDirectTargetLinkController(",
            "REQUEST 18-SECOND UNVERIFIED LINK TEST",
            "STOP LINK AND REQUEST GROUP REMOVAL",
            "targetController?.stop(DebugWifiDirectTargetLinkGate.End.BACKGROUND)",
            "QR preparation does NOT bind this",
            "targetController == null", "No user text, audio, or authenticated identity",
        ):
            self.assertIn(marker, self.discovery, marker)
        for marker in ("fun consumeForOneShotLink", "token !== current",
                       "selected == null", "clear()\n        sink(handle)"):
            self.assertIn(marker, self.ledger, marker)
        self.assertEqual(self.discovery.count("requestUnverifiedLink()"), 2)
        self.assertNotIn("deviceAddress", self.discovery)
        self.assertNotIn("deviceName", self.discovery)
        self.assertNotIn("Socket(", self.discovery)
        self.assertNotIn("requestConnectionInfo(", self.discovery)
        self.assertNotIn("connect(", self.discovery)

    def test_state_does_not_authenticate_on_os_callback(self):
        for marker in (
            "MAX_LINK_MS = 18_000L", "MAX_CLEANUP_MS = 4_000L",
            "enum class Phase { IDLE, CHECKING_EXISTING, READY, REQUESTING",
            "groupAlreadyExists", "phase = Phase.STOPPED",
            "phase != Phase.READY", "submitted = true",
            "phase = Phase.WAITING", "phase = Phase.GROUP_OBSERVED",
            "Phase.CLEANING", "current === token", "current = null",
            "generation never resets",
        ):
            self.assertIn(marker, self.gate, marker)
        for src in (self.discovery, self.ledger, self.link, self.gate):
            for forbidden in (
                "DebugApprovalGate", "SessionProofBinding", "DebugTlsEngineHandshake",
                "TestPacketCodec", "sendDebugText(", "sendPacket(",
                "ServerSocket(", "DatagramSocket(", "Socket(",
                "openFileOutput(", "SharedPreferences", "getLastKnownLocation(",
                "latitude", "longitude", "groupOwnerAddress", "getPassphrase(",
                "getNetworkName(", "getClientList(", "ClipboardManager",
                "Log.d(", "Log.i(", "Log.e("
            ):
                self.assertNotIn(forbidden, src, forbidden)
        self.assertIn("UNVERIFIED", self.link)
        self.assertIn("No sockets or messages", self.link)

    def test_release_and_permissions_unchanged(self):
        main = ET.parse(MAIN / "AndroidManifest.xml").getroot()
        debug = ET.parse(DEBUG / "AndroidManifest.xml").getroot()
        def permission_names(root):
            return [v.get(NS + "name") for v in root.findall("uses-permission")]
        self.assertEqual(set(permission_names(main)), {
            "android.permission.INTERNET", "android.permission.CAMERA",
            "android.permission.ACCESS_NETWORK_STATE"
        })
        self.assertEqual(set(permission_names(debug)), {
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.BLUETOOTH_CONNECT",
            "android.permission.BLUETOOTH_ADVERTISE",
            "android.permission.ACCESS_WIFI_STATE",
            "android.permission.NEARBY_WIFI_DEVICES",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.CHANGE_WIFI_STATE",
            "android.permission.CHANGE_NETWORK_STATE",
            "android.permission.RECORD_AUDIO",
        })
        self.assertEqual(len(permission_names(debug)), len(set(permission_names(debug))))
        self.assertFalse((MAIN / "java/com/edenpractical/comms/wifidirect").exists())
        flags = (MAIN / "java/com/edenpractical/comms/model/ReleaseCapabilities.kt").read_text()
        for flag in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn("const val " + flag + " = false", flags)
        release = (MAIN / "AndroidManifest.xml").read_text()
        self.assertNotIn("DebugWifiDirectTargetLinkController", release)


if __name__ == "__main__":
    unittest.main()
