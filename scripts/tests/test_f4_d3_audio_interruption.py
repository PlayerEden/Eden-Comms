"""F4-D3: local audio stalls, cancellation, and release boundary are tested in source.

Only actual Android device QA can establish latency, mic/speaker interoperability
or interrupt behavior on an OEM audio driver.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
AUDIO = ROOT / "app/src/debug/java/com/edenpractical/comms/audio"
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"


class F4D3LocalAudioInterruption(unittest.TestCase):
    def test_nonblocking_io_and_bounded_clock(self):
        activity = (AUDIO / "DebugLocalPttActivity.kt").read_text()
        budget = (AUDIO / "DebugLocalPttPlaybackBudget.kt").read_text()
        tests = (ROOT / "app/src/test/java/com/edenpractical/comms/audio/"
                 "DebugLocalPttPlaybackBudgetTest.kt").read_text()
        for required in (
            "AudioRecord.READ_NON_BLOCKING",
            "n < 0 && !capturing.get() -> break",
            "hadError = capturing.get() && foreground",
            "AudioTrack.WRITE_NON_BLOCKING",
            "DebugLocalPttPlaybackBudget(SystemClock.elapsedRealtime())",
            "DebugLocalPttPlaybackBudget.RETRY_PAUSE_MILLIS",
            "budget.mayContinue(SystemClock.elapsedRealtime())",
            "packets.close()",
            "pcm.fill(0)",
            "allFramesWritten",
            "recordingThread === worker",
            "playbackThread === worker",
            "playbackThread?.isAlive == true",
            "currentGeneration.incrementAndGet()",
            "if (foreground && ::status.isInitialized) refresh()",
        ):
            self.assertIn(required, activity)
        for forbidden in ("AudioRecord.READ_BLOCKING", "AudioTrack.WRITE_BLOCKING"):
            self.assertNotIn(forbidden, activity)
        self.assertIn("const val MAX_PLAYBACK_MILLIS = 8_000L", budget)
        self.assertIn("nowElapsedMs < lastObserved", budget)
        self.assertIn("closed = true", budget)
        self.assertIn("monotonicClockRollbackFailsClosedEvenWhenWithinDeadline", tests)
        self.assertIn("explicitStopIsTerminal", tests)

    def test_stop_invalidates_previous_result(self):
        activity = (AUDIO / "DebugLocalPttActivity.kt").read_text()
        stop = activity.split("private fun stopPlayback()", 1)[1].split(
            "private fun discardClip()", 1
        )[0]
        self.assertIn("playing.getAndSet(false)", stop)
        self.assertIn("currentGeneration.incrementAndGet()", stop)
        self.assertIn("Playback stopped.", stop)
        completion = activity.split("val worker = Thread.currentThread()", 2)[-1]
        self.assertIn("DebugLocalPttPolicy.acceptWorkerResult(", completion)
        self.assertIn("playbackThread = null", completion)
        self.assertIn("refresh()", completion)

    def test_no_transport_identity_release_or_storage_change(self):
        activity = (AUDIO / "DebugLocalPttActivity.kt").read_text()
        budget = (AUDIO / "DebugLocalPttPlaybackBudget.kt").read_text()
        for src in (activity, budget):
            for forbidden in (
                "Socket(", "java.net.", "javax.net.", "BluetoothSocket",
                "WifiP2p", "LanDemoSession", "SharedPreferences",
                "openFileOutput(", "startService(",
            ):
                self.assertNotIn(forbidden, src)
        self.assertFalse((MAIN / "audio/DebugLocalPttPlaybackBudget.kt").exists())
        self.assertNotIn(
            "android.permission.RECORD_AUDIO",
            (ROOT / "app/src/main/AndroidManifest.xml").read_text(),
        )
        flags = (MAIN / "model/ReleaseCapabilities.kt").read_text()
        for name in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {name} = false", flags)


if __name__ == "__main__":
    unittest.main()
