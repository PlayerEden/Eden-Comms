"""F4-H: prevent host DENY/GRANT from starving behind 3s audio writes.

Source-level guard augments JVM executor-concurrency tests and Android CI.
All frames still use the same pinned/approved TLS socket and output lock.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"


class F4HFloorControlSchedulingContract(unittest.TestCase):
    def test_urgent_floor_controls_do_not_wait_for_pcm_writer(self):
        lan = (MAIN / "transport/LanDemoSession.kt").read_text()
        queue = (MAIN / "transport/DebugLanFloorControlQueue.kt").read_text()
        for expected in (
            'Thread(action, "eden-demo-floor")',
            "private val floorControls = DebugLanFloorControlQueue(",
            "executor = floorWriter",
            "queueFloorControl(response)",
            "queueFloorControl(request)",
            "queueFloorControl(control)",
            "private fun queueFloorControl(",
            "if (floorControls.offer(frame)) return true",
            "floorWriter.shutdownNow()",
            "private val outputLock = Any()",
            "synchronized(outputLock)",
            "sendPacket(TestPacketCodec.debugVoiceFloor(frame))",
            "PinnedTlsPeer.requirePinnedTls13",
            "approvalGate.maySendPreset()",
        ):
            with self.subTest(marker=expected):
                self.assertIn(expected, lan)

        for expected in (
            "private val budget = DemoSendBudget(maxPending)",
            "if (!budget.tryAcquire()) return false",
            "executor.execute {",
            "budget.release()",
            "onFailure()",
        ):
            with self.subTest(marker=expected):
                self.assertIn(expected, queue)
        for forbidden in ("Socket(", "SSLSocket(", "AudioRecord(",
                          "BluetoothAdapter", "SharedPreferences",
                          "FileOutputStream("):
            self.assertNotIn(forbidden, queue)

    def test_active_pcm_end_must_precede_floor_release(self):
        lan = (MAIN / "transport/LanDemoSession.kt").read_text()
        self.assertIn(
            "if (!voiceQueued.get()) floor.releaseLocal() else null", lan
        )
        self.assertIn(
            "releaseWrittenVoiceFloor()", lan
        )
        self.assertIn(
            "sendVoiceFrame(DebugLanVoiceWireCodec.end(turn, sent))", lan
        )
        # Voice release is still written DIRECTLY by the PCM writer,
        # not offered on the concurrent floor control lane.
        release = lan.split("private fun releaseWrittenVoiceFloor()", 1)[1]
        release = release.split("fun sendVoiceBurst(", 1)[0]
        self.assertIn("sendPacket(TestPacketCodec.debugVoiceFloor(frame))", release)
        self.assertNotIn("queueFloorControl(frame)", release)

    def test_unchanged_release_boundary(self):
        release = (MAIN / "model/ReleaseCapabilities.kt").read_text()
        for flag in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {flag} = false", release)
        self.assertNotIn(
            "android.permission.RECORD_AUDIO",
            (ROOT / "app/src/main/AndroidManifest.xml").read_text()
        )


if __name__ == "__main__":
    unittest.main()
