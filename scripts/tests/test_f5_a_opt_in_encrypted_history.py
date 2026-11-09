"""F5-A: real opt-in DEBUG local encrypted history, not production assurance.

Security-relevant source boundaries + unit-test inventory; this cannot replace
Android Keystore/two-phone and Android backup/flash deletion QA.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main"
DEBUG = ROOT / "app/src/debug"
RELEASE = ROOT / "app/src/release"
PRIVACY = MAIN / "java/com/edenpractical/comms/privacy"

class F5AOptInHistory(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.policy = (PRIVACY / "LocalHistoryPolicy.kt").read_text()
        cls.envelope = (PRIVACY / "LocalHistoryEnvelope.kt").read_text()
        cls.impl = (DEBUG / "java/com/edenpractical/comms/privacy/DebugLocalHistoryStore.kt").read_text()
        cls.stub = (RELEASE / "java/com/edenpractical/comms/privacy/DebugLocalHistoryStore.kt").read_text()
        cls.lan = (MAIN / "java/com/edenpractical/comms/pairing/LocalPairingTestScreen.kt").read_text()
        cls.ui = (MAIN / "java/com/edenpractical/comms/pairing/DebugConversationPanel.kt").read_text()
        cls.description = (PRIVACY / "ConversationPrivacyContent.kt").read_text()
        cls.cipher_tests = (ROOT / "app/src/test/java/com/edenpractical/comms/privacy/LocalHistoryEnvelopeTest.kt").read_text()
        cls.policy_tests = (ROOT / "app/src/test/java/com/edenpractical/comms/privacy/LocalHistoryPolicyTest.kt").read_text()

    def test_session_only_30_day_absolute_cap_and_fail_closed_clock(self):
        for x in ("SESSION_ONLY = 0L", "THIRTY_DAYS = 30L * ONE_DAY",
                  "MAX_RECORDS_PER_PEER = 64", "MAX_PEERS = 8",
                  "MAX_CLOCK_ROLLBACK_MS = 60_000L",
                  "fun supported(", "fun expiry(", "fun clockSafe(",
                  "fun visible(", "fun shorten("):
            self.assertIn(x, self.policy)
        for x in ("noSavingUnlessExplicitAllowedDuration",
                  "expiryBoundedAndAbsoluteNoRenewalOnRead",
                  "shortPeriodsCanOnlyMakePreviouslySavedRecordsExpireEarlier",
                  "rollbackExpiredMalformedAndOverlongRecordsFailClosed"):
            self.assertIn(x, self.policy_tests)

    def test_aead_is_real_and_jvm_negative_crypto_tests_exist(self):
        for x in ("Cipher.getInstance(\"AES/GCM/NoPadding\")",
                  "Cipher.ENCRYPT_MODE", "Cipher.DECRYPT_MODE",
                  "updateAAD(aad)", "GCMParameterSpec(TAG_BITS, iv)",
                  "sealed = cipher.doFinal(plain)", "doFinal(data)",
                  "MAX_ENVELOPE_SIZE = 256_000"):
            self.assertIn(x, self.envelope)
        for x in ("authenticCiphertextRoundTripsWithUniqueNonce",
                  "tamperedCiphertextWrongKeyOrAadFailsClosed",
                  "invalidLengthHeaderVersionOrOversizedInputFailsClosed"):
            self.assertIn(x, self.cipher_tests)

    def test_app_private_no_backup_keystore_and_expire_filter_before_read(self):
        for x in ("app.noBackupFilesDir", "ApplicationInfo.FLAG_DEBUGGABLE",
                  'KeyStore.getInstance("AndroidKeyStore")',
                  "KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT",
                  "KeyProperties.BLOCK_MODE_GCM", "LocalHistoryEnvelope.seal(",
                  "LocalHistoryEnvelope.open(", "readState(nowWallMs)",
                  "LocalHistoryPolicy.visible(peer.rows, nowMs)",
                  "LocalHistoryPolicy.clockSafe(nowMs, state.lastSeenMs)",
                  "deleteCiphertext()", "read(peerPin: ByteArray",
                  "fun erasePeer(", "fun eraseAll("):
            self.assertIn(x, self.impl, x)
        self.assertIn('android:allowBackup="false"', (MAIN / "AndroidManifest.xml").read_text())
        self.assertIn("<device-transfer>", (MAIN / "res/xml/data_extraction_rules.xml").read_text())
        self.assertNotIn("ExternalStorage", self.impl)
        for bad in ("Log.", "println(", "SharedPreferences", "AudioRecord",
                    "MediaRecorder", "BluetoothSocket", "WifiP2pManager"):
            self.assertNotIn(bad, self.impl)

    def test_release_stub_cannot_write_or_encrypt_and_flags_remain_off(self):
        for x in ("): Boolean = false", "List<LocalHistoryPolicy.SavedMessage> = emptyList()"):
            self.assertIn(x, self.stub)
        for bad in ("AndroidKeyStore", "KeyGenerator", "FileOutputStream",
                    "Cipher.getInstance", "noBackupFilesDir"):
            self.assertNotIn(bad, self.stub)
        release_flags = (MAIN / "java/com/edenpractical/comms/model/ReleaseCapabilities.kt").read_text()
        for flag in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {flag} = false", release_flags)

    def test_only_actual_confirmed_active_lan_text_callbacks_may_persist(self):
        approval = self.lan.index("LanDemoSession.Phase.APPROVAL_COMPLETE ->")
        saved_pin = self.lan.index("activeHistoryPin = expected.copyOf()")
        event = self.lan.index("LanDemoSession.Phase.TEXT_WRITTEN, LanDemoSession.Phase.TEXT_RECEIVED ->")
        save = self.lan.index("localHistoryStore.save(")
        self.assertLess(approval, saved_pin)
        self.assertLess(saved_pin, event)
        self.assertLess(event, save)
        for x in ("historyDurationMs by remember", "LocalHistoryPolicy.SESSION_ONLY",
                  "historyDurationMs != LocalHistoryPolicy.SESSION_ONLY",
                  "disappearAfterMs == DebugLocalDisappearingPolicy.OFF",
                  "beforeHistory !== debugText", "activeHistoryPin?.fill(0)",
                  "historyDurationMs = LocalHistoryPolicy.SESSION_ONLY",
                  "onHistoryDurationChange = { changeLocalHistoryDuration(it) }",
                  "onHistoryRead = { readLocalHistory() }",
                  "onHistoryErase = { eraseLocalHistory() }"):
            self.assertIn(x, self.lan)

    def test_verified_ui_explicit_user_choice_and_delete_with_disclosure(self):
        for x in ("Save messages on this phone", "LocalHistoryPolicy.label(historyDurationMs)",
                  "VIEW SAVED MESSAGES ON THIS PHONE",
                  "DELETE SAVED HISTORY FOR THIS PERSON",
                  "confirmHistoryErase", "onHistoryRead()", "onHistoryErase()"):
            self.assertIn(x, self.ui)
        for x in ("Session-only", "30 days", "release",
                  "not erase that history", "other phone"):
            self.assertIn(x.lower(), self.description.lower())
        self.assertIn("CONTINUE_HERE.md", (ROOT / "README.md").read_text())
        self.assertTrue((ROOT / "docs/WORK_LOG.md").exists())
        self.assertTrue((ROOT / "docs/CURRENT_HANDOFF.md").exists())


if __name__ == "__main__":
    unittest.main()
