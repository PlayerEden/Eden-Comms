"""F0 live-debug LAN usability: bounded chat lifetime, optional keyboard Send.

Source gates only; this does not assert physical two-phone compatibility.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"
TEST = ROOT / "app/src/test/java/com/edenpractical/comms"


class F0LanChatUsability(unittest.TestCase):
    def test_longer_test_session_remains_bounded_and_liveness_checked(self):
        policy = (MAIN / "transport/DemoSessionLivenessPolicy.kt").read_text()
        session = (MAIN / "transport/LanDemoSession.kt").read_text()
        status = (MAIN / "transport/LanDemoStatus.kt").read_text()
        self.assertIn("const val MAX_SESSION_MINUTES: Long = 30L", policy)
        self.assertIn("MAX_SESSION_MINUTES * 60_000L", policy)
        self.assertIn("const val PEER_SILENCE_MS: Long = 32_000L", policy)
        self.assertIn("nowMs - startedMs >= MAX_SESSION_MS", policy)
        self.assertIn("nowMs - lastReceivedMs > PEER_SILENCE_MS", policy)
        self.assertIn("DemoSessionLivenessPolicy.endReason(", session)
        self.assertIn("APPROVAL_TIMEOUT_MS = 90_000L", session)
        self.assertIn("approvalGate.maySendPreset()", session)
        self.assertIn("SESSION_LIMIT(\"The 30-minute test session limit", status)
        self.assertIn("continuousConfirmedChatCanRunBeyondOldTenMinuteLimit",
                      (TEST / "transport/DemoSessionLivenessPolicyTest.kt").read_text())

    def test_keyboard_send_never_bypasses_existing_composer_validation(self):
        panel = (MAIN / "pairing/DebugConversationPanel.kt").read_text()
        ui = (MAIN / "pairing/LocalPairingTestScreen.kt").read_text()
        self.assertIn("KeyboardOptions(imeAction = ImeAction.Send)", panel)
        self.assertIn("KeyboardActions(onSend = {", panel)
        self.assertIn("if (!hidden && DebugTextDraftPolicy.maySend(draft)) onSend()", panel)
        self.assertIn("enabled = DebugTextDraftPolicy.maySend(draft)", panel)
        self.assertIn("if (DebugTextDraftPolicy.accepts(value)) onDraftChange(value)", panel)
        self.assertIn("The link also closes if the peer stops responding", panel)
        self.assertIn('onSend = {', ui)
        self.assertIn('when (probe?.sendDebugText(textDraft))', ui)
        self.assertIn("LanDemoSession.TextSendResult.AWAITING_APPROVAL", ui)
        self.assertNotIn("SharedPreferences", panel)
        self.assertNotIn("rememberSaveable", panel)
        flags = (MAIN / "model/ReleaseCapabilities.kt").read_text()
        for name in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {name} = false", flags)


if __name__ == "__main__":
    unittest.main()
