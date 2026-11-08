"""F0 convenient role retry stays a reset, never automatic reconnection."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCREEN = ROOT / "app/src/main/java/com/edenpractical/comms/pairing/LocalPairingTestScreen.kt"
RETRY = ROOT / "app/src/main/java/com/edenpractical/comms/pairing/PairingFreshRetry.kt"


class FreshQrRetrySourceGates(unittest.TestCase):
    def test_action_resets_first_then_reenters_only_prior_role(self):
        source = SCREEN.read_text()
        self.assertIn('val retryChoice = PairingFreshRetry.forLastRole(qaRole)', source)
        self.assertIn('if (retryChoice != null && wifi != null)', source)
        self.assertIn('reset()\n                                startRole(retryChoice.role)', source)
        self.assertIn('Text(retryChoice.buttonLabel)', source)
        self.assertIn('Text("Start over with fresh QR codes")', source)
        self.assertIn('qaRole = LocalQaReport.Role.NOT_SELECTED', source)
        self.assertIn('clearTemporaryPairing()', source)
        self.assertIn('stopTest()', source)
        self.assertIn('scanRevision += 1', source)
        self.assertIn('LocalPairingQrCodec.newHostInvite(', source)
        self.assertIn('LocalPairingQrCodec.newJoinReply(', source)
        self.assertIn('approvalStage = DebugApprovalGate.State.WAITING', source)
        self.assertIn('qaLocalApprovalWritten = false', source)

    def test_pure_role_choice_cannot_establish_trust_or_persist_secret(self):
        source = RETRY.read_text()
        self.assertIn('LocalQaReport.Role.PRESENTER', source)
        self.assertIn('LocalQaReport.Role.JOINER', source)
        self.assertIn('LocalQaReport.Role.NOT_SELECTED -> null', source)
        for forbidden in (
            "rememberSaveable", "SharedPreferences", "DataStore", "peerPin",
            "privateKey", "SSLSocket", "connect(", "approveForTest(",
            "hostInvite =", "joinReply =", "sessionCommitment",
        ):
            self.assertNotIn(forbidden, source)
        flags = (ROOT / "app/src/main/java/com/edenpractical/comms/model/ReleaseCapabilities.kt").read_text()
        for flag in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {flag} = false", flags)


if __name__ == "__main__":
    unittest.main()
