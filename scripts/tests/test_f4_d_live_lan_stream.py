"""F4-D: real in-press voice frames on approved TLS + early playout.

Structural guards supplement Android/JVM tests. They cannot prove physical
latency, no acoustic feedback, protocol security review, or radio floor.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"


class LiveLANVoiceContract(unittest.TestCase):
    def test_only_existing_approved_tls_writer_sends_bounded_frame_stream(self):
        session = (MAIN / "transport/LanDemoSession.kt").read_text()
        queue = (MAIN / "transport/DebugLanLiveVoiceQueue.kt").read_text()
        for required in (
            "fun startLiveVoice(): LiveVoiceStart",
            "if (!approvalGate.maySendPreset())",
            "peerVoiceActive.get()",
            "voiceQueued.compareAndSet(false, true)",
            "writer.execute {",
            "val pcm = queue.poll()",
            "sendVoiceFrame(DebugLanVoiceWireCodec.start(turn))",
            "sendVoiceFrame(DebugLanVoiceWireCodec.data(turn, sent, pcm))",
            "sendVoiceFrame(DebugLanVoiceWireCodec.end(turn, sent))",
            "fun offerPcm(frame: ByteArray)",
            "queue.clear()",
            "queue.overran()",
            "val burstStartMs = SystemClock.elapsedRealtime()",
            "val dueMs = burstStartMs + sequence * 20L",
            "if (waitMs > 0L) Thread.sleep(waitMs)",
            "frame.turn",
            "val completed = incomingVoice.accept(voiceFrame)",
            "if (live != null) live(voiceFrame.kind, voiceFrame.pcm)",
            "approvalGate.maySendPreset()",
            "PinnedTlsPeer.requirePinnedTls13(socket.session, expectedPin)",
            "DebugTlsChannelAgreement.verify(",
            "TestPacketCodec.write(tls.outputStream, packet)",
            "socket.startHandshake()",
            "liveVoice?.cancel()",
            "liveVoicePlayback = null",
        ):
            # frame.turn is in strict validated codec, not session.
            if required == "frame.turn":
                self.assertIn(required, (MAIN / "protocol/DebugLanVoiceWireCodec.kt").read_text())
            else:
                self.assertIn(required, session, required)
        self.assertIn("const val MAX_PENDING_FRAMES = 12", queue)
        self.assertIn("val copy = pcm.copyOf()", queue)
        self.assertIn("copy.fill(0)", queue)
        self.assertIn("frames.offer(copy)", queue)
        self.assertIn("overrun = true", queue)
        for forbidden in ("Socket(", "SSLSocket", "AudioRecord", "BluetoothAdapter",
                          "SharedPreferences", "openFileOutput", "WifiP2pManager"):
            self.assertNotIn(forbidden, queue)

    def test_actual_microphone_frames_and_early_nonblocking_speaker(self):
        audio = (MAIN / "pairing/DebugLanVoiceAudio.kt").read_text()
        screen = (MAIN / "pairing/LocalPairingTestScreen.kt").read_text()
        control = (MAIN / "pairing/DebugHoldToTalkControl.kt").read_text()
        for marker in (
            "fun startLiveCapture(", "source.read(", "onFrame(frame)",
            "DebugLanVoiceWireCodec.MAX_FRAMES", "MAX_MS",
            "fun acceptLiveFrame(", "AudioTrack.MODE_STREAM",
            "val copy = pcm.copyOf()", "state.frames.poll(45, TimeUnit.MILLISECONDS)",
            "speaker.play()", "onPlaybackStarted(true)",
            "frame.fill(0)", "state.cancel()", "livePlayback?.cancel()",
        ):
            if marker == "val copy = pcm.copyOf()":
                # PCM is copied inside LivePlayback.offer, not caller method.
                self.assertIn(marker, audio)
            else:
                self.assertIn(marker, audio, marker)
        for marker in (
            "onPressStart = { startLiveVoiceCapture() }",
            "onRelease = { finishLiveVoiceCapture() }",
            "onCancel = { cancelLiveVoiceCapture() }",
            "onAccessibilityClick = { tapVoiceCapture() }",
            "val startedSession = session.startLiveVoice()",
            "onFrame = { pcm -> handle.offerPcm(pcm) }",
            "connection.setLiveVoicePlayback { kind, pcm ->",
            "voiceAudio.acceptLiveFrame(kind, pcm)",
            "handle.finish()", "handle.cancel()",
            "liveVoiceHandle?.cancel()", "voiceAudio.cancelAll()",
            "if (privacyHidden)",
            "same approved Wi-Fi",
            "Three seconds maximum",
        ):
            if marker == "same approved Wi-Fi":
                self.assertIn("approved Wi-Fi", screen)
            else:
                self.assertIn(marker, screen, marker)
        self.assertIn("already have reached the peer", control)
        self.assertIn("USE TAP CONTROLS INSTEAD", screen)

    def test_no_production_permission_credential_or_unreviewed_security_claim(self):
        code = (MAIN / "transport/LanDemoSession.kt").read_text()
        self.assertIn("ApplicationInfo.FLAG_DEBUGGABLE", code)
        self.assertIn("SessionProofExchange.runHost(", code)
        self.assertIn("SessionProofExchange.runJoin(", code)
        self.assertIn("approvalGate.maySendPreset()", code)
        debug_manifest = (ROOT / "app/src/debug/AndroidManifest.xml").read_text()
        main_manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text()
        self.assertIn("android.permission.RECORD_AUDIO", debug_manifest)
        self.assertNotIn("android.permission.RECORD_AUDIO", main_manifest)
        release = (MAIN / "model/ReleaseCapabilities.kt").read_text()
        for flag in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {flag} = false", release)


if __name__ == "__main__":
    unittest.main()
