"""M4-UI06 source guards; not a replacement for Android accessibility QA."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = (ROOT / "app/src/main/java/com/edenpractical/comms/MainActivity.kt").read_text()
SCREEN = (ROOT / "app/src/main/java/com/edenpractical/comms/pairing/LocalPairingTestScreen.kt").read_text()
PANEL = (ROOT / "app/src/main/java/com/edenpractical/comms/pairing/DebugConversationPanel.kt").read_text()
FLAGS = (ROOT / "app/src/main/java/com/edenpractical/comms/model/ReleaseCapabilities.kt").read_text()


class M4Ui06Audit(unittest.TestCase):
    def test_keyboard_insets_and_primary_chat_action_are_reachable_in_source_order(self):
        self.assertIn(".navigationBarsPadding()\n                    // On edge-to-edge", MAIN)
        self.assertIn(".imePadding()", MAIN)
        self.assertLess(PANEL.index('Text("Jump to latest")'),
                        PANEL.index('label = { Text("Test message") }'))
        self.assertLess(PANEL.index('onClick = onSend'),
                        PANEL.index('DebugConversationUiCopy.counts('))
        self.assertLess(PANEL.index('onClick = onSend'),
                        PANEL.index('What does written to test link mean?'))
        self.assertIn('Not a delivery or read receipt.', PANEL)
        self.assertIn('if (hidden) {', PANEL)
        self.assertIn('enabled = DebugTextDraftPolicy.maySend(draft)', PANEL)

    def test_recreation_warning_is_non_sensitive_and_never_reconnects(self):
        self.assertIn('pairingWasInProgress by rememberSaveable', MAIN)
        self.assertIn('val interruptedOnRecreation = remember { pairingWasInProgress }', MAIN)
        self.assertIn('PairingSessionUi.needsLeaveWarning(it)', MAIN)
        self.assertIn('interruptedBeforeRecreation = interruptedOnRecreation', MAIN)
        self.assertIn('showRecreationNotice = false', SCREEN)
        self.assertIn('if (showRecreationNotice) {', SCREEN)
        self.assertIn('Nothing was ', SCREEN)
        self.assertIn('fresh QR codes.', SCREEN)
        self.assertIn('onStageChanged(stage)', SCREEN)
        self.assertIn('latestProbe?.close()', SCREEN)
        self.assertIn('clearTemporaryPairing()', SCREEN)
        for forbidden in ('rememberSaveable { mutableStateOf(hostInvite)',
                          'rememberSaveable { mutableStateOf(debugText)',
                          'rememberSaveable { mutableStateOf(textDraft)'):
            self.assertNotIn(forbidden, SCREEN)

    def test_no_release_authority_or_hidden_transport_expansion(self):
        for flag in ('CAN_CONNECT', 'CAN_SEND_MESSAGES', 'CAN_TRANSMIT_VOICE'):
            self.assertIn('const val ' + flag + ' = false', FLAGS)
        for blocked in ('Socket(', 'LanDemoSession', 'approveForTest(', 'sendDebugText('):
            self.assertNotIn(blocked, PANEL)


if __name__ == "__main__":
    unittest.main()
