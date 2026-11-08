"""F1: actual debug LAN application ACK, with conservative release boundaries.

These are source-contract checks, not TLS or two-handset experiments.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"
TEST = ROOT / "app/src/test/java/com/edenpractical/comms"


class F1LanPeerAppAck(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.session = (MAIN / "transport/LanDemoSession.kt").read_text()
        cls.codec = (MAIN / "protocol/TestPacketCodec.kt").read_text()
        cls.ui = (MAIN / "pairing/LocalPairingTestScreen.kt").read_text()
        cls.panel = (MAIN / "pairing/DebugConversationPanel.kt").read_text()
        cls.ledger = (MAIN / "transport/DebugPeerAckLedger.kt").read_text()

    def test_real_reader_ack_only_after_validated_received_text(self):
        segment = self.session.split(
            "TestPacketCodec.Type.DEBUG_TEXT -> {", 1
        )[1].split("TestPacketCodec.Type.HEARTBEAT -> Unit", 1)[0]
        self.assertIn("debugTextReceive.accept(", segment)
        self.assertIn("approvalGate.maySendPreset()", segment)
        self.assertLess(segment.index("debugTextReceive.accept("),
                        segment.index("queuePeerAppAck(packet.messageId)"))
        self.assertIn("Phase.TEXT_RECEIVED", segment)
        self.assertIn("TestPacketCodec.Type.DEBUG_ACK -> {", segment)
        self.assertIn("peerAckLedger.acknowledge(", segment)
        self.assertIn("DebugPeerAckLedger.Result.REJECT -> throw TestPacketException()", segment)
        self.assertIn("DebugPeerAckLedger.Result.WAITING_FOR_WRITE -> Unit", segment)

    def test_writer_uses_existing_encrypted_socket_and_bounded_queue(self):
        ack = self.session.split("private fun queuePeerAppAck(", 1)[1].split(
            "fun sendPreset(", 1
        )[0]
        self.assertIn("incomingAckBudget.tryAcquire()", ack)
        self.assertIn("writer.execute {", ack)
        self.assertIn("approvalGate.maySendPreset()", ack)
        self.assertIn("sendPacket(TestPacketCodec.debugAck(id))", ack)
        self.assertIn("incomingAckBudget.release()", ack)
        send = self.session.split("fun sendDebugText(rawText:", 1)[1].split(
            "fun approveForTest()", 1
        )[0]
        self.assertIn("peerAckLedger.queue(id)", send)
        self.assertIn("peerAckLedger.written(id)", send)
        self.assertLess(send.index("sendPacket(packet)"),
                        send.index("peerAckLedger.written(id)"))
        self.assertLess(send.index("Phase.TEXT_WRITTEN"),
                        send.index("Phase.TEXT_PEER_ACK"))
        self.assertIn("peerAckLedger.cancel(id)", send)
        self.assertIn("peerAckLedger.close()", self.session)
        self.assertIn("sockets.currentSocket() as? SSLSocket", self.session)

    def test_ack_codec_is_empty_and_not_the_approval_gate(self):
        self.assertIn("fun debugAck(messageId: Long): Packet", self.codec)
        self.assertIn("ProposedChatWireCodec.Packet.Ack(packet.messageId)", self.codec)
        self.assertIn("is ProposedChatWireCodec.Packet.Ack -> Packet(", self.codec)
        self.assertIn("Type.DEBUG_ACK, messageId = candidate.messageId", self.codec)
        self.assertIn("packet.sessionCommitment.isEmpty()", self.codec)
        self.assertIn("packet.text.isEmpty()", self.codec)
        self.assertNotIn("ProductionSessionAuthorization", self.codec)
        for marker in ("Status.QUEUED", "Status.WRITTEN", "Status.EARLY_ACK",
                       "maxOutstanding = 16", "Result.REJECT", "pending.clear()"):
            if marker == "maxOutstanding = 16":
                self.assertIn(marker, self.session)
            else:
                self.assertIn(marker, self.ledger)

    def test_real_chat_keeps_receipt_nonread_and_ephemeral(self):
        self.assertIn("LanDemoSession.Phase.TEXT_PEER_ACK -> {", self.ui)
        self.assertIn("DebugSessionEventAdmission.canRecord(", self.ui)
        self.assertIn("ackedTextIds = emptySet()", self.ui)
        self.assertIn("peerAckIds = ackedTextIds", self.ui)
        self.assertIn("if (writtenHere && entry.id in peerAckIds)", self.panel)
        self.assertIn("Not a delivery or read receipt.", self.panel)
        self.assertIn("not that anyone read it", self.panel)
        self.assertNotIn("rememberSaveable", self.panel)
        self.assertNotIn("SharedPreferences", self.ledger)
        self.assertNotIn("Socket(", self.ledger)
        for name in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            flags = (MAIN / "model/ReleaseCapabilities.kt").read_text()
            self.assertIn("const val " + name + " = false", flags)

    def test_jvm_races_and_codec_boundary_covered(self):
        ack_tests = (TEST / "transport/DebugPeerAckLedgerTest.kt").read_text()
        for n in ("onlyKnownSentAndConfirmedMessagesCanProduceReceipt",
                  "boundedOutstandingRejectsFloodAndCloseClearsState",
                  "earlyNetworkReceiptRacingLocalWriterIsDeferredUntilWrite",
                  "unknownFutureIdsInvalidApprovalAndCancelledWorkFailClosed"):
            self.assertIn(n, ack_tests)
        codec_tests = (TEST / "protocol/TestPacketCodecTest.kt").read_text()
        self.assertIn("invalidDebugTextAndMalformedPeerAcksFailClosed", codec_tests)
        self.assertIn("codec.debugAck(3)", codec_tests)


if __name__ == "__main__":
    unittest.main()
