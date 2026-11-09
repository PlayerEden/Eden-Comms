"""M6-C: focused UX/TalkBack and privacy disclosure regression checks.

Static source contracts only: not a screenshot, large-font emulator pass,
physical TalkBack observation, Android permission QA or security audit.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"
DEBUG = ROOT / "app/src/debug/java/com/edenpractical/comms"


class M6CAccessibilityPolish(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root = (MAIN / "MainActivity.kt").read_text()
        cls.privacy = (MAIN / "privacy/PrivacyPreviewContent.kt").read_text()
        cls.chat = (DEBUG / "ui/DebugVerifiedConversationPanel.kt").read_text()
        cls.voice = (DEBUG / "audio/DebugNearbyVoicePanel.kt").read_text()
        cls.audio = (DEBUG / "audio/DebugNearbyVoiceOwner.kt").read_text()
        cls.bt = (DEBUG / "bluetooth/DebugBluetoothDiscoveryActivity.kt").read_text()
        cls.host = (DEBUG / "wifidirect/DebugWifiDirectGroupHostActivity.kt").read_text()
        cls.join = (DEBUG / "wifidirect/DebugWifiDirectDiscoveryActivity.kt")

    def test_text_button_always_releases_or_cancels_talk_before_hiding(self):
        for expected in (
            "val chooseText = {",
            'if (selection == "talk") onLeaveTalk()',
            'onClick = chooseText',
            "DisposableEffect(selection)",
            "onLeaveTalk: () -> Unit",
        ):
            self.assertIn(expected, self.chat, expected)
        self.assertNotIn('onClick = { selection = "text" }', self.chat)
        for screen in (self.bt, self.host, self.join):
            self.assertIn("onLeaveTalk = { localVoice.release() }", screen)
        for expected in (
            "if (pendingFloor)", "cancelFloor?.invoke()",
            "audio.finishCapture()", "fun disable()", "audio.cancelAll()"
        ):
            self.assertIn(expected, self.audio)

    def test_chat_cards_and_selected_tabs_are_accessible_and_consistent(self):
        for expected in (
            "shape = CutCornerShape(7.dp)",
            "color = MaterialTheme.colorScheme.surface",
            "border = BorderStroke(",
            "modifier = Modifier.semantics { heading() }",
            '"Text mode selected"', '"Text mode not selected"',
            '"Talk mode selected"', '"Talk mode not selected"',
            'if (showPrivacy) "Expanded" else "Collapsed"',
            'if (showQa) "Expanded" else "Collapsed"',
            ".heightIn(min = 52.dp)", ".heightIn(min = 48.dp)",
        ):
            self.assertIn(expected, self.chat, expected)
        self.assertNotIn("RoundedCornerShape(8.dp)", self.chat)
        # Presentation is not a credential or a radio owner.
        for disallowed in (
            "BluetoothSocket", "WifiP2pManager", "SSLEngine",
            "approveForTest(", "startDiscovery(", "requestVoiceFloor(",
            "KeyStore", "SharedPreferences", "AudioRecord",
        ):
            self.assertNotIn(disallowed, self.chat)

    def test_microphone_permission_refreshes_without_automatic_request(self):
        for expected in (
            "DisposableEffect(context)", "LifecycleEventObserver",
            "Lifecycle.Event.ON_RESUME",
            "ContextCompat.checkSelfPermission(",
            "Manifest.permission.RECORD_AUDIO",
            "PackageManager.PERMISSION_GRANTED",
            "lifecycle?.addObserver(observer)",
            "lifecycle?.removeObserver(observer)",
            "permission.launch(Manifest.permission.RECORD_AUDIO)",
            "liveRegion = LiveRegionMode.Polite",
            "modifier = Modifier.semantics { heading() }",
            "activate once to request a turn",
            "activate again to stop or cancel",
            "Audio sends while you hold",
        ):
            self.assertIn(expected, self.voice, expected)
        # Permission request remains solely a visible deliberate button.
        self.assertEqual(
            1, self.voice.count("permission.launch(Manifest.permission.RECORD_AUDIO)")
        )
        assert self.voice.index("permission.launch(Manifest.permission.RECORD_AUDIO)") > (
            self.voice.index("onClick = {")
        )
        self.assertNotIn("LaunchedEffect(", self.voice)
        self.assertNotIn("SharedPreferences", self.voice)
        self.assertNotIn("startActivity(", self.voice)

    def test_home_help_and_legacy_ptt_navigation_reflect_implemented_debug_core(self):
        for expected in (
            "Session-only by default. Local encrypted history is optional",
            "Connect. Text or talk. Disconnect.",
            "No accounts, cloud relay or production-secure messaging.",
            "Switch(",
            'contentDescription = "Dark appearance"',
            "modifier = Modifier.semantics { heading() }",
            '"ptt" -> NearbyScreen(',
            "NearbyMethodCatalog.select(method, debug)",
        ):
            self.assertIn(expected, self.root, expected)
        self.assertNotIn('"ptt" -> PushToTalkScreen()', self.root)
        self.assertNotIn('"Nearby conversations, without permanent chat history."', self.root)
        self.assertNotIn("No normal messaging, voice, account or saved conversations", self.root)

    def test_privacy_page_tracks_three_debug_methods_and_real_optional_storage(self):
        for expected in (
            "Same Wi-Fi", "Bluetooth Classic", "Wi-Fi Direct",
            "short typed TEST messages",
            "both phones approve and confirm",
            "live push-to-talk",
            "session-only", "Keystore AES-GCM", "no-backup storage",
            "separate encrypted local storage",
            "1–30 days", "never guaranteed physical",
            "Microphone permission", "no background microphone",
            "Production secure messaging remains disabled",
        ):
            self.assertIn(expected.lower(), self.privacy.lower(), expected)
        for obsolete in (
            "Bluetooth and Wi-Fi Direct voice remain unavailable",
            "DEBUG LAN-only", "In debug LAN chat ONLY",
            "Bluetooth voice follows text", "normal secure messaging is enabled",
        ):
            self.assertNotIn(obsolete.lower(), self.privacy.lower())
        flags = (MAIN / "model/ReleaseCapabilities.kt").read_text()
        for feature in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {feature} = false", flags)


if __name__ == "__main__":
    unittest.main()
