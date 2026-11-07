"""M3h source contracts. Not a physical LAN test or security certification."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "app/src/main/java/com/edenpractical/comms"


class NearbyRecoveryUiContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.screen = (SRC / "pairing/LocalPairingTestScreen.kt").read_text()
        cls.guide = (SRC / "pairing/NearbyRecoveryGuide.kt").read_text()
        cls.transport = (SRC / "transport/LanDemoSession.kt").read_text()
        cls.release = (SRC / "model/ReleaseCapabilities.kt").read_text()

    def test_reset_always_invalidates_socket_qrs_and_refreshes_wifi(self):
        reset = self.screen.split("    fun reset() {", 1)[1].split(
            "    fun startRole(", 1)[0]
        for fragment in (
            "stopTest()",
            "clearTemporaryPairing()",
            'transcript = DebugSessionTranscript.fresh()',
            'debugText = DebugTextTranscript.fresh()',
            'textDraft = ""',
            'hostInvite = null',
        ):
            if fragment == "hostInvite = null":
                self.assertIn(fragment, self.screen.split(
                    "fun clearTemporaryPairing()", 1)[1].split("fun refreshWifiStatus", 1)[0] +
                              self.screen.split("fun clearTemporaryPairing()", 1)[1].split("fun reset()", 1)[0])
            else:
                self.assertIn(fragment, reset)
        self.assertIn("refreshWifiStatus()", reset)
        self.assertLess(reset.index("stopTest()"), reset.index("refreshWifiStatus()"))
        self.assertIn('stage = "choose"', reset)

    def test_on_resume_refreshes_only_idle_or_finished_states(self):
        observer = self.screen.split(
            "val observer = LifecycleEventObserver", 1
        )[1].split("lifecycleOwner?.lifecycle.addObserver", 1)[0]
        self.assertIn("Lifecycle.Event.ON_RESUME", observer)
        self.assertIn('stage in listOf("choose", "result")', observer)
        self.assertIn("refreshWifiStatus()", observer)
        self.assertIn("Lifecycle.Event.ON_STOP", observer)
        self.assertIn("stopTest()", observer)

    def test_manual_recovery_never_silently_reuses_old_qr_or_sends_text(self):
        self.assertIn("Intent(Settings.ACTION_WIFI_SETTINGS)", self.screen)
        self.assertIn("Start over with fresh QR codes", self.screen)
        self.assertIn("NearbyRecoveryGuide.forResult(", self.screen)
        result = self.screen.split('"result" -> {', 1)[1].split(
            '"identityError" -> {', 1
        )[0]
        self.assertIn("onClick = { reset() }", result)
        self.assertIn("openWifiSettings()", result)
        self.assertNotIn("runProbe(", result)
        self.assertNotIn("connection.join(", result)
        self.assertNotIn("sendDebugText(", result)
        self.assertNotIn("decodeHost(", result)

    def test_future_radios_are_not_claimed_ready(self):
        # M4-UI01 makes this distinction explicit in an optional methods card,
        # rather than cluttering the Show/Scan QR landing screen.
        main = (SRC / "MainActivity.kt").read_text()
        self.assertIn("Bluetooth and Wi-Fi Direct — planned, not available for normal use.", main)
        self.assertIn("Connection test only • Both phones need the same Wi-Fi.", self.screen)
        for flag in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn("const val " + flag + " = false", self.release)
        self.assertIn("FLAG_DEBUGGABLE", self.transport)
        self.assertNotIn("wifiIp", self.guide)
        self.assertNotIn("sessionCommitment", self.guide)


if __name__ == "__main__":
    unittest.main()
