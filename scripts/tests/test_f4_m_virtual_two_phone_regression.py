"""F4-M: virtual two-handset radio regression must use real packet framing.

The JVM lab runs actual in-memory packet framing, floor state and PCM
sequence validation. These source guards keep it wired to production-path
code and prevent accidental claims of physical TLS/Android testing.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"
LAB = ROOT / "app/src/test/java/com/edenpractical/comms/transport/DebugLanVirtualTwoPhoneRegressionTest.kt"


class F4MVirtualTwoPhoneCoverage(unittest.TestCase):
    def test_real_wire_codec_and_protocol_state_are_exercised(self):
        lab = LAB.read_text()
        required = (
            "TestPacketCodec.write(output, packet)",
            "TestPacketCodec.read(ByteArrayInputStream(bytes))",
            "DebugLanVoiceFloor(isHost)",
            "DebugLanVoiceAssembler()",
            "DebugLanLiveVoiceQueue()",
            "DebugLanFloorControlQueue(",
            "DebugTapVoiceSendGate()",
            "DebugSessionAudioOwner()",
            "recipient.incomingFloor(received.floor!!, atMs)",
            "recipient.incomingVoice(incoming)",
            "DebugLanVoiceWireCodec.start(",
            "DebugLanVoiceWireCodec.data(",
            "DebugLanVoiceWireCodec.end(",
            "assertThrows(TestPacketException::class.java)",
            "abrupt Wi-Fi loss",
            "controlFinished.await(",
        )
        for marker in required:
            with self.subTest(marker=marker):
                self.assertIn(marker, lab)
        for forbidden in ("SSLSocket(", "AudioRecord(", "AudioTrack(",
                          "WifiManager(", "BluetoothAdapter(", "Thread.sleep("):
            self.assertNotIn(forbidden, lab)

    def test_critical_live_session_wiring_is_still_present(self):
        actual = (MAIN / "transport/LanDemoSession.kt").read_text()
        for marker in (
            "TestPacketCodec.read(socket.inputStream)",
            "TestPacketCodec.debugVoiceFloor(frame)",
            "queueFloorControl(request)",
            "sendPacket(TestPacketCodec.debugVoice(frame))",
            "voiceFloor?.close()",
            "liveVoice?.cancel()",
            "incomingVoice.clear()",
            "stopLocalAudioOnClosed()",
            "floorWriter.shutdownNow()",
            "PinnedTlsPeer.requirePinnedTls13",
            "approvalGate.maySendPreset()",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, actual)

    def test_preview_release_safety_is_unchanged(self):
        flags = (MAIN / "model/ReleaseCapabilities.kt").read_text()
        for name in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {name} = false", flags)
        self.assertNotIn("android.permission.RECORD_AUDIO",
                         (ROOT / "app/src/main/AndroidManifest.xml").read_text())


if __name__ == "__main__":
    unittest.main()
