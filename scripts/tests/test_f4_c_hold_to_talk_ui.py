"""F4-C/F4-D: real hold/release debug LAN voice, now live during hold.

Source guards complement Gradle's Android compilation. No physical speaker
or touch-device acceptance is claimed by these checks.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"


class HoldToTalkSourceContract(unittest.TestCase):
    def test_press_release_cancel_and_accessibility_have_real_callbacks(self):
        control = (MAIN / "pairing/DebugHoldToTalkControl.kt").read_text()
        for required in (
            "detectTapGestures(", "onPress = {", "currentStart()",
            "tryAwaitRelease()", "if (released) currentRelease() else currentCancel()",
            "if (!finished) currentCancel()", ".pointerInput(enabled)",
            "rememberUpdatedState(onPressStart)", "rememberUpdatedState(onRelease)",
            "rememberUpdatedState(onCancel)", "onAccessibilityClick",
            "currentAccessibility()", "role = Role.Button",
            "Hold to transmit live test voice", "HOLD TO TALK",
            "RELEASE TO STOP"
        ):
            self.assertIn(required, control, required)
        # This control must not open a second transport, microphone or TLS
        # bypass. The actual audio/approval work lives in the parent.
        for forbidden in (
            "AudioRecord", "AudioTrack", "SSLSocket", "Socket(",
            "BluetoothAdapter", "WifiP2pManager", "sendVoiceBurst(",
            "requestPermission(", "SharedPreferences"
        ):
            self.assertNotIn(forbidden, control)

    def test_approved_lan_voice_session_is_reused_with_tap_fallback(self):
        screen = (MAIN / "pairing/LocalPairingTestScreen.kt").read_text()
        for required in (
            "DebugHoldToTalkControl(", "onPressStart = { startLiveVoiceCapture() }",
            "onRelease = { finishLiveVoiceCapture() }",
            "onCancel = { cancelLiveVoiceCapture() }",
            "onAccessibilityClick = { tapVoiceCapture() }",
            "USE TAP CONTROLS INSTEAD", "HIDE TAP CONTROLS",
            'pendingFloorMode == "tap" -> "CANCEL TALK REQUEST"',
            "enabled = !voiceFinalizing && !privacyHidden && !peerTalking",
            'if (stage != "demo" || privacyHidden || talking || voiceFinalizing ||',
            "micPermission.launch(Manifest.permission.RECORD_AUDIO)",
            "voiceAudio.startCapture(", "live.sendVoiceBurst(clip)",
            "voiceAudio.startLiveCapture(", "session.startLiveVoice()",
            "onFrame = { pcm -> handle.offerPcm(pcm) }",
            "voiceAudio.finishCapture()", "voiceAudio.cancelAll()",
            "DebugSessionEventAdmission.current(", "No usable microphone audio",
            "transmit short audio frames over"
        ):
            self.assertIn(required, screen, required)
        self.assertIn("showTapVoiceControls by remember { mutableStateOf(false) }", screen)
        self.assertIn("generation += 1", (MAIN / "pairing/DebugLanVoiceAudio.kt").read_text())

    def test_no_new_voice_transport_release_permissions_or_security_claims(self):
        screen = (MAIN / "pairing/LocalPairingTestScreen.kt").read_text()
        lan = (MAIN / "transport/LanDemoSession.kt").read_text()
        release = (MAIN / "model/ReleaseCapabilities.kt").read_text()
        control = (MAIN / "pairing/DebugHoldToTalkControl.kt").read_text()
        self.assertIn("approvalGate.maySendPreset()", lan)
        self.assertIn("TestPacketCodec.debugVoice(frame)", lan)
        self.assertIn("socket.startHandshake()", lan)
        self.assertIn("PinnedTlsPeer.requirePinnedTls13", lan)
        self.assertIn("Three seconds maximum", screen)
        self.assertIn("Already transmitted live audio cannot be retracted", control)
        for flag in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {flag} = false", release)
        manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text()
        self.assertNotIn("android.permission.RECORD_AUDIO", manifest)
        debug_manifest = (ROOT / "app/src/debug/AndroidManifest.xml").read_text()
        self.assertIn("android.permission.RECORD_AUDIO", debug_manifest)


if __name__ == "__main__":
    unittest.main()
