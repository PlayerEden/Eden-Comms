"""M6-G guards for real stopped radio/TLS paths, not a simulated recovery UI.

These source checks and JVM tests do not prove a phone's Android P2P group
was actually removed or that Bluetooth error reporting is interoperable.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
D = ROOT / "app/src/debug/java/com/edenpractical/comms"
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"


class M6GActualFailureRecovery(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.guide = (D / "ui/DebugNearbyRecoveryGuide.kt").read_text()
        cls.bt = (D / "bluetooth/DebugBluetoothDiscoveryActivity.kt").read_text()
        cls.host = (D / "wifidirect/DebugWifiDirectGroupHostActivity.kt").read_text()
        cls.join = (D / "wifidirect/DebugWifiDirectDiscoveryActivity.kt").read_text()
        cls.ui = (D / "wifidirect/DebugWifiDirectChatUiState.kt").read_text()
        cls.flags = (MAIN / "model/ReleaseCapabilities.kt").read_text()
        cls.bt_gate = (D / "bluetooth/DebugRfcommAttemptGate.kt").read_text()
        cls.host_gate = (D / "wifidirect/DebugWifiDirectGroupHostGate.kt").read_text()
        cls.join_gate = (D / "wifidirect/DebugWifiDirectDiscoveryGate.kt").read_text()

    def test_bluetooth_requires_actual_stopped_link_before_recovery_copy(self):
        for token in (
            "if (linkPhase == DebugRfcommAttemptGate.Phase.STOPPED)",
            "DebugNearbyRecoveryPanel(",
            "DebugNearbyRecoveryGuide.Situation.BLUETOOTH_ENDED",
            "DebugNearbyRecoveryGuide.Situation.BLUETOOTH_PERMISSION",
            "hasConnectPermission()",
            "qrCoordinator.clear()",
            "linkController.disconnect()",
        ):
            self.assertIn(token, self.bt, token)
        self.assertIn("enum class Phase { IDLE, CONNECTING, LINK_OPEN, STOPPED }", self.bt_gate)
        self.assertNotIn("linkController.host(", self.guide)
        self.assertNotIn("linkController.join(", self.guide)

    def test_host_guidance_respects_cleanup_and_ongoing_os_group(self):
        for token in (
            "phase == DebugWifiDirectGroupHostGate.Phase.CLEANING",
            "DebugNearbyRecoveryGuide.Situation.P2P_HOST_CLEANING",
            "gate.snapshot().reason ==",
            "DebugWifiDirectGroupHostGate.End.CLEANUP_UNCONFIRMED",
            "DebugNearbyRecoveryGuide.Situation.P2P_HOST_CLEANUP_UNCONFIRMED",
            "phase == DebugWifiDirectGroupHostGate.Phase.STOPPED",
            "DebugNearbyRecoveryGuide.Situation.P2P_HOST_ENDED",
            "phase == DebugWifiDirectGroupHostGate.Phase.HOST_OBSERVED",
            "DebugWifiDirectApprovedTcpSession.Phase.STOPPED",
            "DebugNearbyRecoveryGuide.Situation.P2P_HOST_LINK_ENDED",
            "active = current",
            "verifiedChat.beginNewAttempt()",
            "endHost(",
        ):
            self.assertIn(token, self.host, token)
        self.assertLess(self.host.index("active = current\n        verifiedChat.beginNewAttempt()"),
                        self.host.index("tcpAttempted = false",
                                        self.host.index("active = current")))
        self.assertIn("enum class Phase { IDLE, CHECKING_EXISTING", self.host_gate)
        self.assertIn("CLEANUP_UNCONFIRMED", self.host_gate)

    def test_join_guidance_requires_terminal_state_and_no_active_scan(self):
        for token in (
            "verifiedChat.phase ==",
            "DebugWifiDirectApprovedTcpSession.Phase.STOPPED &&",
            "token == null",
            "DebugNearbyRecoveryGuide.Situation.P2P_JOIN_LINK_ENDED",
            "phase == DebugWifiDirectDiscoveryGate.Phase.STOPPED",
            "radioState == false",
            "DebugNearbyRecoveryGuide.Situation.P2P_JOIN_RADIO_OFF",
            "targetController == null",
            "DebugNearbyRecoveryGuide.Situation.P2P_JOIN_SEARCH_ENDED",
            "verifiedChat.beginNewAttempt()",
            "targetController?.stop(",
            "stopByUser()",
        ):
            self.assertIn(token, self.join, token)
        self.assertIn("enum class Phase { IDLE, WAITING_FOR_P2P", self.join_gate)
        self.assertIn("verifiedChat.beginNewAttempt()\n        candidateLedger.clear()", self.join)

    def test_new_attempt_only_resets_volatile_ui_never_authorizes_chat(self):
        reset = self.ui.split("fun beginNewAttempt() {", 1)[1].split(
            "fun status(", 1)[0]
        for marker in (
            "phase = DebugWifiDirectApprovedTcpSession.Phase.IDLE",
            'note = "New nearby attempt. No verified conversation yet."',
            "approvalPending = false",
            'draft = ""',
            'sendFeedback = ""',
            "lines = emptyList()",
            "ackedIds = emptySet()",
        ):
            self.assertIn(marker, reset, marker)
        for forbidden in (
            "Phase.CHAT", "onVerifiedText", "onApprovedChannel",
            "approveVerified", "offerText(", "startVerifiedText(",
        ):
            self.assertNotIn(forbidden, reset, forbidden)
        self.assertIn("DebugBluetoothApprovedTextChannel.Event.Ready ->", self.ui)
        self.assertIn("phase = DebugWifiDirectApprovedTcpSession.Phase.CHAT", self.ui)

    def test_recovery_is_display_only_in_debug_with_explicit_no_auto_retry(self):
        for token in (
            "internal object DebugNearbyRecoveryGuide",
            "internal fun DebugNearbyRecoveryPanel(",
            "liveRegion = LiveRegionMode.Polite",
            "heading()",
            "No automatic restart occurs",
            "Nothing resumes automatically",
            "Do not assume the group was removed",
            "Both people must approve again",
        ):
            self.assertIn(token, self.guide, token)
        for forbidden in (
            "BluetoothSocket", "WifiP2pManager", "startActivity(",
            "requestPermission(", "startHost(", "startDiscovery(",
            "QRCode", "Socket(", "SSLEngine", "KeyStore",
            "getSharedPreferences(", "startVerifiedText(",
            "ProductionSessionAuthorization",
        ):
            self.assertNotIn(forbidden, self.guide, forbidden)
        for flag in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {flag} = false", self.flags)
        self.assertFalse((MAIN / "ui/DebugNearbyRecoveryGuide.kt").exists())


if __name__ == "__main__":
    unittest.main()
