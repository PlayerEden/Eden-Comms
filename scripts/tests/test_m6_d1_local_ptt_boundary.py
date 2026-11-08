"""D1 actual debug microphone/speaker source, permission and release gate.

Source/JVM CI does NOT verify Android audio hardware, latency, permission
dialog presentation, or remote voice transmission.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"
DEBUG = ROOT / "app/src/debug"
AUDIO = DEBUG / "java/com/edenpractical/comms/audio"


class D1LocalPttBoundaries(unittest.TestCase):
    def test_real_audio_record_and_track_connected_to_explicit_ui(self):
        src = (AUDIO / "DebugLocalPttActivity.kt").read_text()
        screen = (MAIN / "MainActivity.kt").read_text().split(
            "private fun PushToTalkScreen()", 1
        )[1].split("private fun SettingsScreen(", 1)[0]
        for symbol in (
            "AudioRecord(", "AudioTrack(", "MediaRecorder.AudioSource.MIC",
            "AudioRecord.READ_BLOCKING", "MotionEvent.ACTION_DOWN",
            "MotionEvent.ACTION_UP", "MotionEvent.ACTION_CANCEL",
            "override fun onPause()", "terminateAndDiscard()",
            "Manifest.permission.RECORD_AUDIO", "requestPermissions(",
            "val token = currentGeneration.incrementAndGet()",
            "DebugLocalPttPolicy.acceptWorkerResult(",
            "DebugPttPcmFrames.wipe(audioClip)", "pcm.fill(0)", "buffer.fill(0)",
            "startRecording()", "stopRecording()", "playLocally()",
            "stopPlayback()", "discardClip()"
        ):
            self.assertIn(symbol, src)
        self.assertIn("if (debugBuild) {", screen)
        self.assertIn("Try local microphone and speaker", screen)
        self.assertIn("DebugLocalPttActivity", screen)
        self.assertIn("Voice is disabled until a verified connection is established.", screen)

    def test_microphone_only_on_debug_manifest_and_nonexported(self):
        release = (ROOT / "app/src/main/AndroidManifest.xml").read_text()
        debug = (DEBUG / "AndroidManifest.xml").read_text()
        self.assertNotIn("android.permission.RECORD_AUDIO", release)
        self.assertIn("android.permission.RECORD_AUDIO", debug)
        self.assertIn("android.hardware.microphone", debug)
        self.assertIn('android:name=".audio.DebugLocalPttActivity"', debug)
        self.assertIn('android:exported="false"', debug)
        self.assertFalse((MAIN / "audio/DebugLocalPttActivity.kt").exists())
        self.assertFalse((MAIN / "audio/DebugLocalPttPolicy.kt").exists())

    def test_audio_is_bounded_ephemeral_and_cannot_transmit(self):
        audio = (AUDIO / "DebugLocalPttActivity.kt").read_text()
        policy = (AUDIO / "DebugLocalPttPolicy.kt").read_text()
        self.assertIn("MAX_SECONDS = 5", policy)
        self.assertIn("SAMPLE_RATE_HZ = 16_000", policy)
        self.assertIn("MAX_PCM_BYTES = SAMPLE_RATE_HZ * MAX_SECONDS * 2", policy)
        self.assertIn("!recording && !playing", policy)
        self.assertIn("workerGeneration == currentGeneration && foreground", policy)
        self.assertIn("if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)", audio)
        self.assertIn("No recording started.", audio)
        self.assertIn("permission never auto-starts capture", audio)
        for marker in (
            "synchronized(audioGuard)", "foreground = false", "capturing.set(false)",
            "playing.set(false)", "currentGeneration.incrementAndGet()"
        ):
            self.assertIn(marker, audio)
        for forbidden in (
            "LanDemoSession", "Socket(", "java.net.", "javax.net.",
            "BluetoothSocket", "WifiP2p", "SharedPreferences",
            "openFileOutput(", "MediaRecorder.OutputFormat", "startService("
        ):
            self.assertNotIn(forbidden, audio)

    def test_release_authority_and_security_guard_are_unchanged(self):
        flags = (MAIN / "model/ReleaseCapabilities.kt").read_text()
        for name in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn("const val " + name + " = false", flags)
        checks = (ROOT / "scripts/check_preview_permissions.sh").read_text()
        self.assertIn("android.permission.RECORD_AUDIO", checks)
        security = (ROOT / "scripts/check_security_review_boundary.sh").read_text()
        self.assertIn('LOCAL_AUDIO="app/src/debug/java/', security)
        self.assertIn("FAIL: debug mic permission entered release", security)


if __name__ == "__main__":
    unittest.main()
