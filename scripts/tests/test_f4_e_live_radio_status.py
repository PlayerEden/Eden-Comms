"""F4-E: observed peer audio state is not a guessed or granted radio floor.

Source contract guard. Actual Android audio/radio interoperability remains
physical QA; this does not assert low latency or exclusive floor ownership.
"""
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
LAN = ROOT / "app/src/main/java/com/edenpractical/comms/transport/LanDemoSession.kt"
UI = ROOT / "app/src/main/java/com/edenpractical/comms/pairing/LocalPairingTestScreen.kt"


class LiveRadioStatusContract(unittest.TestCase):
    def test_remote_indicators_only_follow_validated_inbound_frames(self):
        lan = LAN.read_text()
        observed = lan.index("val completed = incomingVoice.accept(voiceFrame)")
        start = lan.index("Phase.VOICE_STARTED,")
        finish = lan.index("Phase.VOICE_FINISHED,")
        self.assertLess(observed, start)
        self.assertLess(start, finish)
        self.assertIn("if (!approvalGate.maySendPreset()) throw TestPacketException()", lan)
        self.assertIn("peerVoiceActive.set(true)", lan)
        self.assertIn("peerVoiceActive.set(false)", lan)
        self.assertIn("if (voiceFrame.kind == DebugLanVoiceWireCodec.Kind.START)", lan)
        self.assertIn("if (voiceFrame.kind == DebugLanVoiceWireCodec.Kind.END)", lan)

    def test_remote_activity_visibly_blocks_new_hold_but_not_existing_tap_stop(self):
        ui = UI.read_text()
        for required in (
            "var peerTalking by remember { mutableStateOf(false) }",
            "LanDemoSession.Phase.VOICE_STARTED ->",
            "LanDemoSession.Phase.VOICE_FINISHED ->",
            "if (stage == \"demo\") peerTalking = true",
            "if (stage == \"demo\") peerTalking = false",
            "RECEIVING • OTHER PHONE IS TALKING",
            "TRANSMITTING • LIVE TEST AUDIO",
            "enabled = !voiceFinalizing && !privacyHidden && !peerTalking",
            '(!peerTalking || talking || pendingFloorMode == "tap")',
            "voiceAudio.cancelAll()",
            "peerTalking = false",
            "Audio starts only after an authenticated talk grant",
            "If both users press at once, the host arbitrates",
        ):
            with self.subTest(required=required):
                self.assertIn(required, ui)
        self.assertGreaterEqual(
            ui.count("peerTalking || pendingFloorMode != null) return false"), 2,
            "Live and tap starts must both reject an observed incoming turn"
        )

    def test_no_new_transport_or_production_radio_claim(self):
        main = ROOT / "app/src/main/java/com/edenpractical/comms"
        caps = list(main.rglob("ReleaseCapabilities.kt"))
        self.assertTrue(caps, "Must preserve explicit production-off boundary")
        text = caps[0].read_text()
        for flag in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertRegex(text, rf"{flag}\s*=\s*false")


if __name__ == "__main__":
    unittest.main()
