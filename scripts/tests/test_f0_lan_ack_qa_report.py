"""F0 LAN: one-phone QA reports count only real accepted peer-app ACK events.

Source tests are not two-phone delivery, peer authentication or security review.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
PAIRING = ROOT / "app/src/main/java/com/edenpractical/comms/pairing"
TRANSPORT = ROOT / "app/src/main/java/com/edenpractical/comms/transport"


class F0LanAckQaReport(unittest.TestCase):
    def test_real_ack_callback_only_after_active_attempt_admission(self):
        screen = (PAIRING / "LocalPairingTestScreen.kt").read_text()
        branch = screen.split(
            "LanDemoSession.Phase.TEXT_PEER_ACK -> {", 1
        )[1].split("LanDemoSession.Phase.ENDED -> {", 1)[0]
        self.assertIn("DebugSessionEventAdmission.canRecord(", branch)
        self.assertIn("debugText.state != DebugTextTranscript.State.ACTIVE", branch)
        self.assertIn("val id = event.messageId ?: return@post", branch)
        self.assertIn("if (id > 0L)", branch)
        self.assertIn("if (qaPeerAppAckEvents < 10_000) qaPeerAppAckEvents += 1", branch)
        self.assertIn("ackedTextIds = (ackedTextIds + id)", branch)
        self.assertNotIn("text =", branch)
        self.assertNotIn("status = \"Delivered", branch)

    def test_counter_survives_terminal_cleanup_but_resets_on_next_attempt(self):
        screen = (PAIRING / "LocalPairingTestScreen.kt").read_text()
        stop = screen.split("fun stopTest() {", 1)[1].split(
            "fun wifiIsOriginalPairingLink()", 1
        )[0]
        fresh = screen.split("fun reset() {", 1)[1].split("fun startRole(", 1)[0]
        probe = screen.split("fun runProbe(asPresenter:", 1)[1].split(
            "val thisAttempt = attemptVersion", 1
        )[0]
        self.assertNotIn("qaPeerAppAckEvents = 0", stop)
        self.assertIn("ackedTextIds = emptySet()", stop)
        self.assertIn("qaPeerAppAckEvents = 0", fresh)
        self.assertIn("qaPeerAppAckEvents = 0", probe)
        self.assertIn("testTextPeerAckEvents = qaPeerAppAckEvents", screen)
        self.assertIn("Peer-app ACK events observed on this phone:", screen)

    def test_report_is_content_free_and_ack_not_read_or_delivery(self):
        report = (PAIRING / "LocalQaReport.kt").read_text()
        self.assertIn("val testTextPeerAckEvents: Int = 0", report)
        self.assertIn("observation.testTextPeerAckEvents in 0..10_000", report)
        self.assertIn("Peer-app ACK events for debug text observed here:", report)
        self.assertIn("NOT message display, human reading or guaranteed delivery", report)
        self.assertIn("One phone only", report)
        self.assertNotIn("observation.peerAddress", report)
        self.assertNotIn("observation.messageContent", report)
        live = (TRANSPORT / "LanDemoSession.kt").read_text()
        self.assertIn("peerAckLedger.acknowledge(", live)
        self.assertIn("Phase.TEXT_PEER_ACK", live)
        self.assertIn("DebugPeerAckLedger.Result.REJECT -> throw TestPacketException()", live)


if __name__ == "__main__":
    unittest.main()
