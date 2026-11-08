"""F4-D2 source boundary: local 20 ms PCM frames may not become a transport."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
AUDIO = ROOT / "app/src/debug/java/com/edenpractical/comms/audio"
RELEASE = ROOT / "app/src/main/java/com/edenpractical/comms"


class F4D2LocalAudioFrames(unittest.TestCase):
    def test_real_local_playback_uses_bounded_frames_not_a_wire(self):
        frames = (AUDIO / "DebugLocalPttFrames.kt").read_text()
        activity = (AUDIO / "DebugLocalPttActivity.kt").read_text()
        self.assertIn("const val FRAME_MILLIS = 20", frames)
        self.assertIn("DebugLocalPttPolicy.SAMPLE_RATE_HZ *", frames)
        self.assertIn("DebugLocalPttPolicy.MAX_PCM_BYTES / FRAME_BYTES", frames)
        self.assertIn("sequence != nextSequence", frames)
        self.assertIn("nextSequence >= MAX_FRAMES", frames)
        self.assertIn("pcm.copyOf()", frames)
        self.assertIn("pending.removeFirst().pcm.fill(0)", frames)
        self.assertIn("DebugLocalPttFrames.fromClip(original)", activity)
        self.assertIn("AudioTrack.MODE_STREAM", activity)
        self.assertIn("AudioTrack.WRITE_BLOCKING", activity)
        self.assertIn("packets.close()", activity)
        self.assertIn("pcm.fill(0)", activity)
        self.assertIn("currentGeneration.get()", activity)
        self.assertIn("terminateAndDiscard()", activity)
        for forbidden in (
            "Socket(", "java.net.", "javax.net.", "BluetoothSocket",
            "WifiP2p", "LanDemoSession", "SharedPreferences",
            "openFileOutput(", "startService("
        ):
            self.assertNotIn(forbidden, frames)
            self.assertNotIn(forbidden, activity)

    def test_release_flags_permissions_and_source_are_unmodified(self):
        self.assertFalse((RELEASE / "audio/DebugLocalPttFrames.kt").exists())
        self.assertFalse((RELEASE / "audio/DebugLocalPttActivity.kt").exists())
        main_manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text()
        self.assertNotIn("android.permission.RECORD_AUDIO", main_manifest)
        flags = (RELEASE / "model/ReleaseCapabilities.kt").read_text()
        for name in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {name} = false", flags)


if __name__ == "__main__":
    unittest.main()
