"""M5-D source-level UI terminal callback admission contract.

Does not prove actual Android main-loop scheduling or encrypted two-phone QA.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"
TEST = ROOT / "app/src/test/java/com/edenpractical/comms"


class M5DTerminalCallbackAdmission(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.screen = (MAIN / "pairing/LocalPairingTestScreen.kt").read_text()
        cls.admission = (MAIN / "pairing/DebugSessionTranscript.kt").read_text()

    def test_both_terminal_branches_reject_nonlive_or_old_callbacks(self):
        for phase in ("ENDED", "FAILED"):
            section = self.screen.split(
                "LanDemoSession.Phase." + phase + " -> {", 1
            )[1].split("                        }", 1)[0]
            self.assertIn("DebugSessionEventAdmission.canAcceptTerminal(", section)
            self.assertIn("thisAttempt, attemptVersion, stage, transcript", section)
            self.assertIn("return@post", section)
            self.assertIn("stopTest()", section)
            self.assertLess(section.index("stopTest()"), section.index("qaFinish = "))
            self.assertIn("stage = \"result\"", section)

    def test_admission_rejects_finished_and_unknown_stages(self):
        self.assertIn("fun canAcceptTerminal(", self.admission)
        self.assertIn("if (!current(callbackAttempt, activeAttempt)) return false", self.admission)
        self.assertIn('"connecting", "showReply", "approval"', self.admission)
        self.assertIn('"demo" -> transcript.state == DebugSessionTranscript.State.ACTIVE', self.admission)
        self.assertIn("else -> false", self.admission)
        tests = (TEST / "pairing/DebugSessionTerminalAdmissionTest.kt").read_text()
        for method in ("terminalEventsAcceptedOnlyInMatchingLivePhases",
                       "staleAndAlreadyFinishedCallbacksNeverOverwriteResultOrNewAttempt",
                       "applyingFirstTerminalInvalidatesOldGenerationBeforeNextRound"):
            self.assertIn(method, tests)

    def test_old_callback_and_history_guards_remain(self):
        self.assertIn("attemptVersion += 1", self.screen)
        self.assertIn("probe?.close()", self.screen)
        self.assertIn("debugText = debugText.finish()", self.screen)
        self.assertIn("textDraft = \"\"", self.screen)
        self.assertIn("clearTemporaryPairing()", self.screen)
        flags = (MAIN / "model/ReleaseCapabilities.kt").read_text()
        for flag in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn("const val " + flag + " = false", flags)


if __name__ == "__main__":
    unittest.main()
