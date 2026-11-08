"""F4: only the actually approved DEBUG LAN TLS session carries voice bursts."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"
DEBUG = ROOT / "app/src/debug"


class ActualLanVoiceBurstBoundary(unittest.TestCase):
    def test_approved_tls_live_session_is_the_only_packet_sender_receiver(self):
        lan = (MAIN / "transport/LanDemoSession.kt").read_text()
        codec = (MAIN / "protocol/TestPacketCodec.kt").read_text()
        frame = (MAIN / "protocol/DebugLanVoiceWireCodec.kt").read_text()
        self.assertIn("ApplicationInfo.FLAG_DEBUGGABLE", lan)
        self.assertIn("socket.startHandshake()", lan)
        self.assertIn("PinnedTlsPeer.requirePinnedTls13(socket.session, expectedPin)", lan)
        self.assertIn("SessionProofExchange.runHost(", lan)
        self.assertIn("SessionProofExchange.runJoin(", lan)
        self.assertIn("DebugTlsChannelAgreement.verify(", lan)
        self.assertIn("if (!approvalGate.maySendPreset()) return VoiceSendResult.AWAITING_APPROVAL", lan)
        self.assertIn("if (!approvalGate.maySendPreset()) throw TestPacketException()", lan)
        self.assertIn("val tls = sockets.currentSocket() as? SSLSocket", lan)
        self.assertIn("TestPacketCodec.write(tls.outputStream, packet)", lan)
        self.assertIn("sendPacket(TestPacketCodec.debugVoice(frame))", lan)
        self.assertIn("incomingVoice.accept(voiceFrame)", lan)
        self.assertIn("completed?.fill(0)", lan)  # wipe assembled PCM with or without live sink
        self.assertIn("voicePlayback = null", lan)
        self.assertIn("incomingVoice.clear()", lan)
        self.assertIn("close()", lan)
        self.assertIn("VOICE_RECEIVED", lan)
        self.assertIn("VOICE_WRITTEN", lan)
        self.assertIn("DEBUG_VOICE_START, DEBUG_VOICE_PCM, DEBUG_VOICE_END", codec)
        self.assertIn('private const val MAGIC = 0x45435650', frame)
        self.assertIn("const val MAX_FRAMES = 150", frame)
        self.assertIn("const val PCM_BYTES = 640", frame)
        self.assertIn("frame.turn <= completedTurn", frame)
        self.assertIn("frame.sequence != next", frame)
        for forbidden in ("SharedPreferences", "openFileOutput(", "AudioRecord",
                          "AudioTrack", "BluetoothSocket", "WifiP2pManager"):
            self.assertNotIn(forbidden, frame)

    def test_recording_is_local_short_lived_explicit_and_foreground(self):
        ui = (MAIN / "pairing/LocalPairingTestScreen.kt").read_text()
        audio = (MAIN / "pairing/DebugLanVoiceAudio.kt").read_text()
        self.assertIn('"demo" -> {', ui)
        self.assertIn('"START TALKING"', ui)
        self.assertIn('"SEND VOICE BURST"', ui)
        self.assertIn("micPermission.launch(Manifest.permission.RECORD_AUDIO)", ui)
        self.assertIn("connection.setVoicePlayback { pcm ->", ui)
        self.assertIn("voiceAudio.playReceived(pcm)", ui)
        self.assertIn("live.sendVoiceBurst(clip)", ui)
        self.assertIn("voiceAudio.cancelAll()", ui)
        self.assertIn("Lifecycle.Event.ON_STOP", ui)
        self.assertIn("voiceAudio.finishCapture()", ui)
        self.assertIn("PackageManager.PERMISSION_GRANTED", ui)
        self.assertIn("context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE", audio)
        self.assertIn("private const val MAX_MS = 3_000L", audio)
        self.assertIn("DebugLanVoiceWireCodec.MAX_PCM_BYTES", audio)
        self.assertIn("MediaRecorder.AudioSource.MIC", audio)
        self.assertIn("AudioTrack.MODE_STATIC", audio)
        self.assertIn("recording.fill(0)", audio)
        self.assertIn("copy.fill(0)", audio)
        for forbidden in ("Socket(", "SSLSocket", "WifiP2p",
                          "startService(", "openFileOutput(", "SharedPreferences",
                          "MediaRecorder.OutputFormat"):
            self.assertNotIn(forbidden, audio)
        debug_manifest = (DEBUG / "AndroidManifest.xml").read_text()
        main_manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text()
        self.assertIn('android.permission.RECORD_AUDIO', debug_manifest)
        self.assertNotIn('android.permission.RECORD_AUDIO', main_manifest)
        capabilities = (MAIN / "model/ReleaseCapabilities.kt").read_text()
        for flag in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {flag} = false", capabilities)

    def test_future_unaudited_voice_lab_is_still_unwired(self):
        future = (DEBUG / "java/com/edenpractical/comms/audio/DebugFutureLanVoiceFrameCodec.kt").read_text()
        self.assertIn("IT DOES NOT provide encryption", future)
        for path in (
            MAIN / "protocol/TestPacketCodec.kt",
            MAIN / "transport/LanDemoSession.kt",
            MAIN / "pairing/LocalPairingTestScreen.kt",
            MAIN / "pairing/DebugLanVoiceAudio.kt",
        ):
            self.assertNotIn("DebugFutureLanVoiceFrameCodec", path.read_text())


if __name__ == "__main__":
    unittest.main()
