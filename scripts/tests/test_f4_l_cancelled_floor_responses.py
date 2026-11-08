"""F4-L: multiple abandoned talk requests must not disconnect a good LAN link.

Android/JUnit validates actual state machine transitions; these inexpensive
structural checks ensure the bounded bookkeeping remains connected to
the single encrypted approved session and the release safety boundary.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"


class CancelledVoiceFloorResponseContract(unittest.TestCase):
    def test_cancelled_requests_are_bounded_consumed_once_and_cleared(self):
        floor = (MAIN / "transport/DebugLanVoiceFloor.kt").read_text()
        for marker in (
            "private val abandonedRequests = LinkedHashSet<Long>()",
            "const val MAX_ABANDONED_REQUESTS = 32",
            "abandonedRequests.add(abandonedId)",
            "abandonedRequests.size > MAX_ABANDONED_REQUESTS",
            "oldest.remove()",
            "abandonedRequests.remove(requestId)",
            "abandonedRequests.clear()",
            "if (closed) throw TestPacketException()",
            "val permitted = owner == Owner.FREE",
            "fun maySendAudio()",
            "fun cancelPending()",
            "fun timedOut(nowMs: Long)",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, floor)
        self.assertNotIn("private var cancelled =", floor)

    def test_same_approved_socket_and_cancel_flow(self):
        session = (MAIN / "transport/LanDemoSession.kt").read_text()
        ui = (MAIN / "pairing/LocalPairingTestScreen.kt").read_text()
        for marker in (
            "voiceFloor = DebugLanVoiceFloor(isHost = isHost)",
            "floor.cancelPending()",
            "queueFloorControl(control)",
            "TestPacketCodec.debugVoiceFloor(frame)",
            "sendPacket(TestPacketCodec.debugVoiceFloor(frame))",
            "approvalGate.maySendPreset()",
            "PinnedTlsPeer.requirePinnedTls13",
            "voiceFloor?.close()",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, session)
        self.assertIn('pendingFloorMode = null', ui)
        self.assertIn('probe?.cancelUnstartedVoiceFloor()', ui)
        self.assertIn("LanDemoSession.Phase.VOICE_FLOOR_GRANTED ->", ui)
        self.assertIn("LanDemoSession.Phase.VOICE_FLOOR_DENIED ->", ui)

    def test_production_and_permission_boundary_is_unchanged(self):
        flags = (MAIN / "model/ReleaseCapabilities.kt").read_text()
        for name in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {name} = false", flags)
        self.assertNotIn(
            "android.permission.RECORD_AUDIO",
            (ROOT / "app/src/main/AndroidManifest.xml").read_text()
        )


if __name__ == "__main__":
    unittest.main()
