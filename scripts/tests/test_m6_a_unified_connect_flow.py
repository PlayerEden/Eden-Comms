"""M6-A: one visible Connect → Text/Talk → Disconnect debug user flow.

Static UI/source contracts only. They are neither screenshots nor proof that
Android radios, OEM permissions, two phones or security review have passed.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"
DEBUG = ROOT / "app/src/debug"
D = DEBUG / "java/com/edenpractical/comms"


class M6AUnifiedConnectFlow(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root = (MAIN / "MainActivity.kt").read_text()
        cls.catalog = (MAIN / "model/NearbyMethodCatalog.kt").read_text()
        cls.flags = (MAIN / "model/ReleaseCapabilities.kt").read_text()
        cls.main_manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text()
        cls.debug_manifest = (DEBUG / "AndroidManifest.xml").read_text()
        cls.shell = (D / "ui/DebugVerifiedConversationPanel.kt").read_text()
        cls.bt = (D / "bluetooth/DebugBluetoothDiscoveryActivity.kt").read_text()
        cls.p2p_host = (D / "wifidirect/DebugWifiDirectGroupHostActivity.kt").read_text()
        cls.p2p_join = (D / "wifidirect/DebugWifiDirectDiscoveryActivity.kt").read_text()
        cls.p2p_setup = (D / "wifidirect/DebugWifiDirectPreflightActivity.kt").read_text()

    def test_one_entry_for_connect_text_talk_and_no_fake_persistent_status(self):
        self.assertEqual(2, self.root.count('onConnect = { navigate("connect") }'))
        self.assertEqual(2, self.root.count('onChatPreview = { navigate("connect") }'))
        self.assertEqual(2, self.root.count('onPtt = { navigate("connect") }'))
        self.assertIn('"connect" -> NearbyScreen(', self.root)
        self.assertIn('ConnectionStatus("Not connected"', self.root)
        chooser = self.root.split("private fun NearbyScreen(", 1)[1].split(
            "private fun PushToTalkScreen()", 1
        )[0]
        self.assertIn('FieldSectionHeader("CONNECT", "Choose how the phones connect")', chooser)
        self.assertIn('method = NearbyMethodCatalog.current(ConnectionMethod.LAN)', chooser)
        self.assertIn('method = NearbyMethodCatalog.current(ConnectionMethod.BLUETOOTH)', chooser)
        self.assertIn('method = NearbyMethodCatalog.current(ConnectionMethod.WIFI_DIRECT)', chooser)
        self.assertIn('Text("USE SAME WI-FI")' if False else 'actionLabel = "USE SAME WI-FI"', chooser)
        self.assertIn('actionLabel = "USE BLUETOOTH"', chooser)
        self.assertIn('actionLabel = "USE WI-FI DIRECT"', chooser)
        self.assertIn("No saved peer record can skip", chooser)
        self.assertNotIn('current = true', chooser)
        self.assertNotIn('approveForTest()', chooser)

    def test_only_debug_launches_nonexported_existing_activities_and_release_off(self):
        for literal in (
            "FLAG_DEBUGGABLE", "NearbyMethodCatalog.select(method, debug)",
            "NearbyMethodCatalog.Selection.LaunchDebugBluetooth",
            "NearbyMethodCatalog.Selection.LaunchDebugWifiDirectPreflight",
            'Intent().setClassName(context, target)',
            "catch (_: RuntimeException)",
        ):
            self.assertIn(literal, self.root, literal)
        for name in ("DebugBluetoothDiscoveryActivity",
                     "DebugWifiDirectPreflightActivity"):
            self.assertIn(name, self.root)
            self.assertIn(name, self.debug_manifest)
            self.assertNotIn(name, self.main_manifest)
        self.assertIn('android:exported="false"', self.debug_manifest)
        self.assertIn("if (!debuggable) return Selection.NotAvailable", self.catalog)
        for flag in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {flag} = false", self.flags)
        for denied in ("BLUETOOTH_SCAN", "NEARBY_WIFI_DEVICES", "RECORD_AUDIO"):
            self.assertNotIn(denied, self.main_manifest)
        for bad in ("BluetoothAdapter", "WifiP2pManager", "Socket(",
                    "discoverPeers(", "startDiscovery("):
            self.assertNotIn(bad, self.root)

    def test_shared_chat_shell_is_presentation_only_and_has_real_disconnect(self):
        for required in (
            "internal fun DebugVerifiedConversationPanel(",
            "textContent: @Composable () -> Unit",
            "talkContent: @Composable () -> Unit",
            "privacyContent: @Composable () -> Unit",
            'if (selection == "text") textContent()',
            "else talkContent()",
            "onClick = onDisconnect",
            'Text("DISCONNECT THIS TEST SESSION")',
            "showPrivacy = !showPrivacy",
            "if (showPrivacy) privacyContent()",
            "remember { mutableStateOf(",
            ".heightIn(min = 52.dp)",
            "not independently reviewed",
        ):
            self.assertIn(required, self.shell, required)
        for forbidden in (
            "rememberSaveable", "BluetoothSocket", "WifiP2pManager",
            "LocalTlsIdentity", "AndroidKeystore", "approveForTest(",
            "sendDebugText(", "startActivity(", "startDiscovery(",
            "Log.", "println(", "SharedPreferences",
        ):
            self.assertNotIn(forbidden, self.shell, forbidden)
        self.assertFalse((ROOT / "app/src/release/java/com/edenpractical/comms/ui/DebugVerifiedConversationPanel.kt").exists())

    def test_three_connected_views_only_render_after_real_approval_and_can_stop(self):
        self.assertIn("chatReady &&", self.bt)
        self.assertIn("linkPhase == DebugRfcommAttemptGate.Phase.LINK_OPEN", self.bt)
        for view in (self.p2p_host, self.p2p_join):
            self.assertIn(
                "verifiedChat.phase == DebugWifiDirectApprovedTcpSession.Phase.CHAT",
                view
            )
        for view in (self.bt, self.p2p_host, self.p2p_join):
            self.assertIn("DebugVerifiedConversationPanel(", view)
            self.assertIn("DebugNearbyVoicePanel(localVoice)", view)
            self.assertIn("DebugVerifiedNearbyPrivacyPanel(localPrivacy)", view)
            self.assertIn("localVoice.disable()", view)
            self.assertIn("onDisconnect = {", view)
        self.assertIn("linkController.disconnect()", self.bt)
        self.assertIn("endHost(", self.p2p_host)
        self.assertIn("DebugWifiDirectGroupHostGate.End.USER_STOP", self.p2p_host)
        self.assertIn("targetController?.stop(", self.p2p_join)
        self.assertIn("DebugWifiDirectTargetLinkGate.End.USER_STOP", self.p2p_join)
        self.assertIn("stopByUser()", self.p2p_join)
        for view in (self.bt, self.p2p_host, self.p2p_join):
            self.assertIn("super.onStop()", view)

    def test_p2p_preflight_still_requires_explicit_permission_and_role(self):
        for required in (
            "ActivityResultContracts.RequestPermission()",
            "report?.state ==",
            "DebugWifiDirectReadinessPolicy.State.PREREQUISITES_OBSERVED",
            "DebugWifiDirectDiscoveryActivity::class.java",
            "DebugWifiDirectGroupHostActivity::class.java",
            "JOIN • FIND NEARBY HOST PHONE",
            "HOST • CREATE A TEMPORARY GROUP",
            "This setup check does not scan or connect",
        ):
            self.assertIn(required, self.p2p_setup, required)
        for blocked in ("discoverPeers(", "createGroup(", "connect(",
                        "requestPeers(", "Socket(", "getInputStream("):
            self.assertNotIn(blocked, self.p2p_setup)

if __name__ == "__main__":
    unittest.main()
