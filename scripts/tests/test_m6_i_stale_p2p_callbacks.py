"""M6-I actual Wi-Fi Direct join lifetime fences after interrupted sockets.

These are focused source guardrails, plus JVM identity-race tests. They do
NOT run an Android radio, prove callback delivery on OEMs, or audit TLS.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
D = ROOT / "app/src/debug/java/com/edenpractical/comms"
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"


class M6IStaleP2pCallbacks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.fence = (D / "wifidirect/DebugWifiDirectCallbackFence.kt").read_text()
        cls.link = (D / "wifidirect/DebugWifiDirectTargetLinkController.kt").read_text()
        cls.ui = (D / "wifidirect/DebugWifiDirectDiscoveryActivity.kt").read_text()
        cls.session = (D / "wifidirect/DebugWifiDirectApprovedTcpSession.kt").read_text()
        cls.audio = (D / "audio/DebugNearbyVoiceOwner.kt").read_text()
        cls.test = (
            ROOT / "app/src/test/java/com/edenpractical/comms/wifidirect/"
            "DebugWifiDirectCallbackFenceTest.kt"
        ).read_text()
        cls.flags = (MAIN / "model/ReleaseCapabilities.kt").read_text()

    def test_a_delayed_radio_failure_cannot_close_new_qr_or_chat(self):
        for reason in (
            "No active joining P2P group or permission.",
            "Wi-Fi Direct group changed; cannot verify peer.",
            "Exact Android P2P IPv4 route unavailable.",
            "Cannot inspect exact P2P interface for text.",
            "Android P2P endpoint unavailable for TLS.",
        ):
            self.assertIn(f'endPendingVerified(token, request, "{reason}")', self.link)
        function = self.link.split("private fun endPendingVerified(", 1)[1].split(
            "fun approveVerified()", 1)[0]
        self.assertIn("token: DebugWifiDirectTargetLinkGate.Token", function)
        self.assertIn("request: DebugBluetoothTlsProbeInput", function)
        self.assertIn("DebugWifiDirectCallbackFence.mayFailPending(", function)
        for safety in (
            "current = current", "expected = token",
            "pendingRequest = pendingVerifiedRequest",
            "expectedRequest = request",
            "groupObserved = gate.snapshot().phase ==",
            "DebugWifiDirectTargetLinkGate.Phase.GROUP_OBSERVED",
            "verifiedSessionPresent = verifiedSession != null",
        ):
            self.assertIn(safety, function, safety)
        self.assertLess(function.index("mayFailPending("),
                        function.index("pendingVerifiedRequest = null"))
        self.assertLess(function.index("mayFailPending("),
                        function.index("request.close()"))
        self.assertLess(function.index("mayFailPending("),
                        function.index("onVerifiedStatus("))
        self.assertNotIn("pendingVerifiedRequest?.close()", function)
        self.assertNotIn("endPendingVerified(message:", self.link)

    def test_fence_requires_two_live_identities_and_not_promoted(self):
        for invariant in (
            "expected != null && current === expected",
            "owns(current, expected) &&",
            "expectedRequest != null &&",
            "pendingRequest === expectedRequest &&",
            "groupObserved &&",
            "!verifiedSessionPresent",
        ):
            self.assertIn(invariant, self.fence, invariant)
        for forbidden in (
            "startActivity(", "WifiP2pManager", "BluetoothSocket",
            "SSLEngine", "autoReconnect", "approveVerified()",
            "SharedPreferences", "openFileOutput(", "Socket(",
        ):
            self.assertNotIn(forbidden, self.fence)
        self.assertFalse(
            (MAIN / "wifidirect/DebugWifiDirectCallbackFence.kt").exists()
        )

    def test_join_ui_filters_all_old_controller_callbacks_before_side_effects(self):
        start = self.ui.index("var ownedController: DebugWifiDirectTargetLinkController?")
        end = self.ui.index("ownedController = controller", start)
        block = self.ui[start:end]
        for label in ("oldFinished", "currentStatus", "currentText", "currentIdentity"):
            pattern = f"return@{label}"
            self.assertIn(pattern, block, pattern)
        for cb in (
            "onReport =",
            "onFinished =",
            "onVerifiedStatus =",
            "onVerifiedApproval =",
            "onVerifiedText =",
            "onApprovedIdentity =",
        ):
            self.assertIn(cb, block, cb)
        self.assertGreaterEqual(block.count(
            "DebugWifiDirectCallbackFence.owns(targetController, ownedController)"
        ), 6)
        for label, first_side_effect in (
            ("oldFinished", "targetController = null"),
            ("currentStatus", "verifiedChat.status(phase, message)"),
            ("currentText", "verifiedChat.event(event)"),
            ("currentIdentity", "localPrivacy.openVerified(self, peer)"),
        ):
            region = block[block.index(f"return@{label}"):]
            self.assertLess(region.index(f"return@{label}"),
                            region.index(first_side_effect), label)
        self.assertIn(
            "if (DebugWifiDirectCallbackFence.owns(targetController, ownedController)) "
            "targetNotice = message", block)
        self.assertIn("localVoice.disable()", block)
        self.assertIn("preparation.clear()", block)
        self.assertIn("localPrivacy.close()", block)

    def test_underlying_tls_and_voice_still_fail_closed(self):
        for good in (
            "if (current === token && readyAndForeground()",
            "if (current === token && verifiedSession === owned)",
            "pendingVerifiedRequest = request",
            "verifiedSession?.close()",
            "pendingVerifiedRequest?.close()",
            "current = null",
        ):
            self.assertIn(good, self.link, good)
        for good in (
            "if (active !== token || phase != Phase.CHAT",
            "event.pcm.fill(0)",
            "active = null",
            "closeText?.close()",
            "closeQuietly(closeSocket)",
            "lastStopped !== token",
        ):
            self.assertIn(good, self.session, good)
        self.assertIn("audio.cancelAll()", self.audio)
        for flag in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {flag} = false", self.flags)

    def test_jvm_tests_exercise_cancel_late_reply_and_new_owner(self):
        for name in (
            "onlyExactCurrentControllerMayMutateUi",
            "pendingFailureMustMatchBothAttemptAndOneShotQrRequest",
            "canceledOrPromotedRequestCannotCloseAnotherAttempt",
            "delayedCallbackCannotClobberNewOwnerAfterInterruption",
            "assertEquals(0, applied.get())",
            "assertFalse(fence.mayFailPending(",
        ):
            self.assertIn(name, self.test, name)


if __name__ == "__main__":
    unittest.main()
