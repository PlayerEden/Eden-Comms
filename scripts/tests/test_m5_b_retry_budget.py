"""M5-B source regression: actual LAN join uses a bounded remaining TCP budget.

JVM and static tests do not prove physical Wi-Fi timing or TLS security.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"
TEST = ROOT / "app/src/test/java/com/edenpractical/comms"


class M5BRetryBudget(unittest.TestCase):
    def test_live_join_caps_connect_and_rejects_unbounded_timeout_zero(self):
        src = (MAIN / "transport/LanDemoSession.kt").read_text()
        join = src.split("private fun connectTcp(", 1)[1].split(
            "private fun run(", 1)[0]
        self.assertIn("LanConnectRetryPolicy.attemptTimeoutMs(", join)
        self.assertIn("SystemClock.elapsedRealtime() - started", join)
        self.assertIn("else CONNECT_TIMEOUT_MS", join)
        self.assertIn("if (connectTimeoutMs <= 0)", join)
        self.assertIn("raw.connect(InetSocketAddress(peer, PORT), connectTimeoutMs)", join)
        self.assertLess(join.index("if (connectTimeoutMs <= 0)"),
                        join.index("raw.connect("))
        self.assertIn("catch (e: IOException)", join)
        self.assertIn("raw.close()", join)
        self.assertIn("sockets.releaseSocket(raw)", join)
        self.assertIn("LanConnectRetryPolicy.shouldRetry(e, elapsed, retryWhileWaiting)", join)

    def test_policy_is_pre_tls_only_and_bounds_last_attempt(self):
        src = (MAIN / "transport/LanConnectRetryPolicy.kt").read_text()
        self.assertIn("fun attemptTimeoutMs(elapsedMs: Long): Int", src)
        self.assertIn("if (elapsedMs !in 0 until MAX_WAIT_MS) 0", src)
        self.assertIn("minOf(CONNECT_TIMEOUT_MS.toLong(), MAX_WAIT_MS - elapsedMs)", src)
        self.assertIn("(error is ConnectException || error is SocketTimeoutException)", src)
        for forbidden in ("SSLSocket", "approveForTest(", "Socket(", "raw.connect(", "SharedPreferences"):
            self.assertNotIn(forbidden, src)

        tests = (TEST / "transport/LanConnectRetryPolicyTest.kt").read_text()
        for marker in ("blockingConnectTimeoutNeverExceedsRemainingRetryBudget",
                       "expiredOrInvalidConnectBudgetCannotBecomeInfiniteSocketTimeout",
                       "neverRetriesTlsOrRoutingOrUnexpectedFailures"):
            self.assertIn(marker, tests)

    def test_debug_only_boundaries_unchanged(self):
        flags = (MAIN / "model/ReleaseCapabilities.kt").read_text()
        for name in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn("const val " + name + " = false", flags)
        session = (MAIN / "transport/LanDemoSession.kt").read_text()
        self.assertIn("ApplicationInfo.FLAG_DEBUGGABLE", session)
        self.assertIn("PinnedTlsPeer.requirePinnedTls13", session)
        self.assertIn("requireStableWifi()", session)
        self.assertIn("private val approvalGate = DebugApprovalGate()", session)
        self.assertIn("sockets.close()", session)


if __name__ == "__main__":
    unittest.main()
