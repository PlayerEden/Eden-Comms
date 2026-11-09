"""F2-M: future-only one-channel teardown bundle is NEVER live authority.

Checks the exact debug resource bundle and its integration into the existing
future owner through JVM tests, without wiring actual RFCOMM/TLS or send APIs.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
BT = ROOT / "app/src/debug/java/com/edenpractical/comms/bluetooth"
TEST = ROOT / "app/src/test/java/com/edenpractical/comms/bluetooth"
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"


class F2MFutureChannelBundle(unittest.TestCase):
    def test_resource_bundle_is_atomic_and_failure_independent(self):
        src = (BT / "DebugBluetoothFutureChannelBundle.kt").read_text()
        for marker in (
            "internal class DebugBluetoothFutureChannelBundle(",
            "radioSocket: Closeable",
            "tlsEngine: SSLEngine",
            "recordAdapter: AutoCloseable",
            "fun matchesCandidates(socket: Closeable, tls: SSLEngine)",
            "radio === socket && engine === tls",
            "private val lock = Any()",
            "if (closed) return",
            "closed = true",
            "adapter = null",
            "radio = null",
            "engine = null",
            "toClose.first?.close()",
            "toClose.second?.close()",
            "toClose.third?.closeOutbound()",
            "catch (_: Exception)",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, src)
        self.assertLess(src.index("toClose.first?.close()"),
                        src.index("toClose.second?.close()"))
        self.assertLess(src.index("toClose.second?.close()"),
                        src.index("toClose.third?.closeOutbound()"))
        for forbidden in (
            "fun transmit(", "fun receive(", "fun send(", "fun read(",
            "BluetoothSocket", "SharedPreferences", "FileOutputStream",
            "Socket(", "SSLSocket(", "startService(",
            "DebugBluetoothProbeCompletion",
        ):
            # No real RFCOMM, chat API, saved trust or receipt gate.
            self.assertNotIn(forbidden, src, forbidden)

    def test_real_bluetooth_still_closes_socket_after_consent(self):
        probe = (BT / "DebugRfcommTlsProbe.kt").read_text()
        controller = (BT / "DebugRfcommLinkController.kt").read_text()
        for src in (probe, controller):
            self.assertNotIn("DebugBluetoothFutureChannelBundle", src)
            self.assertNotIn("DebugBluetoothFutureOwnedSession", src)
        self.assertIn("DebugBluetoothConsentExchange.run(", probe)
        self.assertIn("engine?.closeOutbound()", probe)
        self.assertIn("transport?.clear()", probe)
        self.assertIn("stop(token, DebugRfcommCompletionPolicy.endAfterConsent(", controller)
        self.assertIn("closeQuietly(oldSocket)", controller)
        self.assertNotIn("sendDebugText(", controller)

    def test_future_owner_remains_non_authorizing_and_tests_cover_races(self):
        owner = (BT / "DebugBluetoothFutureOwnedSession.kt").read_text()
        tests = (TEST / "DebugBluetoothFutureChannelBundleTest.kt").read_text()
        self.assertIn("owned: Closeable", owner)
        self.assertIn("budget.stillObserved(", owner)
        self.assertIn("closeQuietly(released)", owner)
        self.assertNotIn("fun send(", owner)
        self.assertNotIn("fun receive(", owner)
        for marker in (
            "exactReferenceMatchIsOnlyCorrelationAndClosureIsIrreversible",
            "adapterFailureDoesNotPreventRadioOrEngineCleanup",
            "adapterIsInvalidatedBeforeRadioIsClosed",
            "futureOwnerRejectionClosesAllResourcesWithoutGrantingAnySession",
            "currentContextMismatchClosesBundleButStaleObservationDoesNot",
            "repeatedOfferOfSameOwnedBundleDoesNotCloseItWhileCurrent",
            "concurrentStopTimeoutAndCloseCleanUpExactlyOnce",
        ):
            self.assertIn(marker, tests)

    def test_no_release_feature_or_new_manifest_permissions(self):
        flags = (MAIN / "model/ReleaseCapabilities.kt").read_text()
        for name in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {name} = false", flags)
        release_manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text()
        self.assertNotIn("android.permission.BLUETOOTH_CONNECT", release_manifest)
        self.assertNotIn("android.permission.BLUETOOTH_SCAN", release_manifest)
        self.assertFalse((MAIN / "bluetooth/DebugBluetoothFutureChannelBundle.kt").exists())


if __name__ == "__main__":
    unittest.main()
