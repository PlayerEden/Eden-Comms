"""F4-O: the last 20-ms live PCM sample cannot sneak past a stop action.

JVM concurrency tests validate the real pure gates; structural checks verify
Android AudioRecord/AudioTrack actually use them on the foreground session.
No new transport, production capability, background service or permission.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"
AUDIO = MAIN / "pairing/DebugLanVoiceAudio.kt"
GATES = MAIN / "pairing/DebugLiveVoiceStopBoundaries.kt"


class F4OAudioStopBoundaries(unittest.TestCase):
    def test_microphone_worker_uses_same_gate_as_release_and_teardown(self):
        audio = AUDIO.read_text()
        for marker in (
            "private var liveMicFrameGate: DebugLiveMicFrameGate? = null",
            "val gate = DebugLiveMicFrameGate()",
            "liveMicFrameGate = gate",
            "gate.offerIfActive(frame, onFrame)",
            "gate.stop()",
            "liveMicFrameGate?.stop()",
            "liveMicFrameGate = null",
            "if (liveMicFrameGate === gate) liveMicFrameGate = null",
            "synchronized(lock) {",
            "capturing = false",
            "generation += 1",
            "frame.fill(0)",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, audio)
        stop = audio.split("fun finishCapture() {", 1)[1].split(
            "fun playReceived(", 1
        )[0]
        self.assertLess(stop.index("liveMicFrameGate?.stop()"),
                        stop.index("recorder?.stop()"))
        cancel = audio.split("fun cancelAll() {", 1)[1].split(
            "companion object {", 1
        )[0]
        self.assertLess(cancel.index("liveMicFrameGate?.stop()"),
                        cancel.index("recorder?.stop()"))

    def test_speaker_reader_enqueue_cannot_outlive_cancel(self):
        audio = AUDIO.read_text()
        boundary = GATES.read_text()
        for marker in (
            "val frames = DebugLivePlaybackBuffer()",
            "fun offer(pcm: ByteArray): Boolean = frames.offer(pcm)",
            "fun finish() = frames.finish()",
            "fun cancel() = frames.cancel()",
            "state.frames.poll(45, TimeUnit.MILLISECONDS)",
            "if (state.cancelled || state.token != generation) break",
            "livePlayback?.cancel()",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, audio)
        for marker in (
            "class DebugLiveMicFrameGate",
            "fun offerIfActive(frame: ByteArray, handoff: (ByteArray) -> Boolean)",
            "!stopped && handoff(frame)",
            "class DebugLivePlaybackBuffer",
            "const val MAX_PENDING_FRAMES = 12",
            "if (ended || cancelled ||",
            "val copy = pcm.copyOf()",
            "if (frames.offer(copy)) return true",
            "copy.fill(0)",
            "sample.fill(0)",
            "fun cancel()",
            "@Synchronized",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, boundary)
        for forbidden in (
            "Socket(", "SSLSocket(", "BluetoothSocket", "WifiP2p",
            "SharedPreferences", "FileOutputStream(", "startService(",
            "AudioRecord(", "AudioTrack(",
        ):
            self.assertNotIn(forbidden, boundary)

    def test_release_and_approval_boundaries_remain_disabled(self):
        flags = (MAIN / "model/ReleaseCapabilities.kt").read_text()
        for name in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {name} = false", flags)
        self.assertNotIn("android.permission.RECORD_AUDIO",
                         (ROOT / "app/src/main/AndroidManifest.xml").read_text())
        screen = (MAIN / "pairing/LocalPairingTestScreen.kt").read_text()
        self.assertIn("voiceAudio.finishCapture()", screen)
        self.assertIn("voiceAudio.cancelAll()", screen)
        session = (MAIN / "transport/LanDemoSession.kt").read_text()
        self.assertIn("approvalGate.maySendPreset()", session)
        self.assertIn("PinnedTlsPeer.requirePinnedTls13(socket.session, expectedPin)", session)


if __name__ == "__main__":
    unittest.main()
