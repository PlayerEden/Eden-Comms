"""F4-A: the existing real Android microphone and speaker activity must
exercise strict, bounded 20ms packet framing IN MEMORY ONLY.

These source checks and JVM tests are not physical audio, QoS, encrypted
transport, remote PTT or security-approval evidence.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
AUDIO = ROOT / "app/src/debug/java/com/edenpractical/comms/audio"
TEST = ROOT / "app/src/test/java/com/edenpractical/comms/audio"
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"


class F4ALocalPacketPipelineBoundary(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.codec = (AUDIO / "DebugPttPcmFrames.kt").read_text()
        cls.activity = (AUDIO / "DebugLocalPttActivity.kt").read_text()
        cls.test = (TEST / "DebugPttPcmFramesTest.kt").read_text()
        cls.release = (MAIN / "model/ReleaseCapabilities.kt").read_text()

    def test_local_microphone_packets_have_strict_audio_and_sequence_bounds(self):
        for clause in (
            "SAMPLE_RATE_HZ = 16_000",
            "const val FRAME_MS = 20",
            "const val MAX_FRAMES = DebugLocalPttPolicy.MAX_SECONDS",
            "const val HEADER_BYTES = 10",
            "private const val END_FLAG = 1",
            "fun packetize(pcm: ByteArray, validBytes: Int): List<ByteArray>",
            "fun reassemble(frames: List<ByteArray>): ByteArray",
            "validBytes <= pcm.size",
            "validBytes % PCM_BYTES_PER_SAMPLE == 0",
            "if (seq != i + 1)",
            "if (!last && n != PCM_BYTES_PER_FRAME)",
            "if (bytes > DebugLocalPttPolicy.MAX_PCM_BYTES)",
            "if (bytes < DebugLocalPttPolicy.MIN_PCM_BYTES)",
            "fun wipe(frames: List<ByteArray>?)",
            "frames?.forEach { it.fill(0) }",
        ):
            self.assertIn(clause, self.codec, clause)

    def test_actual_audio_record_to_packet_to_speaker_path_and_cleanup(self):
        for clause in (
            "AudioRecord(", "AudioTrack(",
            "MediaRecorder.AudioSource.MIC",
            "AudioRecord.READ_BLOCKING",
            "val clip = if (!hadError",
            "DebugPttPcmFrames.packetize(buffer, written - written % 2)",
            "DebugPttPcmFrames.reassemble(packets)",
            "buffer.fill(0)", "pcm.fill(0)",
            "DebugPttPcmFrames.wipe(audioClip)",
            "DebugPttPcmFrames.wipe(clip)",
            "private var audioClip: List<ByteArray>? = null",
            "override fun onPause()", "terminateAndDiscard()",
            "Manifest.permission.RECORD_AUDIO",
            "requestPermissions(",
            "permission never auto-starts capture",
            "No packets leave the app",
        ):
            self.assertIn(clause, self.activity, clause)
        self.assertNotIn("audioClip?.fill(0)", self.activity)
        for forbidden in (
            "LanDemoSession", "Socket(", "java.net.", "javax.net.",
            "BluetoothSocket", "WifiP2p", "SharedPreferences",
            "openFileOutput(", "startService(",
        ):
            self.assertNotIn(forbidden, self.activity, forbidden)

    def test_real_byte_roundtrips_and_reject_cases_are_exercised(self):
        for test in (
            "twentyMsFramesPreserveActualSamplesIncludingShortFinalFrame",
            "exactFiveSecondRecordingRemainsBoundedAndLossless",
            "validFrameLengthsAndSequencesRoundtripWithoutPadding",
            "invalidCapturedPcmLengthIsRefusedBeforeAnyPacketAllocation",
            "malformedHeaderWrongVersionOrForbiddenFlagsFailBeforePlayback",
            "missingDuplicateAndOutOfOrderAudioFramesFailClosed",
            "clearedPacketBuffersDoNotRemainPlayable",
            "randomizedEvenSampleLengthsMaintainExactRoundtripAndBoundedFrames",
        ):
            self.assertIn(test, self.test, test)

    def test_no_remote_ppt_or_release_permission_accidentally_enabled(self):
        for forbidden in (
            "Socket(", "java.net.", "javax.net.", "LanDemoSession",
            "SSLEngine", "AudioRecord(", "AudioTrack(", "openFileOutput(",
            "SharedPreferences", "DebugBluetooth", "WifiP2p",
            "TestPacketCodec", "sendDebugText(", "sendPacket(",
        ):
            self.assertNotIn(forbidden, self.codec, forbidden)
        for flag in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {flag} = false", self.release)
        self.assertFalse((MAIN / "audio/DebugPttPcmFrames.kt").exists())
        self.assertIn(
            "android.permission.RECORD_AUDIO",
            (ROOT / "app/src/debug/AndroidManifest.xml").read_text()
        )
        self.assertNotIn(
            "android.permission.RECORD_AUDIO",
            (ROOT / "app/src/main/AndroidManifest.xml").read_text()
        )


if __name__ == "__main__":
    unittest.main()
