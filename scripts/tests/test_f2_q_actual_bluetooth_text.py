"""F2-Q: actual debug Android Bluetooth text path, not future-only lab wiring.

Static integration regressions supplement paired JVM TLS tests. They do NOT
assert Android radio compatibility or independently reviewed security.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
BT = ROOT / "app/src/debug/java/com/edenpractical/comms/bluetooth"
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"


class F2QActualBluetoothText(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.probe = (BT / "DebugRfcommTlsProbe.kt").read_text()
        cls.controller = (BT / "DebugRfcommLinkController.kt").read_text()
        cls.channel = (BT / "DebugBluetoothApprovedTextChannel.kt").read_text()
        cls.screen = (BT / "DebugBluetoothDiscoveryActivity.kt").read_text()
        cls.ui = (BT / "DebugBluetoothTextPanel.kt").read_text()
        cls.interop = (ROOT / "app/src/test/java/com/edenpractical/comms/bluetooth"
                       / "DebugPairedTlsEngineInteropTest.kt").read_text()

    def test_exact_same_engine_and_bytes_not_receipt_or_claimed_auth(self):
        for marker in (
            "DebugTlsEngineHandshake.negotiate(engine, wireIn, wireOut)",
            "PinnedTlsPeer.requirePinnedTls13(engine.session, expectedPin)",
            "SessionProofExchange.runHost(",
            "SessionProofExchange.runJoin(",
            "DebugExporterBindingFrame.exchange(",
            "DebugBluetoothCommitmentFrame.exchange(",
            "DebugBluetoothConsentExchange.run(",
            "val atEnd = request.freshBinding()",
            "onVerifiedStage(DebugBluetoothVerifiedStage.HUMAN_CONFIRMATIONS)",
            "transport.clear()",
            "onApprovedChannel(approvedEngine, wireIn, wireOut)",
        ):
            self.assertIn(marker, self.probe, marker)
        self.assertLess(self.probe.index("DebugBluetoothConsentExchange.run("),
                        self.probe.index("onApprovedChannel(approvedEngine"))
        self.assertLess(self.probe.index("onVerifiedStage(DebugBluetoothVerifiedStage.HUMAN_CONFIRMATIONS)"),
                        self.probe.index("onApprovedChannel(approvedEngine"))
        self.assertNotIn("DebugBluetoothProbeCompletion", self.channel)
        self.assertNotIn("DebugBluetoothFutureSessionContext", self.controller)
        self.assertNotIn("DebugBluetoothContinuationBudget", self.controller)

    def test_live_rfcomm_owner_is_bounded_and_cancelable(self):
        for marker in (
            "val completion = DebugRfcommTlsProbe.run(",
            "onApprovedChannel = { engine, wireInput, wireOutput ->",
            "DebugBluetoothApprovedTextChannel(",
            "active === token",
            "gate.snapshot().phase == DebugRfcommAttemptGate.Phase.LINK_OPEN",
            "isForeground() && hasConnectPermission() && liveText == null",
            "liveText = approved",
            "armTimer(token, TEXT_LIFETIME_MS",
            "approved.runUntilClosed()",
            "oldText = liveText",
            "oldText?.close()",
            "closeQuietly(oldSocket)",
            "fun offerText(text: String): Boolean",
            "handler.post {",
            "liveText == null || !isForeground() || !hasConnectPermission()",
        ):
            self.assertIn(marker, self.controller, marker)
        self.assertIn("TEXT_LIFETIME_MS = 10L * 60_000L", self.controller)
        self.assertNotIn("createInsecureRfcomm", self.controller)
        self.assertNotIn("listenUsingInsecureRfcomm", self.controller)
        self.assertNotIn("TestPacketCodec", self.controller + self.channel)
        self.assertNotIn("ProductionSessionAuthorization", self.channel)

    def test_concurrent_encrypted_writer_reader_ack_and_queue_limits(self):
        for marker in (
            "ArrayBlockingQueue<Pending>(10)",
            "ledger.prepareText(text)",
            "outbound.offer(Pending(frame, text))",
            "encrypted.transmit(pending.bytes)",
            "encrypted.receive()",
            "ledger.acceptText(frame)",
            "outbound.offer(Pending(accepted.ack))",
            "ledger.acceptAck(frame)",
            "pending.bytes.fill(0)",
            "frame.fill(0)",
            "oldSender?.interrupt()",
            "ledger.close()",
            "encrypted.clear()",
        ):
            self.assertIn(marker, self.channel, marker)
        for forbidden in ("BluetoothSocket", "SharedPreferences", "DataStore",
                          "openFileOutput(", "AudioRecord", "MediaRecorder"):
            self.assertNotIn(forbidden, "\n".join(
                l for l in self.channel.splitlines()
                if not l.strip().startswith("*") and not l.strip().startswith("//")), forbidden)

    def test_user_can_send_received_message_and_observe_peer_app_ack(self):
        for marker in (
            "onTextEvent = { event ->",
            "is DebugBluetoothApprovedTextChannel.Event.Ready ->",
            "is DebugBluetoothApprovedTextChannel.Event.Received ->",
            "is DebugBluetoothApprovedTextChannel.Event.Acknowledged ->",
            "DebugBluetoothTextPanel(",
            "onSend = { sendBluetoothText() }",
            "linkController.offerText(draft)",
            "chatLines = emptyList()",
            "linkController.background()",
        ):
            self.assertIn(marker, self.screen, marker)
        self.assertIn("Other app returned ACK", self.ui)
        self.assertIn("not mean a person read it", self.ui)
        self.assertIn("onValueChange = { onDraft(it.take(280)) }", self.ui)
        self.assertNotIn("rememberSaveable", self.screen + self.ui)

    def test_bilateral_real_jvm_protocol_to_full_live_channels(self):
        for marker in (
            "realApprovedTextWorkersExchangeBothDirectionsAndAckOnSameEngines()",
            "fullJvmControlSequence(",
            "DebugBluetoothApprovedTextChannel(",
            "channel.runUntilClosed()",
            'hostChannel.get().offerText("hello from host")',
            'joinChannel.get().offerText("reply from join")',
            "hostAck.await(5, TimeUnit.SECONDS)",
            "joinAck.await(5, TimeUnit.SECONDS)",
        ):
            self.assertIn(marker, self.interop, marker)

    def test_public_release_and_privacy_boundaries_remain_closed(self):
        flags = (MAIN / "model/ReleaseCapabilities.kt").read_text()
        for name in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {name} = false", flags)
        for file in ("DebugBluetoothApprovedTextChannel.kt", "DebugBluetoothTextPanel.kt"):
            self.assertFalse((MAIN / "bluetooth" / file).exists())
        manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text()
        self.assertNotIn("android.permission.BLUETOOTH_CONNECT", manifest)


if __name__ == "__main__":
    unittest.main()
