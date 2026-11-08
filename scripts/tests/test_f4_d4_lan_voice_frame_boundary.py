"""F4-D4 future LAN voice is an isolated *unreviewed* debug byte-shape lab.

A test-only serializer and receiver must not turn into an actual audio sender,
source of trust, production permission, or claimed secure voice.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"
DEBUG = ROOT / "app/src/debug/java/com/edenpractical/comms"
CODEC = DEBUG / "audio/DebugFutureLanVoiceFrameCodec.kt"
TEST = ROOT / "app/src/test/java/com/edenpractical/comms/audio/DebugFutureLanVoiceFrameCodecTest.kt"


class F4D4FutureLanVoiceBoundary(unittest.TestCase):
    def test_codec_is_fixed_and_rejects_malformed_replay_and_gaps(self):
        code = CODEC.read_text()
        tests = TEST.read_text()
        for marker in (
            "const val HEADER_BYTES = 18",
            "const val PCM_BYTES = DebugLocalPttFrames.FRAME_BYTES",
            "const val MAX_FRAMES = DebugLocalPttFrames.MAX_FRAMES",
            "const val PACKET_BYTES = HEADER_BYTES + PCM_BYTES",
            "require(bytes.size == PACKET_BYTES)",
            "pcm.size == PCM_BYTES",
            "sequence >= 0L && sequence < MAX_FRAMES.toLong()",
            "length == PCM_BYTES && input.remaining() == length",
            "decoded.sequence != nextSequence",
            "decoded.close()",
            "closed = true",
            "override fun close() { pcm.fill(0) }",
        ):
            self.assertIn(marker, code)
        for scenario in (
            "malformedSizesSequenceAndHeaderFieldsAreRejected",
            "independentSyntheticSourceAndReceiverExchangeOrderedLocalPcm",
            "gapReplayAndMalformedDataCloseTheSyntheticReceiver",
            "arbitraryOldFrameCanEnterFreshSyntheticReceiverSoNoSessionTrustIsClaimed",
        ):
            self.assertIn(scenario, tests)

    def test_future_codec_has_no_runtime_transports_or_authority(self):
        code = CODEC.read_text()
        for forbidden in (
            "Socket(", "java.net.", "javax.net.", "BluetoothSocket",
            "WifiP2p", "LanDemoSession", "LanTlsProbe", "sendPacket(",
            "AudioRecord", "AudioTrack", "Manifest.permission.",
            "SharedPreferences", "openFileOutput(", "startService(",
            "ProductionSessionAuthorization", "SessionSecurityGate(",
        ):
            self.assertNotIn(forbidden, code)
        self.assertFalse((MAIN / "audio/DebugFutureLanVoiceFrameCodec.kt").exists())
        for root in (MAIN, DEBUG):
            for file in root.rglob("*.kt"):
                if file == CODEC:
                    continue
                self.assertNotIn(
                    "DebugFutureLanVoiceFrameCodec", file.read_text(),
                    f"Future voice codec was wired into {file.relative_to(ROOT)}",
                )
                self.assertNotIn(
                    "DebugFutureLanVoiceSequenceRehearsal", file.read_text(),
                    f"Synthetic receiver was wired into {file.relative_to(ROOT)}",
                )

    def test_release_voice_permission_and_authorization_stay_off(self):
        flags = (MAIN / "model/ReleaseCapabilities.kt").read_text()
        manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text()
        for capability in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {capability} = false", flags)
        self.assertNotIn("android.permission.RECORD_AUDIO", manifest)


if __name__ == "__main__":
    unittest.main()
