"""F2-P: late Bluetooth approvals cannot revive an expired real consent gate.

Pure Kotlin one-use state plus source checks of the ACTUAL RFCOMM controller
and encrypted consent exchange. No live Bluetooth TEXT, trust storage, radio
permissions or post-consent socket retention is authorized.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
BT = ROOT / "app/src/debug/java/com/edenpractical/comms/bluetooth"
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"


class F2PTerminalConsentDeadline(unittest.TestCase):
    def test_real_consent_gate_consumes_expired_prompt(self):
        gate = (BT / "DebugBluetoothConsentDecision.kt").read_text()
        for marker in (
            "private val monotonicNanos: () -> Long = System::nanoTime",
            "private var awaitConsumed = false",
            "private var startedNanos: Long? = null",
            "private var maxWaitNanos = 0L",
            "if (closed || chosen != null) return false",
            "now < start || elapsed < 0L || elapsed >= maxWaitNanos",
            "chosen = Decision.TIMEOUT",
            "decisionLatch.countDown()",
            "if (awaitConsumed || closed) return Decision.CANCELED",
            "awaitConsumed = true",
            "maxWaitNanos = TimeUnit.MILLISECONDS.toNanos(maxWaitMs)",
            "decisionLatch.await(maxWaitMs, TimeUnit.MILLISECONDS)",
            "catch (interrupted: InterruptedException)",
            "close()",
            "chosen = Decision.CANCELED",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, gate)
        for forbidden in (
            "BluetoothSocket", "SSLEngine", "Socket(", "OutputStream",
            "InputStream", "SharedPreferences", "FileOutputStream",
            "fun send(", "fun transmit(", "DataStore", "startService(",
        ):
            self.assertNotIn(forbidden, gate)

    def test_real_worker_waits_with_bounded_consent_and_closes_socket(self):
        consent = (BT / "DebugBluetoothConsentExchange.kt").read_text()
        controller = (BT / "DebugRfcommLinkController.kt").read_text()
        probe = (BT / "DebugRfcommTlsProbe.kt").read_text()
        self.assertIn("const val CONSENT_WAIT_MS = 25_000L", consent)
        self.assertIn("decision.await(CONSENT_WAIT_MS)", consent)
        self.assertIn("DebugBluetoothConsentDecision.Decision.TIMEOUT ->", consent)
        self.assertIn("throw DebugBluetoothConsentDenied()", consent)
        self.assertIn("decision.close()", consent)
        self.assertIn("val consent = DebugBluetoothConsentDecision()", probe)
        self.assertIn("consent.close()", probe)
        self.assertIn("oldConsent?.close()", controller)
        self.assertIn("closeQuietly(oldSocket)", controller)
        self.assertIn("stop(token, DebugRfcommCompletionPolicy.endAfterConsent(", controller)
        self.assertIn("DebugRfcommCompletionPolicy.mayChooseConsent(", controller)
        self.assertNotIn("DebugBluetoothFutureOwnedSession", controller)
        self.assertNotIn("DebugBluetoothFutureTlsTextLab", probe)

    def test_debug_only_guards_and_concurrency_regressions(self):
        tests = (ROOT / "app/src/test/java/com/edenpractical/comms/bluetooth"
                 / "DebugBluetoothConsentDecisionTest.kt").read_text()
        for marker in (
            "aTimedOutWaitCanNeverAcceptAStaleApprovalOrDecline",
            "aLateTapFailsEvenIfWaitingThreadHasNotYetResumed",
            "aTapBeforeMonotonicDeadlineStillWorksOnce",
            "elapsedClockRollbackFailsClosedAndWakesWorker",
            "interruptionCancelsThePromptAndRejectsAnyLateTap",
            "aSecondWaiterCannotReuseAnActivePrompt",
        ):
            self.assertIn(marker, tests)
        flags = (MAIN / "model/ReleaseCapabilities.kt").read_text()
        for name in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {name} = false", flags)
        self.assertNotIn("android.permission.BLUETOOTH_CONNECT",
                         (ROOT / "app/src/main/AndroidManifest.xml").read_text())


if __name__ == "__main__":
    unittest.main()
