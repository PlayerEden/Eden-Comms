"""Real app UI wakefulness is scoped and passive, not a persistent WakeLock."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"
SCREEN = MAIN / "pairing/LocalPairingTestScreen.kt"
POLICY = MAIN / "pairing/PairingScreenAwakePolicy.kt"


class ForegroundScreenAwakeGuards(unittest.TestCase):
    def test_pairing_only_composition_owns_and_disposes_view_keep_screen_on(self):
        screen = SCREEN.read_text()
        policy = POLICY.read_text()
        self.assertIn("val pairingView = LocalView.current", screen)
        self.assertIn("DisposableEffect(pairingView, stage)", screen)
        self.assertIn("PairingScreenAwakePolicy.shouldKeepOn(stage)", screen)
        self.assertIn("pairingView.keepScreenOn = keepAwake", screen)
        self.assertIn("onDispose { pairingView.keepScreenOn = false }", screen)
        self.assertIn('"showHost", "scanHost", "scanReply", "showReply"', policy)
        self.assertIn('"connecting", "approval"', policy)
        self.assertIn('"demo" -> true', policy)
        self.assertIn("else -> false", policy)
        # Our existing foreground-only connection monitor must not be relaxed.
        self.assertIn("Lifecycle.Event.ON_STOP", screen)
        self.assertIn("stopTest()", screen)
        self.assertIn("clearTemporaryPairing()", screen)

    def test_no_wakelock_service_credential_or_release_capability(self):
        policy = POLICY.read_text()
        screen = SCREEN.read_text()
        for forbidden in ("PowerManager", "WakeLock", "WAKE_LOCK",
                          "ForegroundService", "SharedPreferences",
                          "requestPermissions(", "FLAG_KEEP_SCREEN_ON"):
            self.assertNotIn(forbidden, policy)
        self.assertNotIn("acquireWakeLock(", screen)
        flags = (MAIN / "model/ReleaseCapabilities.kt").read_text()
        for flag in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {flag} = false", flags)


if __name__ == "__main__":
    unittest.main()
