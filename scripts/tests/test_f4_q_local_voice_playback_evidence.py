"""F4-Q: real Android playout failures must be observable without audio logs.

JVM tests exercise the one-shot multi-threaded voice turn evidence model.
This source check ensures validated TLS START/END cannot be mistaken for
a successful local AudioTrack and that only bounded counts enter QA export.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"
PAIR = MAIN / "pairing"


class F4QLocalVoiceEvidence(unittest.TestCase):
    def test_real_android_voice_callback_reports_rejected_data_only_once(self):
        ui = (PAIR / "LocalPairingTestScreen.kt").read_text()
        for marker in (
            "val currentPlayoutTurn = AtomicReference<DebugVoicePlayoutTurn?>(null)",
            "connection.setLiveVoicePlayback { kind, pcm ->",
            "DebugVoicePlayoutTurn().also { currentPlayoutTurn.set(it) }",
            "voiceAudio.acceptLiveFrame(kind, pcm) { started ->",
            "turn?.speakerResult(started)",
            "kind != DebugLanVoiceWireCodec.Kind.END",
            "turn?.rejectedFrame()",
            "currentPlayoutTurn.get() === turn",
            "DebugSessionEventAdmission.current(thisAttempt, attemptVersion)",
            "qaVoiceSpeakerStarts < 10_000",
            "qaVoicePlaybackProblemTurns < 10_000",
            "voiceNote = \"Live voice arrived, but this phone dropped local speaker audio. Ask to repeat.\"",
            "turn?.hasPlaybackProblem() != true",
            "if (privacyHidden)",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, ui)
        self.assertNotIn("if (!accepted && kind == DebugLanVoiceWireCodec.Kind.START)", ui)

    def test_counts_survive_stop_but_not_a_new_connection(self):
        ui = (PAIR / "LocalPairingTestScreen.kt").read_text()
        fresh = ui.split("fun reset() {", 1)[1].split("fun startRole(", 1)[0]
        connect = ui.split("fun runProbe(asPresenter:", 1)[1].split(
            "val thisAttempt = attemptVersion", 1
        )[0]
        stop = ui.split("fun stopTest() {", 1)[1].split(
            "fun wifiIsOriginalPairingLink()", 1
        )[0]
        for source in (fresh, connect):
            self.assertIn("qaVoiceSpeakerStarts = 0", source)
            self.assertIn("qaVoicePlaybackProblemTurns = 0", source)
        self.assertNotIn("qaVoiceSpeakerStarts = 0", stop)
        self.assertNotIn("qaVoicePlaybackProblemTurns = 0", stop)
        self.assertIn("voiceSpeakerStarts = qaVoiceSpeakerStarts", ui)
        self.assertIn("voicePlaybackProblemTurns = qaVoicePlaybackProblemTurns", ui)

    def test_bounded_content_free_and_no_production_authority(self):
        counter = (PAIR / "DebugVoicePlayoutTurn.kt").read_text()
        report = (PAIR / "LocalQaReport.kt").read_text()
        for marker in (
            "@Synchronized",
            "fun speakerResult(started: Boolean): Observation",
            "fun rejectedFrame(): Observation",
            "fun hasPlaybackProblem(): Boolean",
        ):
            self.assertIn(marker, counter)
        for forbidden in (
            "AudioRecord(", "AudioTrack(", "Socket(", "SSLSocket(",
            "BluetoothSocket", "WifiP2p", "SharedPreferences",
            "FileOutputStream(", "startService(", "ByteArray",
            "peerAddress", "messageContent",
        ):
            self.assertNotIn(forbidden, counter)
        self.assertIn("observation.voiceSpeakerStarts in 0..10_000", report)
        self.assertIn("observation.voicePlaybackProblemTurns in 0..10_000", report)
        self.assertIn("NOT proof of audible sound or delivery", report)
        self.assertIn("No audio, PCM, speech content", report)
        self.assertNotIn("observation.rawPcm", report)
        flags = (MAIN / "model/ReleaseCapabilities.kt").read_text()
        for name in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {name} = false", flags)
        manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text()
        self.assertNotIn("android.permission.RECORD_AUDIO", manifest)


if __name__ == "__main__":
    unittest.main()
