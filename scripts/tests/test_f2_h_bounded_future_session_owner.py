"""F2-H: bounded, never-live session resource ownership contract.

Source assertions are NOT security approval or device evidence.
The owner is only a future resource-reclamation rehearsal, never
post-consent channel authorization.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
BT = ROOT / "app/src/debug/java/com/edenpractical/comms/bluetooth"
TEST = ROOT / "app/src/test/java/com/edenpractical/comms/bluetooth"
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"


class F2HSessionOwnerBoundary(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.owner = (BT / "DebugBluetoothFutureOwnedSession.kt").read_text()
        cls.budget = (BT / "DebugBluetoothContinuationBudget.kt").read_text()
        cls.probe = (BT / "DebugRfcommTlsProbe.kt").read_text()
        cls.controller = (BT / "DebugRfcommLinkController.kt").read_text()
        cls.lab = (BT / "DebugBluetoothFutureTlsTextLab.kt").read_text()
        cls.tests = (TEST / "DebugBluetoothFutureOwnedSessionTest.kt").read_text()
        cls.paired = (TEST / "DebugPairedTlsEngineInteropTest.kt").read_text()
        cls.release = (MAIN / "model/ReleaseCapabilities.kt").read_text()

    def test_monotonic_elapsed_clock_regression_closed(self):
        for clause in (
            "private var lastObservedMs = -1L",
            "lastObservedMs = elapsedMs",
            "elapsedMs < lastObservedMs",
            "elapsedMs >= deadlineMs",
            "Phase.TERMINATED",
        ):
            self.assertIn(clause, self.budget, clause)
        self.assertIn(
            "rollbackAfterMultipleValidObservationsAlsoTerminatesPermanently",
            (TEST / "DebugBluetoothContinuationBudgetTest.kt").read_text()
        )

    def test_owned_resource_is_closed_on_rejection_or_terminal_event(self):
        for clause in (
            "internal class DebugBluetoothFutureOwnedSession(",
            "private val budget: DebugBluetoothContinuationBudget",
            "enum class Phase { NEW, OBSERVING, CLOSED }",
            "if (phase != Phase.NEW)",
            "budget.beginObservation(generation, elapsedMs, observations)",
            "if (!admitted)",
            "closeQuietly(owned)",
            "if (phase != Phase.OBSERVING || attempt != generation)",
            "budget.stillObserved(",
            "budget.terminate(generation)",
            "end = End.INVALIDATED",
            "end = End.USER",
            "end = End.OWNER_CLOSED",
            "resource = null",
            "closeQuietly(released)",
        ):
            self.assertIn(clause, self.owner, clause)
        for test in (
            "oneResourceIsKeptOnlyWithinBoundedLifetimeThenClosedExactlyOnce",
            "failedPreconditionsCloseOfferedResourceAndCannotRearm",
            "staleAttemptCallbacksCannotCloseCurrentlyObservedResource",
            "validThenBackwardClockClosesLinkAndBurnsGeneration",
            "backgroundAndPermissionLossAlwaysCloseOwnedResource",
            "userDisconnectAndOwnerCloseAreIdempotentEvenIfCloseThrows",
            "competingDisconnectAndDeadlineCallbacksCloseExactlyOnce",
        ):
            self.assertIn(test, self.tests, test)

    def test_tls_pipes_and_message_ledger_reclaimed_in_jvm_only(self):
        for clause in (
            "futureSessionOwnerExpiresAndClosesPairedTlsLabResources",
            "DebugBluetoothFutureOwnedSession(",
            "DebugBluetoothFutureTlsTextLab(host, pipe.hostInput, pipe.hostOutput)",
            "val cleanup = Closeable {",
            "h.clear()",
            "j.clear()",
            "sent.close()",
            "received.close()",
            "pipe.close()",
            "assertEquals(0, sent.outstanding())",
            "pipe.hostOutput.write(1)",
        ):
            self.assertIn(clause, self.paired, clause)

    def test_unauthorized_radio_and_release_remain_completely_unwired(self):
        for live in (self.probe, self.controller):
            self.assertNotIn("DebugBluetoothFutureOwnedSession", live)
            self.assertNotIn("DebugBluetoothContinuationBudget", live)
            self.assertNotIn("DebugBluetoothFutureTlsTextLab", live)
        self.assertIn("closeQuietly(oldSocket)", self.controller)
        self.assertIn("LINK_LIFETIME_MS = 45_000L", self.controller)
        self.assertIn(
            "stop(token, DebugRfcommCompletionPolicy.endAfterConsent(",
            self.controller
        )
        for prohibited in (
            "android.bluetooth", "SSLEngine", "BluetoothSocket",
            "DebugBluetoothProbeCompletion", "TestPacketCodec",
            "sendDebugText(", "DebugBluetoothTextFrameCodec",
            "SharedPreferences", "ProductionSessionAuthorization",
            "AudioRecord",
        ):
            self.assertNotIn(prohibited, self.owner, prohibited)
        self.assertFalse((MAIN / "bluetooth/DebugBluetoothFutureOwnedSession.kt").exists())
        for flag in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {flag} = false", self.release)


if __name__ == "__main__":
    unittest.main()
