"""F4-J: a closed LAN TLS session kills only its own microphone/speaker.

These source guards complement the JVM audio-owner concurrency tests.
Actual phone microphone release timing and LAN outage behavior require
physical QA; CI cannot establish audio hardware behavior.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"
SESSION = MAIN / "transport/LanDemoSession.kt"
UI = MAIN / "pairing/LocalPairingTestScreen.kt"


class F4JCloseAudioContract(unittest.TestCase):
    def test_session_has_immediate_one_shot_close_hook(self):
        code = SESSION.read_text()
        for marker in (
            "AtomicReference<(() -> Unit)?>(null)",
            "fun setLocalAudioStopOnClose(callback: () -> Unit)",
            "localAudioStopOnClose.compareAndSet(null, callback)",
            "if (stopped.get()) stopLocalAudioOnClosed()",
            "localAudioStopOnClose.getAndSet(null)?.invoke()",
            "private fun stopLocalAudioOnClosed()",
            "if (!stopped.compareAndSet(false, true)) return",
            "sockets.close()",
            "stopLocalAudioOnClosed()",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, code)
        close = code.split("override fun close() {", 1)[1]
        self.assertLess(close.index("sockets.close()"), close.index("stopLocalAudioOnClosed()"))
        self.assertIn("voiceFloor?.close()", close)
        self.assertIn("liveVoice?.cancel()", close)
        self.assertIn("floorWriter.shutdownNow()", close)

    def test_current_session_only_can_stop_actual_audio(self):
        ui = UI.read_text()
        owner = (MAIN / "pairing/DebugSessionAudioOwner.kt").read_text()
        for marker in (
            "val voiceSessionOwner = remember { DebugSessionAudioOwner() }",
            "connection.setLocalAudioStopOnClose {",
            "voiceSessionOwner.stopIfCurrent(connection) {",
            "voiceAudio.cancelAll()",
            "voiceSessionOwner.activate(connection)",
            "voiceSessionOwner.revoke()",
            "probe = connection",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, ui)
        actual_hook = ui.split("connection.setLocalAudioStopOnClose {", 1)[1]
        self.assertLess(actual_hook.index("voiceSessionOwner.activate(connection)"),
                        actual_hook.index("probe = connection"))
        stop = ui.split("fun stopTest() {", 1)[1].split("fun wifiIsOriginalPairingLink()", 1)[0]
        self.assertLess(stop.index("voiceSessionOwner.revoke()"), stop.index("probe?.close()"))
        dispose = ui.split("onDispose {", 2)[-1]
        self.assertIn("voiceSessionOwner.revoke()", dispose)
        self.assertIn("@Synchronized", owner)
        self.assertIn("if (current !== owner) return false", owner)
        self.assertIn("stopAudio()", owner)

    def test_no_new_path_and_release_permissions_stay_off(self):
        owner = (MAIN / "pairing/DebugSessionAudioOwner.kt").read_text()
        for forbidden in ("AudioRecord(", "Socket(", "SSLSocket(", "FileOutputStream(",
                          "SharedPreferences", "WifiP2pManager", "sendVoiceBurst("):
            self.assertNotIn(forbidden, owner)
        flags = (MAIN / "model/ReleaseCapabilities.kt").read_text()
        for name in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {name} = false", flags)
        self.assertNotIn("android.permission.RECORD_AUDIO",
                         (ROOT / "app/src/main/AndroidManifest.xml").read_text())


if __name__ == "__main__":
    unittest.main()
