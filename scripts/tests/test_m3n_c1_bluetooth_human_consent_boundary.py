"""M3n-C1 exact-session human consent over debug Bluetooth TLS, NO chat.

Security/source regression assertions only. Actual RF phone interoperability
and independent review remain unperformed.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
BT = ROOT / "app/src/debug/java/com/edenpractical/comms/bluetooth"
MAIN = ROOT / "app/src/main"


class BluetoothHumanConsentBoundary(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tls = (BT / "DebugRfcommTlsProbe.kt").read_text()
        cls.exchange = (BT / "DebugBluetoothConsentExchange.kt").read_text()
        cls.frame = (BT / "DebugBluetoothConsentFrame.kt").read_text()
        cls.decision = (BT / "DebugBluetoothConsentDecision.kt").read_text()
        cls.link = (BT / "DebugRfcommLinkController.kt").read_text()
        cls.activity = (BT / "DebugBluetoothDiscoveryActivity.kt").read_text()
        cls.transport = (BT / "DebugTlsControlTransport.kt").read_text()
        cls.release = (MAIN / "java/com/edenpractical/comms/model/ReleaseCapabilities.kt").read_text()
        cls.main_manifest = (MAIN / "AndroidManifest.xml").read_text()
        cls.debug_manifest = (ROOT / "app/src/debug/AndroidManifest.xml").read_text()

    def test_authentication_precedes_soliciting_any_human_consent(self):
        order = [
            "DebugTlsEngineHandshake.negotiate(",
            "PinnedTlsPeer.requirePinnedTls13(engine.session, expectedPin)",
            "SessionProofExchange.runHost(",
            "DebugExporterBindingFrame.exchange(",
            "DebugApprovalCommitment.derive(",
            "DebugBluetoothCommitmentFrame.exchange(",
            "DebugBluetoothConsentExchange.run(",
        ]
        positions = [self.tls.index(token) for token in order]
        self.assertEqual(positions, sorted(positions))
        self.assertIn("onApprovalRequired = { onConsentRequired(consent) }", self.tls)
        self.assertIn("request.freshBinding()", self.tls)
        self.assertIn("val atEnd = request.freshBinding()", self.tls)
        self.assertIn("consent.close()", self.tls)

    def test_exact_approved_session_uses_two_distinct_confirm_records(self):
        for part in (
            "DebugApprovalGate()",
            "DebugBluetoothConsentDecision.Decision.APPROVE",
            "DebugBluetoothConsentDecision.Decision.DECLINE",
            "DebugBluetoothConsentDecision.Decision.TIMEOUT",
            "DebugBluetoothConsentFrame.Type.APPROVE",
            "DebugBluetoothConsentFrame.Type.DECLINE",
            "DebugBluetoothConsentFrame.Type.CONFIRM",
            "gate.localWritten()",
            "gate.peerObserved()",
            "gate.mayWriteConfirmation()",
            "gate.localConfirmationWritten()",
            "gate.peerConfirmationObserved()",
            "DebugApprovalGate.State.CONFIRMED",
            "transport.advance(",
            "DebugTlsControlTransport.Phase.COMMITMENT",
            "DebugTlsControlTransport.Phase.APPROVAL",
            "DebugTlsControlTransport.Phase.CONFIRM",
            "decision.await(CONSENT_WAIT_MS)",
            "decision.close()"
        ):
            self.assertIn(part, self.exchange, part)
        for part in (
            "RECORD_BYTES = 4 + 1 + 1 + 1 + 32",
            "MessageDigest.isEqual(",
            "bytes[5].toInt() != (if (expectPeerHost) HOST else JOIN)",
            "bytes.copyOfRange(7, RECORD_BYTES), localCommitment",
            "DataInputStream(input).readFully(bytes)",
            "Type.APPROVE -> APPROVE",
            "Type.DECLINE -> DECLINE",
            "Type.CONFIRM -> CONFIRM",
            "bytes.fill(0)"
        ):
            self.assertIn(part, self.frame, part)
        self.assertIn("APPROVAL(DebugBluetoothConsentFrame.RECORD_BYTES)", self.transport)
        self.assertIn("CONFIRM(DebugBluetoothConsentFrame.RECORD_BYTES)", self.transport)
        self.assertIn("expected == Phase.COMMITMENT && next == Phase.APPROVAL", self.transport)
        self.assertIn("expected == Phase.APPROVAL && next == Phase.CONFIRM", self.transport)

    def test_explicit_user_buttons_are_only_shown_when_active_socket_is_ready(self):
        for part in (
            "onApprovalReady = { available ->",
            "approvalPending = available",
            "if (approvalPending &&",
            "linkPhase == DebugRfcommAttemptGate.Phase.LINK_OPEN",
            "APPROVE THIS TEST SESSION",
            "DECLINE AND CLOSE SESSION",
            "linkController.approve()",
            "linkController.decline()",
            "linkController.background()",
            "approvalPending = false"
        ):
            self.assertIn(part, self.activity, part)
        for part in (
            "private var consent: DebugBluetoothConsentDecision? = null",
            "consent = decision",
            "active !== token",
            "consent !== decision",
            "!gate.isLive(token)",
            "onApprovalReady(true)",
            "onApprovalReady(false)",
            "oldConsent?.close()",
            "fun approve(): Boolean",
            "fun decline(): Boolean",
            "DebugRfcommAttemptGate.End.CONSENT_CONFIRMED",
            "DebugRfcommAttemptGate.End.CONSENT_DECLINED",
            "closeQuietly(oldSocket)"
        ):
            self.assertIn(part, self.link, part)
        for part in (
            "CountDownLatch(1)",
            "fun approve(): Boolean",
            "fun decline(): Boolean",
            "decisionLatch.await(maxWaitMs, TimeUnit.MILLISECONDS)",
            "Decision.TIMEOUT",
            "Decision.CANCELED",
            "closed = true"
        ):
            self.assertIn(part, self.decision, part)

    def test_release_stays_unmodified_and_no_bluetooth_user_payloads(self):
        for name in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {name} = false", self.release)
        for permission in ("BLUETOOTH_SCAN", "BLUETOOTH_CONNECT", "BLUETOOTH_ADVERTISE"):
            self.assertIn("android.permission." + permission, self.debug_manifest)
            self.assertNotIn("android.permission." + permission, self.main_manifest)
        for klass in ("DebugBluetoothConsentFrame.kt",
                      "DebugBluetoothConsentDecision.kt",
                      "DebugBluetoothConsentExchange.kt"):
            self.assertFalse((MAIN / "java/com/edenpractical/comms/bluetooth" / klass).exists())
        for forbidden in (
            "TestPacketCodec", "sendDebugText(", "AudioRecord", "MediaRecorder",
            "SharedPreferences", "DataStore", "ProductionSessionAuthorization",
            "listenUsingInsecureRfcomm", "createInsecureRfcomm",
            "onAuthenticatedRemoteApproval("
        ):
            self.assertNotIn(forbidden, self.tls + self.exchange +
                             self.frame + self.decision + self.link)


if __name__ == "__main__":
    unittest.main()
