"""F4-I: a canceled tap capture cannot enqueue after its AudioRecord callback.

Kotlin/JVM concurrency tests establish the atomic send/cancel decision. This
source guard checks that real Android UI teardown paths actually use that gate
and the existing approved TLS floor is released on start/no-audio failure.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"
UI = MAIN / "pairing/LocalPairingTestScreen.kt"


class TapSendCancellationContract(unittest.TestCase):
    def test_one_shot_gate_blocks_stale_recording_callbacks(self):
        gate = (MAIN / "pairing/DebugTapVoiceSendGate.kt").read_text()
        for required in (
            "@Synchronized",
            "fun <T> submitIfActive(submit: () -> T): T?",
            "if (state != State.CAPTURING) return null",
            "state = State.COMMITTED",
            "fun cancel(): Boolean",
            "state = State.CANCELLED",
        ):
            self.assertIn(required, gate)
        for forbidden in ("AudioRecord", "Socket(", "SSLSocket(",
                          "SharedPreferences", "FileOutputStream",
                          "BluetoothAdapter", "sendVoiceBurst("):
            self.assertNotIn(forbidden, gate)

    def test_real_tap_capture_uses_guard_before_tls_send(self):
        ui = UI.read_text()
        self.assertIn("val sendGate = DebugTapVoiceSendGate()", ui)
        self.assertIn("tapSendGate = sendGate", ui)
        self.assertIn(
            "sendGate.submitIfActive { live.sendVoiceBurst(clip) }", ui
        )
        self.assertNotIn("val result = live.sendVoiceBurst(clip)", ui)
        self.assertIn("if (result != LanDemoSession.VoiceSendResult.QUEUED)", ui)
        self.assertIn("live.cancelUnstartedVoiceFloor()", ui)
        self.assertIn("tapSendGate?.cancel()", ui)
        self.assertIn("tapSendGate = null", ui)
        # Cancel before release; hiding/background must invalidate callbacks.
        cancel_start = ui.index("fun cancelVoiceCapture()")
        cancel_end = ui.index("fun tapVoiceCapture()", cancel_start)
        cancel = ui[cancel_start:cancel_end]
        self.assertLess(
            cancel.index("tapSendGate?.cancel()"),
            cancel.index("probe?.cancelUnstartedVoiceFloor()"),
        )
        stop = ui[ui.index("fun stopTest()"):ui.index("fun wifiIsOriginalPairingLink()")]
        self.assertLess(stop.index("tapSendGate?.cancel()"), stop.index("probe?.close()"))
        hidden = ui[ui.index("onToggleHidden = {"):ui.index("onClearRequested = {")]
        self.assertLess(hidden.index("tapSendGate?.cancel()"), hidden.index("voiceAudio.cancelAll()"))
        dispose = ui[ui.index("onDispose {"):ui.index("Column(verticalArrangement")]
        self.assertIn("tapSendGate?.cancel()", dispose)

    def test_failed_live_start_relinquishes_granted_floor(self):
        ui = UI.read_text()
        method = ui[ui.index("fun beginLiveCaptureWithFloor("):
                    ui.index("fun startLiveVoiceCapture(")]
        self.assertIn("session.cancelUnstartedVoiceFloor()", method)
        self.assertLess(
            method.index("session.cancelUnstartedVoiceFloor()"),
            method.index("voiceNote = when (startedSession.status)")
        )
        session = (MAIN / "transport/LanDemoSession.kt").read_text()
        self.assertIn("if (!voiceQueued.get()) floor.releaseLocal() else null", session)
        self.assertIn("releaseWrittenVoiceFloor()", session)

    def test_release_boundaries_stay_unchanged(self):
        flags = (MAIN / "model/ReleaseCapabilities.kt").read_text()
        for f in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {f} = false", flags)
        self.assertNotIn("android.permission.RECORD_AUDIO",
                         (ROOT / "app/src/main/AndroidManifest.xml").read_text())


if __name__ == "__main__":
    unittest.main()
