"""Source-level guards for session-aware Back and read-only link state.

These supplement Kotlin unit tests and Android CI; they do not test device
back gestures, network disconnect timing, or cryptographic correctness.
"""
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
MAIN = (ROOT / "app/src/main/java/com/edenpractical/comms/MainActivity.kt").read_text()
SCREEN = (ROOT / "app/src/main/java/com/edenpractical/comms/pairing/LocalPairingTestScreen.kt").read_text()
POLICY = (ROOT / "app/src/main/java/com/edenpractical/comms/pairing/PairingSessionUi.kt").read_text()


class M4Ui05SessionNavigation(unittest.TestCase):
    def test_both_back_paths_share_stage_aware_confirmation(self):
        self.assertIn('PairingSessionUi.needsLeaveWarning(pairingStage)', MAIN)
        self.assertIn('BackHandler(enabled = history.size > 1) { back() }', MAIN)
        self.assertIn('page = page, onBack = back,', MAIN)
        self.assertIn('if (confirmLeavePairing && page == "pairing-test")', MAIN)
        self.assertIn('Text("Leave and disconnect")', MAIN)
        self.assertIn('Text("Stay here")', MAIN)
        self.assertIn('history = NavigationHistory.pop(history)', MAIN)

    def test_live_state_is_observation_only_and_result_can_exit(self):
        self.assertIn('onStageChanged(stage)', SCREEN)
        self.assertIn('PairingSessionUi.statusLabel(stage)', SCREEN)
        self.assertIn('onClick = onBackToPrevious', SCREEN)
        self.assertIn('Text("Start over with fresh QR codes")', SCREEN)
        self.assertIn('latestProbe?.close()', SCREEN)
        self.assertIn('debugText = debugText.finish()', SCREEN)
        self.assertIn('clearTemporaryPairing()', SCREEN)

    def test_no_new_transport_or_saved_history_in_policy(self):
        self.assertIn('"demo" -> "TEMPORARY TEST CHAT ACTIVE"', POLICY)
        self.assertIn('"approval" -> "APPROVAL AND CONFIRMATION PENDING"', POLICY)
        for forbidden in ('rememberSaveable', 'Socket(', 'connect(', 'send(', 'SSID',
                          'peerPin', 'CAN_SEND_MESSAGES = true', 'CAN_TRANSMIT_VOICE = true'):
            self.assertNotIn(forbidden, POLICY)


if __name__ == "__main__":
    unittest.main()
