"""F2-I: identity-bound *future* handoff correlation, not authorization.

The real diagnostic must continue closing RFCOMM after both approvals.
Source guards are only regression checks, not an independent audit.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
BT = ROOT / "app/src/debug/java/com/edenpractical/comms/bluetooth"
TEST = ROOT / "app/src/test/java/com/edenpractical/comms/bluetooth"
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"
DOC = ROOT / "docs/F2_I_BLUETOOTH_SESSION_REVIEW_GATE.md"


class F2IReviewHandoffTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.context = (BT / "DebugBluetoothFutureSessionContext.kt").read_text()
        cls.owner = (BT / "DebugBluetoothFutureOwnedSession.kt").read_text()
        cls.tests = (TEST / "DebugBluetoothFutureSessionContextTest.kt").read_text()
        cls.owner_tests = (TEST / "DebugBluetoothFutureOwnedSessionTest.kt").read_text()
        cls.probe = (BT / "DebugRfcommTlsProbe.kt").read_text()
        cls.controller = (BT / "DebugRfcommLinkController.kt").read_text()
        cls.release = (MAIN / "model/ReleaseCapabilities.kt").read_text()
        cls.review = DOC.read_text()

    def test_non_authorizing_context_is_copy_isolated_and_compared(self):
        for required in (
            "private val pin = expectedPeerPin.copyOf()",
            "private val commitment = transcriptCommitment.copyOf()",
            "val hostRole: Boolean",
            "MessageDigest.isEqual(local.first, remote.first)",
            "MessageDigest.isEqual(local.second, remote.second)",
            "hostRole == other.hostRole",
            "fun copyForReview()",
            "fun sameAs(",
            "pin.fill(0)",
            "commitment.fill(0)",
            "private fun valid(bytes: ByteArray)",
            "bytes.size == 32",
        ):
            self.assertIn(required, self.context, required)
        for required in (
            "exactRolePeerPinAndTranscriptMustMatch",
            "copiesCannotBeChangedByMutatingCallerArraysOrClosingCaller",
            "closedContextsCannotBeComparedOrCopied",
            "zeroPinsAndBadWidthsAreRefused",
        ):
            self.assertIn(required, self.tests, required)

    def test_matching_generation_context_failure_consumes_future_owner(self):
        for required in (
            "claimedContext.copyForReview()",
            "context = reviewCopy",
            "context?.sameAs(claimedContext) == true",
            "budget.terminate(generation)",
            "releasedContext = context",
            "releasedContext?.close()",
            "closeQuietly(released)",
        ):
            self.assertIn(required, self.owner, required)
        for required in (
            "matchingPeerPinTranscriptAndRoleRequiredForEveryObservation",
            "closedContextIsNotAdmittedAndCallerArrayChangesCannotRebindSession",
            "assertFalse(owner.observe(72, 101, false, false, mismatch))",
            "assertFalse(owner.observe(71, 102, true, true, mismatch))",
        ):
            self.assertIn(required, self.owner_tests, required)

    def test_only_real_probe_can_check_tls_qr_exporter_and_human_consent(self):
        # A caller could invent a matching context. It is NOT authority.
        for live in (self.probe, self.controller):
            self.assertNotIn("DebugBluetoothFutureSessionContext", live)
            self.assertNotIn("DebugBluetoothFutureOwnedSession", live)
        self.assertIn("PinnedTlsPeer.requirePinnedTls13(", self.probe)
        self.assertIn("SessionProofExchange.runHost(", self.probe)
        self.assertIn("DebugExporterBindingFrame.exchange(", self.probe)
        self.assertIn("DebugBluetoothCommitmentFrame.exchange(", self.probe)
        self.assertIn("DebugBluetoothConsentExchange.run(", self.probe)
        self.assertIn("DebugBluetoothProbeCompletion.afterVerifiedDiagnostic(", self.probe)
        self.assertIn("stop(token, DebugRfcommCompletionPolicy.endAfterConsent(", self.controller)
        self.assertIn("closeQuietly(oldSocket)", self.controller)
        self.assertIn("LINK_LIFETIME_MS = 45_000L", self.controller)
        self.assertFalse((MAIN / "bluetooth/DebugBluetoothFutureSessionContext.kt").exists())
        for flag in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {flag} = false", self.release)
        for forbidden in (
            "BluetoothSocket", "SSLEngine", "sendDebugText(", "TestPacketCodec",
            "AudioRecord", "SharedPreferences", "ProductionSessionAuthorization",
        ):
            self.assertNotIn(forbidden, self.context)
            self.assertNotIn(forbidden, self.owner)

    def test_review_packet_requires_actual_independent_and_device_signoff(self):
        # Markdown can wrap a phrase across lines, or apply emphasis inside it.
        # Check normalized reviewer meaning rather than formatting details.
        normalized_review = " ".join(
            self.review.lower().replace("**", "").replace("`", "").split()
        )
        for requirement in (
            "same live RFCOMM socket",
            "two encrypted CONFIRM",
            "Android 12",
            "Conscrypt",
            "peer certificate pin",
            "transcript commitment",
            "background",
            "permission",
            "malformed",
            "independent security review",
            "Do not enable Bluetooth text",
        ):
            normalized_requirement = " ".join(requirement.lower().split())
            self.assertIn(normalized_requirement, normalized_review, requirement)


if __name__ == "__main__":
    unittest.main()
