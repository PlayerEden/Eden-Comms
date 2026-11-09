"""F5-B: static safety contract. JVM tests separately exercise time rules.
This is NOT Android OEM, Bluetooth/Wi-Fi or independent security acceptance.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"
PRIV = MAIN / "privacy"
DEBUG = ROOT / "app/src/debug/java/com/edenpractical/comms/privacy"
RELEASE = ROOT / "app/src/release/java/com/edenpractical/comms/privacy"

class PeerMemorySafetyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.policy = (PRIV / "PeerMemoryPolicy.kt").read_text()
        cls.store = (DEBUG / "DebugPeerMemoryStore.kt").read_text()
        cls.release = (RELEASE / "DebugPeerMemoryStore.kt").read_text()
        cls.pairing = (MAIN / "pairing/LocalPairingTestScreen.kt").read_text()
        cls.ui = (MAIN / "pairing/DebugRememberPeerPanel.kt").read_text()
        cls.home = (MAIN / "MainActivity.kt").read_text()
        cls.history = (DEBUG / "DebugLocalHistoryStore.kt").read_text()
        cls.flags = (MAIN / "model/ReleaseCapabilities.kt").read_text()
        cls.tests = (ROOT / "app/src/test/java/com/edenpractical/comms/privacy/PeerMemoryPolicyTest.kt").read_text()

    def test_max_30_days_no_auto_extension_clock_rollback_and_caps(self):
        for val in ("MAX_PEERS = 8", "MAX_CLOCK_ROLLBACK_MS = 60_000L",
                    "MAX_AGE_MS = PeerTrustExpiry.MAX_DAYS * ONE_DAY_MS",
                    "days !in 1..PeerTrustExpiry.MAX_DAYS",
                    "PeerTrustExpiry.expiresAtOrNull", "PeerTrustExpiry.isValid",
                    "fun clockSafe(", "fun retainedExpiry(",
                    "minOf(existingExpiryMs, requestedExpiryMs)"):
            self.assertIn(val, self.policy)
        for name in ("sessionOnlyDoesNotGenerateExpiryAndBoundsAreOneToThirtyDays",
                     "validRecordExpiresAtExactBoundaryAndRejectsFutureOrForgedLifetime",
                     "savingAgainCannotRenewAndShorteningWorks",
                     "clockRollbackAndUnknownPeerFailClosed"):
            self.assertIn(name, self.tests)

    def test_memory_is_separately_encrypted_and_no_backup(self):
        for s in ("app.noBackupFilesDir", "ApplicationInfo.FLAG_DEBUGGABLE",
                  'KEY_ALIAS = "eden_comms_debug_peer_memory_aes_1"',
                  'DIRECTORY = "eden_comms_peer_memory_debug_v1"',
                  '"eden-comms/debug-peer-memory-v1/"',
                  "KeyStore.getInstance(\"AndroidKeyStore\")",
                  "LocalHistoryEnvelope.open(", "LocalHistoryEnvelope.seal(",
                  "state.ownPin.contentEquals(ownPin)",
                  "PeerMemoryPolicy.clockSafe(nowMs, state.lastSeenMs)",
                  "state.peers.removeAll { !PeerMemoryPolicy.valid(it, nowMs) }",
                  "fun forgetAll()", "fun forget(",
                  "previous?.savedAtMs ?: nowMs", "PeerMemoryPolicy.retainedExpiry("):
            self.assertIn(s, self.store)
        self.assertNotIn("eden_comms_debug_history_aes_1", self.store)
        self.assertNotIn("DebugLocalHistoryStore(", self.store)
        self.assertNotIn("sendPacket(", self.store)
        for disallowed in ("Socket(", "BluetoothSocket", "WifiP2pManager",
                           "Log.", "println(", "phoneNumber", "location", "SharedPreferences",
                           "AudioRecord", "privateKey"):
            self.assertNotIn(disallowed, self.store)

    def test_approved_session_only_and_never_preauthorizes_pairing(self):
        approved = self.pairing.index("LanDemoSession.Phase.APPROVAL_COMPLETE ->")
        checked = self.pairing.index("peerMemoryStore.rememberedUntil(")
        self.assertLess(approved, checked)
        self.assertIn("event.approvalState != DebugApprovalGate.State.CONFIRMED", self.pairing)
        self.assertIn('stage != "approval"', self.pairing)
        self.assertIn("if (stage != \"demo\" || peerMemoryBusy ||", self.pairing)
        self.assertIn("DebugSessionEventAdmission.current(attempt, attemptVersion)", self.pairing)
        self.assertIn("memoryReadRevision == peerMemoryRevision", self.pairing)
        self.assertIn("peerMemoryStore.remember(", self.pairing)
        self.assertIn("peerMemoryStore.forget(", self.pairing)
        self.assertIn("peerMemoryStore.forgetAll()", self.pairing)
        for bad in ("peerMemoryStore.rememberedUntil(", "DebugPeerMemoryStore(", "PeerMemoryPolicy"):
            # Not part of network trust gate/transport authentication sources.
            for path in (MAIN / "security/SessionSecurityGate.kt",
                         MAIN / "security/ProductionSessionAuthorization.kt"):
                self.assertNotIn(bad, path.read_text())

    def test_consent_and_readable_delete_are_not_decorative(self):
        for val in ("REMEMBER VERIFIED PHONE", "SHORTEN REMEMBER PERIOD",
                    "FORGET THIS PHONE", "Forget all remembered phones on this device",
                    "Remember on this phone", "onRemember(", "onForget()",
                    "onForgetAll()", "steps = 28",
                    "new chat ALWAYS needs fresh QR",
                    "rememberedUntilMs", "enabled = !busy"):
            self.assertIn(val, self.ui)
        self.assertIn("DebugRememberPeerPanel(", self.pairing)
        self.assertIn("if (!privacyHidden) {", self.pairing)
        self.assertIn("DebugPeerMemoryStore(context).forgetAll()", self.home)
        self.assertIn("DebugLocalHistoryStore(context).eraseAll()", self.home)

    def test_release_disabled_and_user_choices_separate_from_message_history(self):
        for val in ("Long? = null", "Boolean = false"):
            self.assertIn(val, self.release)
        for bad in ("KeyStore", "AES", "FileOutputStream", "readBytes",
                    "Keystore", "Cipher"):
            self.assertNotIn(bad, self.release)
        for flag in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {flag} = false", self.flags)
        self.assertIn("LocalHistoryPolicy.SESSION_ONLY", self.pairing)
        self.assertIn("var peerMemoryBusy by remember", self.pairing)
        self.assertIn("separate from message history",
                      (PRIV / "ConversationPrivacyContent.kt").read_text())


if __name__ == "__main__":
    unittest.main()
