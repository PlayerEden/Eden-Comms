"""F4-F: host-granted floor is required for BOTH debug LAN voice modes.

Structural guards complement strict Kotlin/JUnit round trips, host/join
simulations, Android compilation and explicit two-phone acceptance.
"""
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"


class LiveFloorWiringContract(unittest.TestCase):
    def test_floor_frames_only_on_same_authenticated_tls_writer(self):
        lan = (MAIN / "transport/LanDemoSession.kt").read_text()
        packet = (MAIN / "protocol/TestPacketCodec.kt").read_text()
        floor = (MAIN / "transport/DebugLanVoiceFloor.kt").read_text()
        for marker in (
            "voiceFloor = DebugLanVoiceFloor(isHost = isHost)",
            "fun requestVoiceFloor(): FloorRequestResult",
            "fun cancelUnstartedVoiceFloor()",
            "fun releaseWrittenVoiceFloor()",
            "voiceFloor?.maySendAudio() != true",
            "queueFloorControl(request)",
            "queueFloorControl(response)",
            "TestPacketCodec.debugVoiceFloor(frame)",
            "sendPacket(TestPacketCodec.debugVoiceFloor(",
            "floorControls = DebugLanFloorControlQueue(",
            ".incoming(frame, SystemClock.elapsedRealtime())",
            "remoteAudioStart()",
            "remoteAudioEnd()",
            "releaseWrittenVoiceFloor()",
            "VOICE_FLOOR_GRANTED",
            "VOICE_FLOOR_DENIED",
            "voiceFloor?.close()",
            "voiceFloor?.timedOut(SystemClock.elapsedRealtime())",
            "PinnedTlsPeer.requirePinnedTls13",
            "approvalGate.maySendPreset()",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, lan)
        self.assertIn("DEBUG_VOICE_FLOOR", packet)
        self.assertIn("DebugLanVoiceFloorCodec.decode(bytes)", packet)
        self.assertIn("DebugLanVoiceFloorCodec.encode(packet.floor)", packet)
        self.assertIn("enum class Ask { GRANTED, PENDING, BUSY }", floor)

    def test_mic_does_not_start_before_floor_and_tap_cannot_bypass(self):
        ui = (MAIN / "pairing/LocalPairingTestScreen.kt").read_text()
        for marker in (
            'pendingFloorMode = "hold"',
            'pendingFloorMode = "tap"',
            "session.requestVoiceFloor()",
            "LanDemoSession.Phase.VOICE_FLOOR_GRANTED ->",
            "LanDemoSession.Phase.VOICE_FLOOR_DENIED ->",
            "beginLiveCaptureWithFloor(active)",
            "beginTapCaptureWithFloor(active)",
            "probe?.cancelUnstartedVoiceFloor()",
            "WAITING FOR RADIO CHANNEL • MIC OFF",
            "voiceAudio.startLiveCapture(",
            "voiceAudio.startCapture(",
            "live.sendVoiceBurst(clip)",
            "CANCEL TALK REQUEST",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, ui)
        self.assertLess(
            ui.index("fun beginLiveCaptureWithFloor("),
            ui.index("fun startLiveVoiceCapture(")
        )
        self.assertLess(
            ui.index("fun beginTapCaptureWithFloor("),
            ui.index("fun startVoiceCapture(")
        )

    def test_release_still_voice_disabled_and_no_new_permissions(self):
        release = (MAIN / "model/ReleaseCapabilities.kt").read_text()
        for marker in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {marker} = false", release)
        self.assertNotIn(
            "android.permission.RECORD_AUDIO",
            (ROOT / "app/src/main/AndroidManifest.xml").read_text()
        )
        self.assertIn(
            "android.permission.RECORD_AUDIO",
            (ROOT / "app/src/debug/AndroidManifest.xml").read_text()
        )


if __name__ == "__main__":
    unittest.main()
