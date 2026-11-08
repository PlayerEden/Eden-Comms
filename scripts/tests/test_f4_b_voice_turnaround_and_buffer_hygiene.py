"""F4-B: audio tap/playback status and best-effort packet cleanup.

Run on the real current Android sources, not on an isolated future voice lab.
These tests are not proof of actual speaker audibility, privacy erasure or
hardware compatibility.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"


class F4BVoiceTurnaround(unittest.TestCase):
    def test_quick_tap_has_terminal_no_audio_callback_not_stuck_finishing(self):
        audio = (MAIN / "pairing/DebugLanVoiceAudio.kt").read_text()
        ui = (MAIN / "pairing/LocalPairingTestScreen.kt").read_text()
        self.assertIn("onNoAudio: () -> Unit = {}", audio)
        self.assertIn("if (token == generation)", audio)
        self.assertIn("used >= DebugLanVoiceWireCodec.PCM_BYTES", audio)
        self.assertIn("onNoAudio()", audio)
        self.assertIn("var voiceFinalizing by remember", ui)
        self.assertIn("voiceFinalizing = true", ui)
        self.assertIn("voiceFinalizing = false", ui)
        self.assertIn("enabled = !voiceFinalizing", ui)
        self.assertIn("voiceAudio.finishCapture()", ui)
        self.assertIn("onNoAudio = {", ui)
        self.assertIn("voiceAttempt, attemptVersion", ui)
        self.assertIn('stage == "demo"', ui)
        self.assertIn("No usable microphone audio.", ui)

    def test_received_packet_is_not_automatically_reported_as_played(self):
        audio = (MAIN / "pairing/DebugLanVoiceAudio.kt").read_text()
        ui = (MAIN / "pairing/LocalPairingTestScreen.kt").read_text()
        self.assertIn("onPlaybackStarted: (Boolean) -> Unit = {}", audio)
        self.assertIn("speaker.play()", audio)
        self.assertLess(audio.index("speaker.play()"),
                        audio.index("onPlaybackStarted(true)"))
        self.assertIn("onPlaybackStarted(false)", audio)
        self.assertIn("voiceAudio.playReceived(pcm) { started ->", ui)
        self.assertIn("sound not verified", ui)
        self.assertIn("could not start speaker playback", ui)
        self.assertIn("VOICE_RECEIVED -> {", ui)
        receive = ui.split("LanDemoSession.Phase.VOICE_RECEIVED -> {", 1)[1].split(
            "LanDemoSession.Phase.VOICE_WRITTEN", 1)[0]
        self.assertNotIn('voiceNote = "Received', receive)

    def test_hide_audio_and_session_end_reject_stale_microphone_results(self):
        ui = (MAIN / "pairing/LocalPairingTestScreen.kt").read_text()
        audio = (MAIN / "pairing/DebugLanVoiceAudio.kt").read_text()
        self.assertIn("privacyHidden = !privacyHidden", ui)
        self.assertIn("if (privacyHidden) {", ui)
        self.assertIn("voiceAudio.cancelAll()", ui)
        self.assertIn("voiceFinalizing = false", ui)
        self.assertIn("Conversation hidden: received voice was not played.", ui)
        self.assertIn("generation += 1", audio)
        self.assertIn("token != generation", audio)
        self.assertIn("recording.fill(0)", audio)
        self.assertIn("copy.fill(0)", audio)
        self.assertIn("Lifecycle.Event.ON_STOP", ui)

    def test_temp_voice_and_text_tls_frames_are_erased_on_success_and_failure(self):
        packet = (MAIN / "protocol/TestPacketCodec.kt").read_text()
        self.assertIn("encode(packet).fill(0)", packet)
        self.assertIn("val payload = encode(packet)", packet)
        self.assertIn("val framed = FrameCodec.encode(payload)", packet)
        self.assertIn("out.write(framed)", packet)
        self.assertIn("framed.fill(0)", packet)
        self.assertIn("payload.fill(0)", packet)
        self.assertIn("val payload = FrameCodec.readFrom(input)", packet)
        self.assertIn("try { return decode(payload) }", packet)
        self.assertIn("finally { payload.fill(0) }", packet)
        self.assertNotIn("out.write(FrameCodec.encode(encode(packet)))", packet)
        flags = (MAIN / "model/ReleaseCapabilities.kt").read_text()
        for name in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {name} = false", flags)


if __name__ == "__main__":
    unittest.main()
