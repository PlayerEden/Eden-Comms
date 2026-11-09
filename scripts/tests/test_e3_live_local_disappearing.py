"""E3: actual user-facing LAN text expiration, not a mock or Preview page.

This tests source integration alongside JVM policy tests. It cannot substitute
for Android timing/OS lifetime tests or prove erasure on either participant.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"
PAIR = MAIN / "pairing"


class LiveLocalDisappearingSource(unittest.TestCase):
    def test_live_callbacks_and_main_screen_use_exact_same_policy(self):
        screen = (PAIR / "LocalPairingTestScreen.kt").read_text()
        ui = (PAIR / "DebugConversationPanel.kt").read_text()
        record = (PAIR / "DebugTextTranscript.kt").read_text()
        self.assertIn("LanDemoSession.Phase.TEXT_WRITTEN, LanDemoSession.Phase.TEXT_RECEIVED", screen)
        self.assertIn("observedAtElapsedMs = SystemClock.elapsedRealtime()", screen)
        self.assertIn("disappearAfterMs = disappearAfterMs", screen)
        self.assertIn("LaunchedEffect(stage, debugText)", screen)
        self.assertIn("delay(expiry - now)", screen)
        self.assertIn("debugText = debugText.pruneExpired(SystemClock.elapsedRealtime())", screen)
        self.assertIn("onDisappearAfterChange = { newDuration ->", screen)
        self.assertIn("shortenVisibleExpiry(newDuration)", screen)
        self.assertIn("DebugLocalDisappearingPolicy.choices.forEach", ui)
        self.assertIn("onDisappearAfterChange(choice.durationMs)", ui)
        self.assertIn("fun pruneExpired(nowElapsedMs: Long)", record)
        self.assertIn("fun nextExpiryElapsedMs(): Long?", record)
        self.assertIn("entry.expiresAtElapsedMs", record)
        self.assertIn("id <= lastId", record)

    def test_no_hidden_disk_or_remote_deletion_and_release_flags_unchanged(self):
        screen = (PAIR / "LocalPairingTestScreen.kt").read_text()
        ui = (PAIR / "DebugConversationPanel.kt").read_text()
        record = (PAIR / "DebugTextTranscript.kt").read_text()
        policy = (MAIN / "privacy/ConversationPrivacyContent.kt").read_text()
        flags = (MAIN / "model/ReleaseCapabilities.kt").read_text()
        self.assertIn("does not tell the other phone to delete", policy)
        for source in (screen, ui, record):
            for forbidden in ("SharedPreferences", "rememberSaveable(",
                              "SQLiteDatabase", "openFileOutput("):
                self.assertNotIn(forbidden, source)
        self.assertNotIn("LanDemoSession", record)
        self.assertNotIn("BluetoothSocket", record)
        for name in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {name} = false", flags)


if __name__ == "__main__":
    unittest.main()
