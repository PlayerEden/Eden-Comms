"""F3-C code integration evidence, not hardware/radio or crypto acceptance.

A real Android OS P2P group and its exact interface feed one TCP socket.
No generic discovery, zero-byte TCP result, QR metadata or UI boolean
authorizes text: only same pinned TLS + fresh challenges/exporter +
bilateral APPROVE and encrypted CONFIRM on THAT socket.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
BT = ROOT / "app/src/debug/java/com/edenpractical/comms/bluetooth"
P2P = ROOT / "app/src/debug/java/com/edenpractical/comms/wifidirect"
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"

class F3CLiveP2P(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.session = (P2P / "DebugWifiDirectApprovedTcpSession.kt").read_text()
        cls.host = (P2P / "DebugWifiDirectGroupHostActivity.kt").read_text()
        cls.join = (P2P / "DebugWifiDirectTargetLinkController.kt").read_text()
        cls.discovery = (P2P / "DebugWifiDirectDiscoveryActivity.kt").read_text()
        cls.qr = (P2P / "DebugWifiDirectQrCoordinator.kt").read_text()
        cls.panel = (P2P / "DebugWifiDirectInlineQrPanel.kt").read_text()
        cls.chat = (P2P / "DebugWifiDirectTextPanel.kt").read_text()
        cls.protocol = (BT / "DebugRfcommTlsProbe.kt").read_text()

    def test_live_qr_is_owned_by_radio_screen_not_evaporating_on_navigation(self):
        for activity in (self.host, self.discovery):
            self.assertIn("DebugWifiDirectQrCoordinator()", activity)
            self.assertIn("preparation.loadExistingIdentity()", activity)
            self.assertIn("DebugWifiDirectInlineQrPanel(preparation", activity)
            self.assertIn("preparation.readyForVerifiedTransport(", activity)
            self.assertIn("preparation.clear()", activity)
            self.assertIn("WindowManager.LayoutParams.FLAG_SECURE", activity)
        for marker in ("Stage.HOST_SHOW_ACK", "Stage.JOIN_PREPARED",
                       "newVerifiedTransportRequest(hostRole: Boolean)",
                       "DebugWifiDirectFutureQrTlsExpectation.host(",
                       "DebugWifiDirectFutureQrTlsExpectation.join(",
                       "copyLiveQrProofBinding()", "DebugBluetoothTlsProbeInput("):
            self.assertIn(marker, self.qr)
        self.assertIn("LocalPairingQrScanner(", self.panel)
        self.assertIn("LocalPairingQrDisplay(", self.panel)
        self.assertNotIn("SharedPreferences", self.qr + self.panel)

    def test_real_os_bound_socket_no_default_route_or_unverified_send(self):
        for marker in (
            "DebugWifiDirectTcpProbe.testAddressAllowed(local)",
            "!DebugWifiDirectTcpProbe.testAddressAllowed(owner)",
            "server.bind(InetSocketAddress(local, PORT), 1)",
            "server.accept()",
            "outbound.bind(InetSocketAddress(local, 0))",
            "outbound.connect(InetSocketAddress(owner, PORT), CONNECT_TIMEOUT_MS)",
            "outbound.localAddress != local",
            "outbound.inetAddress != owner",
            "if (!claimSocket(token, accepted, local))",
            "current.getInputStream(), current.getOutputStream(), request",
        ):
            self.assertIn(marker, self.session)
        for forbidden in ("NetworkInterface.getNetworkInterfaces()",
                          "getPassphrase(", "InetSocketAddress(\"",
                          "getHardwareAddress(", "createInsecureRfcomm",
                          "SharedPreferences", "Log.", "println("):
            self.assertNotIn(forbidden, self.session)
        self.assertIn("DebugWifiDirectP2pInterface.resolve(", self.host)
        self.assertIn("DebugWifiDirectP2pInterface.resolve(", self.join)
        self.assertIn("groupOwnerAddress = info.groupOwnerAddress", self.host)
        self.assertIn("groupOwnerAddress = owner", self.join)
        self.assertIn("onClick = { startVerifiedHostChat() }", self.host)
        self.assertIn("onClick = { requestVerifiedP2pText() }", self.discovery)

    def test_actual_tls_proof_consumes_original_streams_then_user_consent(self):
        for marker in (
            "return runStreams(", "fun runStreams(",
            "DebugTlsEngineHandshake.negotiate(engine, wireIn, wireOut)",
            "PinnedTlsPeer.requirePinnedTls13(engine.session, expectedPin)",
            "SessionProofExchange.runHost(", "SessionProofExchange.runJoin(",
            "DebugExporterBindingFrame.exchange(",
            "DebugBluetoothCommitmentFrame.exchange(",
            "DebugBluetoothConsentExchange.run(",
            "onVerifiedStage(DebugBluetoothVerifiedStage.HUMAN_CONFIRMATIONS)",
            "onApprovedChannel(approvedEngine, wireIn, wireOut)",
        ):
            self.assertIn(marker, self.protocol)
        self.assertLess(
            self.protocol.index("DebugBluetoothConsentExchange.run("),
            self.protocol.index("onApprovedChannel(approvedEngine")
        )
        self.assertIn("DebugRfcommTlsProbe.runStreams(", self.session)
        self.assertIn("phase == Phase.AWAITING_APPROVAL && text == null", self.session)
        self.assertIn("approved.runUntilClosed()", self.session)
        self.assertIn("DebugBluetoothApprovedTextChannel(", self.session)
        self.assertNotIn("DebugBluetoothProbeCompletion.afterVerifiedDiagnostic", self.session)
        self.assertNotIn("TestPacketCodec", self.session)

    def test_timeout_permission_cancellation_cleanups_and_real_ui(self):
        for marker in ("INITIAL_DEADLINE_MS = 120_000L",
                       "CHAT_DEADLINE_MS = 10L * 60_000L",
                       "active === token && canContinue()",
                       "closeDecision?.close()", "closeText?.close()",
                       "closeQuietly(closeSocket)", "closeQuietly(closeListener)",
                       "request.close()", "fun approve(): Boolean",
                       "fun decline(): Boolean", "fun offerText(message: String)"):
            self.assertIn(marker, self.session)
        self.assertIn("@Volatile private var foreground = false", self.host)
        self.assertIn("@Volatile private var active:", self.host)
        self.assertIn("@Volatile private var foreground = false", self.discovery)
        self.assertIn("@Volatile private var current:", self.join)
        for marker in ("verifiedSession?.close()",
                       "preparation.clear()", "verifiedChat.clear()",
                       "startVerifiedHostChat()", "verifiedSession?.offerText(draft)"):
            self.assertIn(marker, self.host)
        for marker in ("pendingVerifiedRequest?.close()", "verifiedSession?.close()",
                       "canStartVerifiedText()", "fun offerVerifiedText(text: String)"):
            self.assertIn(marker, self.join)
        self.assertIn("Other app returned ACK", self.chat)
        self.assertIn("not human read receipts", self.chat)
        self.assertIn("onDraft(it.take(280))", self.chat)

    def test_release_source_and_original_untrusted_probe_still_isolated(self):
        flags = (MAIN / "model/ReleaseCapabilities.kt").read_text()
        for key in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {key} = false", flags)
        for name in ("DebugWifiDirectApprovedTcpSession.kt",
                     "DebugWifiDirectInlineQrPanel.kt", "DebugWifiDirectChatUiState.kt",
                     "DebugWifiDirectTextPanel.kt"):
            self.assertFalse((MAIN / "wifidirect" / name).exists())
        zero_data = (P2P / "DebugWifiDirectTcpProbe.kt").read_text()
        self.assertNotIn("DebugRfcommTlsProbe", zero_data)
        self.assertNotIn("DebugBluetoothApprovedTextChannel", zero_data)
        self.assertNotIn("getInputStream(", zero_data)
        self.assertNotIn("getOutputStream(", zero_data)
        self.assertNotIn("DebugWifiDirectApprovedTcpSession", zero_data)


if __name__ == "__main__":
    unittest.main()
