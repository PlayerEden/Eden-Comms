"""M6-F source guards for nearby debug keyboard, capture and link reset.

These checks do not execute an Android IME, test 200% font rendering,
perform two-device radio interop or certify screenshot privacy on an OEM.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
DEBUG = ROOT / "app/src/debug/java/com/edenpractical/comms"
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"


class M6FKeyboardCaptureRecovery(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.bt = (DEBUG / "bluetooth/DebugBluetoothDiscoveryActivity.kt").read_text()
        cls.host = (DEBUG / "wifidirect/DebugWifiDirectGroupHostActivity.kt").read_text()
        cls.join = (DEBUG / "wifidirect/DebugWifiDirectDiscoveryActivity.kt").read_text()
        cls.shell = (DEBUG / "ui/DebugVerifiedConversationPanel.kt").read_text()
        cls.bt_panel = (DEBUG / "bluetooth/DebugBluetoothTextPanel.kt").read_text()
        cls.p2p_panel = (DEBUG / "wifidirect/DebugWifiDirectTextPanel.kt").read_text()
        cls.debug_manifest = (ROOT / "app/src/debug/AndroidManifest.xml").read_text()
        cls.main_manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text()
        cls.release_flags = (MAIN / "model/ReleaseCapabilities.kt").read_text()
        cls.matrix = (ROOT / "docs/TWO_PHONE_ACCEPTANCE_MATRIX.md").read_text()

    def test_all_nearby_pages_resize_around_ime_and_system_bars(self):
        for activity in (self.bt, self.host, self.join):
            for required in (
                "Modifier.fillMaxSize()", ".statusBarsPadding()",
                ".navigationBarsPadding()", ".imePadding()",
                ".verticalScroll(rememberScrollState())",
                ".padding(20.dp)",
                "DebugVerifiedConversationPanel(",
                "override fun onStop()",
            ):
                self.assertIn(required, activity, required)
            self.assertLess(activity.index(".imePadding()"),
                            activity.index(".verticalScroll(rememberScrollState())"))
            self.assertLess(activity.index(".verticalScroll(rememberScrollState())"),
                            activity.index(".padding(20.dp)"))
        for name in (
            ".bluetooth.DebugBluetoothDiscoveryActivity",
            ".wifidirect.DebugWifiDirectDiscoveryActivity",
            ".wifidirect.DebugWifiDirectGroupHostActivity"
        ):
            block = self.debug_manifest.split(f'android:name="{name}"', 1)[1]
            block = block.split("/>", 1)[0]
            self.assertIn('android:exported="false"', block)
            self.assertIn('android:windowSoftInputMode="adjustResize"', block)
        self.assertEqual(3, self.debug_manifest.count(
            'android:windowSoftInputMode="adjustResize"'))
        self.assertNotIn('android:windowSoftInputMode="adjustResize"',
                         self.main_manifest)

    def test_bluetooth_qr_and_verified_text_are_capture_protected(self):
        self.assertIn("import android.view.WindowManager", self.bt)
        self.assertIn("window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)", self.bt)
        self.assertLess(
            self.bt.index("window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)"),
            self.bt.index("qrCoordinator.loadExistingIdentity()")
        )
        for screen in (self.host, self.join):
            self.assertIn("window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)", screen)
        self.assertIn('android:exported="false"', self.debug_manifest)

    def test_leaving_text_focus_does_not_leave_ime_above_mic_controls(self):
        for phrase in (
            "LocalFocusManager.current",
            "LocalSoftwareKeyboardController.current",
            "val chooseTalk = {",
            "val disconnect = {",
            "focusManager.clearFocus(force = true)",
            "softwareKeyboard?.hide()",
            "onClick = chooseTalk",
            "onClick = disconnect",
            "onDisconnect()",
            "val chooseText = {",
            'if (selection == "talk") onLeaveTalk()',
            "DisposableEffect(selection)",
            'if (selection == "talk") onLeaveTalk()',
            "onLeaveTalk: () -> Unit",
        ):
            self.assertIn(phrase, self.shell, phrase)
        self.assertEqual(2, self.shell.count("onClick = chooseTalk"))
        self.assertEqual(2, self.shell.count("onClick = chooseText"))
        self.assertEqual(2, self.shell.count("focusManager.clearFocus(force = true)"))
        self.assertEqual(2, self.shell.count("softwareKeyboard?.hide()"))
        self.assertNotIn('onClick = { selection = "talk" }', self.shell)
        self.assertNotIn("onClick = onDisconnect", self.shell)
        for screen in (self.bt, self.host, self.join):
            self.assertIn("onLeaveTalk = { localVoice.release() }", screen)
            self.assertIn("localVoice.disable()", screen)

    def test_stopped_bluetooth_session_cannot_show_old_queue_feedback(self):
        stopped = self.bt.split(
            "if (phase == DebugRfcommAttemptGate.Phase.STOPPED) {", 1)[1].split(
            "onApprovalReady =", 1)[0]
        for phrase in (
            'chatReady = false', 'chatDraft = ""',
            'chatSendFeedback = ""', "chatLines = emptyList()",
            "ackedChatIds = emptySet()", "localVoice.disable()",
            "qrCoordinator.clear()", "clearCandidateMemory()",
        ):
            self.assertIn(phrase, stopped, phrase)
        ready = self.bt.split("is DebugBluetoothApprovedTextChannel.Event.Ready -> {", 1)[1]
        self.assertIn('chatSendFeedback = ""', ready.split(
            "is DebugBluetoothApprovedTextChannel.Event.Written ->", 1)[0])
        for screen in (self.host, self.join):
            self.assertIn("verifiedChat.clear()", screen)
        for panel in (self.bt_panel, self.p2p_panel):
            self.assertIn("DebugNearbyTextUiCopy.canSubmit(draft)", panel)
            self.assertIn("A peer-app ACK is not a read receipt.", panel)

    def test_no_debug_ux_change_authorizes_radio_or_production_release(self):
        for name in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {name} = false", self.release_flags)
        for forbidden in (
            "BluetoothSocket", "WifiP2pManager", "SSLEngine",
            "onApprovedChannel", "ProductionSessionAuthorization",
            "SharedPreferences", "openFileOutput(", "startActivity(",
        ):
            self.assertNotIn(forbidden, self.shell)
        self.assertIn("State: NOT RUN", self.matrix)
        self.assertFalse(
            (ROOT / "app/src/release/java/com/edenpractical/comms/ui/DebugVerifiedConversationPanel.kt").exists())


if __name__ == "__main__":
    unittest.main()
