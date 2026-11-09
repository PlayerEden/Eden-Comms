"""F4-S: debug live mic must stop polling and release hardware promptly.

JVM budget tests establish monotonic deadlines and no stalled/clock-rolled
resurrection. This source guard verifies the REAL Android live AudioRecord
worker uses NON-BLOCKING reads without changing the older tap fallback.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"
PAIR = MAIN / "pairing"


class F4SNonblockingLiveMic(unittest.TestCase):
    def test_real_live_audio_only_uses_nonblocking_bounded_polling(self):
        actual = (PAIR / "DebugLanVoiceAudio.kt").read_text()
        live = actual.split("fun startLiveCapture(", 1)[1].split(
            "fun acceptLiveFrame(", 1
        )[0]
        for marker in (
            "val gate = DebugLiveMicFrameGate()",
            "val readBudget = DebugLiveMicReadBudget(started)",
            "AudioRecord.READ_NON_BLOCKING",
            "readBudget.mayContinue(SystemClock.elapsedRealtime())",
            "readBudget.observedBytes(",
            "Thread.sleep(DebugLiveMicReadBudget.RETRY_PAUSE_MS)",
            "if (bytes < 0) break",
            "if (bytes == 0)",
            "gate.offerIfActive(frame, onFrame)",
            "frame.fill(0)",
            "gate.stop()",
            "source.startRecording()",
            "source.read(",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, live)
        self.assertNotIn("AudioRecord.READ_BLOCKING", live)
        # Deliberately do not alter accessible record-then-send fallback.
        tap = actual.split("fun startCapture(", 1)[1].split(
            "fun finishCapture()", 1
        )[0]
        self.assertIn("AudioRecord.READ_BLOCKING", tap)

    def test_monotonic_budget_is_bounded_and_has_no_transport(self):
        budget = (PAIR / "DebugLiveMicReadBudget.kt").read_text()
        for marker in (
            "const val MAX_TURN_MS = 3_000L",
            "const val MAX_SILENCE_MS = 450L",
            "const val RETRY_PAUSE_MS = 5L",
            "nowMs < lastCheckedMs",
            "nowMs - startedAt >= MAX_TURN_MS",
            "nowMs - lastProgressMs >= MAX_SILENCE_MS",
            "lastProgressMs = nowMs",
            "stopped = true",
            "count <= 0",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, budget)
        for forbidden in (
            "Socket(", "SSLSocket(", "BluetoothSocket", "WifiP2p",
            "AudioRecord(", "AudioTrack(", "SharedPreferences",
            "FileOutputStream", "startService(", "ByteArray",
        ):
            self.assertNotIn(forbidden, budget)

    def test_approved_transport_and_production_still_unchanged(self):
        session = (MAIN / "transport/LanDemoSession.kt").read_text()
        self.assertIn("approvalGate.maySendPreset()", session)
        self.assertIn("PinnedTlsPeer.requirePinnedTls13(socket.session, expectedPin)", session)
        self.assertIn("stopLocalAudioOnClosed()", session)
        flags = (MAIN / "model/ReleaseCapabilities.kt").read_text()
        for key in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {key} = false", flags)
        self.assertNotIn("android.permission.RECORD_AUDIO",
                         (ROOT / "app/src/main/AndroidManifest.xml").read_text())


if __name__ == "__main__":
    unittest.main()
