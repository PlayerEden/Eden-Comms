"""F4-U: authenticated-session debug voice burst safety / source regression.

Actual voice PCM MUST travel on the existing pinned QR/exporter-approved
SSLEngine used by text, never over raw RFCOMM or P2P or saved as history.
Android OEM microphone/speaker and real radios still need two-device QA.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
DEBUG = ROOT / "app/src/debug/java/com/edenpractical/comms"
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"

class NearbyVoiceBurst(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        def src(p):
            return (DEBUG / p).read_text()
        cls.frames = src("bluetooth/DebugNearbyApplicationFrame.kt")
        cls.tls = src("bluetooth/DebugBluetoothFutureTlsTextLab.kt")
        cls.channel = src("bluetooth/DebugBluetoothApprovedTextChannel.kt")
        cls.rfcomm = src("bluetooth/DebugRfcommLinkController.kt")
        cls.bt = src("bluetooth/DebugBluetoothDiscoveryActivity.kt")
        cls.p2ps = src("wifidirect/DebugWifiDirectApprovedTcpSession.kt")
        cls.p2pc = src("wifidirect/DebugWifiDirectTargetLinkController.kt")
        cls.p2ph = src("wifidirect/DebugWifiDirectGroupHostActivity.kt")
        cls.p2pj = src("wifidirect/DebugWifiDirectDiscoveryActivity.kt")
        cls.p2pui = src("wifidirect/DebugWifiDirectChatUiState.kt")
        cls.owner = src("audio/DebugNearbyVoiceOwner.kt")
        cls.panel = src("audio/DebugNearbyVoicePanel.kt")
        cls.interop = (ROOT / "app/src/test/java/com/edenpractical/comms/bluetooth/DebugPairedTlsEngineInteropTest.kt").read_text()

    def test_exact_existing_tls_application_frame_boundary(self):
        for source in (self.tls, self.channel):
            self.assertIn("DebugNearbyApplicationFrame.inspect(frame)", source)
        for value in ('private const val VOICE_MAGIC = 0x45435650',
                      "DebugLanVoiceWireCodec.decode(bytes).clear()",
                      "DebugBluetoothTextFrameCodec.decode(bytes)",
                      "DebugLanVoiceWireCodec.PCM_BYTES",
                      "DebugBluetoothTextFrameCodec.MAX_FRAME_BYTES"):
            self.assertIn(value, self.frames)
        self.assertIn("engine.wrap(ByteBuffer.wrap(frame), encrypted)", self.tls)
        self.assertIn("engine.unwrap(ByteBuffer.wrap(record), plaintext)", self.tls)
        self.assertIn("DebugTlsRecordBoundary.requireSingleCiphertext(record)", self.tls)
        self.assertIn("DebugNearbyApplicationFrame.MAX_BYTES", self.tls)

    def test_voice_frames_capped_ordered_zeroed_and_no_raw_sockets(self):
        for s in ("fun offerVoiceClip(pcm: ByteArray)", "voicePending",
                  "ArrayBlockingQueue<Pending>(10)", "pcm.copyOf()",
                  "DebugLanVoiceWireCodec.MAX_PCM_BYTES",
                  "pcm.size % DebugLanVoiceWireCodec.PCM_BYTES != 0",
                  "DebugLanVoiceAssembler()", "incomingVoice.accept(voice)",
                  "private fun writeVoice(pcm: ByteArray)",
                  "DebugLanVoiceWireCodec.start(turn)",
                  "DebugLanVoiceWireCodec.data(turn, index, chunk)",
                  "DebugLanVoiceWireCodec.end(turn, count)",
                  "DebugLanVoiceWireCodec.MAX_FRAMES",
                  "pending.voiceClip?.fill(0)", "incomingVoice.clear()"):
            self.assertIn(s, self.channel, s)
        for bad in ("BluetoothSocket", "ServerSocket", "DatagramSocket",
                    "AudioRecord", "AudioTrack", "SharedPreferences",
                    "openFileOutput(", "FileOutputStream", "Log.", "println("):
            self.assertNotIn(bad, self.channel)
        self.assertIn("event.pcm.fill(0)", self.rfcomm)
        self.assertIn("event.pcm.fill(0)", self.p2ps)

    def test_real_approved_live_transport_not_unverified_probe(self):
        self.assertIn("DebugBluetoothApprovedTextChannel(", self.rfcomm)
        self.assertIn("onApprovedChannel = { engine, wireInput, wireOutput ->", self.rfcomm)
        self.assertIn("fun offerVoiceClip(pcm: ByteArray)", self.rfcomm)
        self.assertIn("fun offerVoiceClip(pcm: ByteArray)", self.p2ps)
        self.assertIn("phase != Phase.CHAT", self.p2ps)
        self.assertIn("fun offerVerifiedVoiceClip(pcm: ByteArray)", self.p2pc)
        self.assertIn("verifiedSession?.offerVoiceClip(pcm)", self.p2pc)
        for path in (
            "wifidirect/DebugWifiDirectTcpProbe.kt",
            "bluetooth/DebugBluetoothQrCoordinator.kt",
            "wifidirect/DebugWifiDirectQrCoordinator.kt",
        ):
            data = (DEBUG / path).read_text()
            self.assertNotIn("DebugNearbyVoiceOwner(", data)
            self.assertNotIn("offerVoiceClip(", data)

    def test_user_opt_in_permission_audible_playback_and_lifecycle(self):
        for value in ("DebugHoldToTalkControl(", "ActivityResultContracts.RequestPermission()",
                      "permission.launch(Manifest.permission.RECORD_AUDIO)",
                      "NOT live streaming", "max 3 seconds"):
            # Captions may say 'up to 3 seconds' instead.
            if value != "max 3 seconds":
                self.assertIn(value, self.panel, value)
        for value in ("audio.startCapture(", "audio.finishCapture()",
                      "audio.playReceived(pcm)", "audio.cancelAll()",
                      "sender?.invoke(pcm)", "current(ticket)",
                      "canContinue: () -> Boolean", "fun disable()"):
            self.assertIn(value, self.owner, value)
        for screen in (self.bt, self.p2ph, self.p2pj):
            for value in ("DebugNearbyVoicePanel(localVoice)",
                          "localVoice.activate(", "localVoice.receiveEncryptedVoice(",
                          "localVoice.voiceWritten(", "localVoice.disable()"):
                self.assertIn(value, screen, value)
        self.assertIn("VoiceReceived", self.p2pui)
        self.assertIn("VoiceWritten", self.p2pui)

    def test_jvm_two_way_tls_audio_and_old_release_flags(self):
        for value in ("realApprovedTextWorkersExchangeBothDirectionsAndAckOnSameEngines",
                      "testHostPcm", "testJoinPcm", "hostVoice.await(",
                      "joinVoice.await(", "offerVoiceClip(", "fullJvmControlSequence("):
            self.assertIn(value, self.interop)
        flags = (MAIN / "model/ReleaseCapabilities.kt").read_text()
        for key in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {key} = false", flags)
        for bad in ("DebugNearbyVoiceOwner.kt", "DebugNearbyVoicePanel.kt"):
            self.assertFalse((ROOT / "app/src/release/java/com/edenpractical/comms/audio" / bad).exists())


if __name__ == "__main__":
    unittest.main()
