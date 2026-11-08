"""M5-E source contracts for actual Compose restart boundaries.

JVM and source checks cannot prove real Android camera/lifecycle races.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"
TESTS = ROOT / "app/src/test/java/com/edenpractical/comms"


class M5ERestartAdmission(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.screen = (MAIN / "pairing/LocalPairingTestScreen.kt").read_text()
        cls.policy = (MAIN / "pairing/PairingRestartAdmission.kt").read_text()

    def test_incomplete_preflight_closes_session_drops_qr_and_reports_failure(self):
        region = self.screen.split(
            "if (own == null || expected == null || invite == null || reply == null)", 1
        )[1].split("        try {", 1)[0]
        for required in (
            "stopTest()", "clearTemporaryPairing()",
            "qaFinish = LocalQaReport.Finish.CONNECTION_FAILED",
            "qaEndCode = null", "qaFailureCode = null",
            "transcript = transcript.finish()", "debugText = debugText.finish()",
            'stage = "result"'
        ):
            self.assertIn(required, region)
        self.assertLess(region.index("stopTest()"), region.index('stage = "result"'))
        self.assertLess(region.index("clearTemporaryPairing()"), region.index('stage = "result"'))

    def test_scanners_check_attempt_and_revision_on_both_callback_types(self):
        scan = self.screen.split('"scanHost", "scanReply" -> {', 1)[1].split(
            '"showReply" -> {', 1
        )[0]
        self.assertIn("val scanAttempt = attemptVersion", scan)
        self.assertIn("val scannerRevision = scanRevision", scan)
        self.assertEqual(2, scan.count("PairingRestartAdmission.mayAcceptScan("))
        self.assertIn("scanAttempt, attemptVersion,", scan)
        self.assertIn("scannerRevision, scanRevision, stage", scan)
        reset = self.screen.split("    fun reset() {", 1)[1].split(
            "    fun startRole(", 1
        )[0]
        self.assertIn("scanRevision += 1", reset)
        self.assertIn("clearTemporaryPairing()", reset)
        self.assertIn("pendingRole = null", reset)

    def test_identity_creation_and_upgrade_require_foreground_fresh_request(self):
        dialog = self.screen.split("if (showIdentityConsent) {", 1)[1]
        self.assertEqual(2, dialog.count("PairingRestartAdmission.mayContinueConsent("))
        self.assertEqual(2, dialog.count("val consentAttempt = attemptVersion"))
        self.assertEqual(2, dialog.count("return@launch"))
        self.assertIn("?.isAtLeast(Lifecycle.State.RESUMED) == true", dialog)
        stop = self.screen.split("DisposableEffect(lifecycleOwner) {", 1)[1].split(
            "onDispose {", 1
        )[0]
        self.assertIn('if (event == Lifecycle.Event.ON_STOP && stage == "choose")', stop)
        self.assertIn("attemptVersion += 1", stop)
        self.assertIn("pendingRole = null", stop)

    def test_no_persistence_or_release_authority(self):
        self.assertNotIn("SharedPreferences", self.policy)
        self.assertNotIn("Socket(", self.policy)
        self.assertNotIn("android.", self.policy)
        tests = (TESTS / "pairing/PairingRestartAdmissionTest.kt").read_text()
        for name in (
            "validForegroundConsentCompletionMayContinueOnlyChosenRole",
            "cancelledScanCallbackCannotArriveIntoIdenticalStageNextAttempt",
            "resetAndBackgroundInvalidateBothIndependentCallbackFamilies"
        ):
            self.assertIn(name, tests)
        flags = (MAIN / "model/ReleaseCapabilities.kt").read_text()
        for name in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn("const val " + name + " = false", flags)


if __name__ == "__main__":
    unittest.main()
