"""F5-C: actual Bluetooth Classic / Wi-Fi Direct privacy wiring contracts.

Code-level regression only. Does NOT certify Android radio interoperability,
OS Keystore ciphertext security, or independent protocol review.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
DEBUG = ROOT / "app/src/debug/java/com/edenpractical/comms"
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"

class F5CAuthenticatedNearbyPrivacy(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        def source(relative):
            return (DEBUG / relative).read_text()
        cls.owner = source("privacy/DebugVerifiedNearbyPrivacy.kt")
        cls.panel = source("privacy/DebugVerifiedNearbyPrivacyPanel.kt")
        cls.bt_controller = source("bluetooth/DebugRfcommLinkController.kt")
        cls.bt_screen = source("bluetooth/DebugBluetoothDiscoveryActivity.kt")
        cls.p2p_session = source("wifidirect/DebugWifiDirectApprovedTcpSession.kt")
        cls.p2p_controller = source("wifidirect/DebugWifiDirectTargetLinkController.kt")
        cls.p2p_host = source("wifidirect/DebugWifiDirectGroupHostActivity.kt")
        cls.p2p_join = source("wifidirect/DebugWifiDirectDiscoveryActivity.kt")

    def test_shared_owner_is_not_network_or_identity_authorization(self):
        for good in (
            "internal class DebugVerifiedNearbyPrivacy(",
            "DebugLocalHistoryStore(context.applicationContext)",
            "DebugPeerMemoryStore(context.applicationContext)",
            "val storageMutex = Mutex()",
            "fun openVerified(ownPin: ByteArray, verifiedPeerPin: ByteArray)",
            "PeerMemoryPolicy.validPin(verifiedPeerPin)",
            "ownPin.contentEquals(verifiedPeerPin)",
            "own = ownPin.copyOf()", "other = verifiedPeerPin.copyOf()",
            "fun receiveEncryptedEvent(event: DebugBluetoothApprovedTextChannel.Event)",
            "is DebugBluetoothApprovedTextChannel.Event.Written",
            "is DebugBluetoothApprovedTextChannel.Event.Received",
            "else -> return // Ready/ACK/Failed are NEVER message-history entries.",
            "history.save(", "history.changeRetention(", "history.read(",
            "peers.rememberedUntil(", "peers.remember(", "peers.forget(",
            "peers.forgetAll()", "fun close()", "revision++",
            "other?.fill(0)", "LocalHistoryPolicy.SESSION_ONLY",
        ):
            self.assertIn(good, self.owner, good)
        for forbidden in ("BluetoothSocket", "WifiP2pManager", "SSLEngine",
                          "BluetoothAdapter", "InetSocketAddress", "TestPacketCodec",
                          "sendDebugText(", "connect(", "approve(",
                          "newTlsProbeInput(", "Log.", "println(", "SharedPreferences",
                          "rememberSaveable"):
            self.assertNotIn(forbidden, self.owner, forbidden)
        for path in ("bluetooth/DebugRfcommTlsProbe.kt",
                     "wifidirect/DebugWifiDirectQrCoordinator.kt",
                     "bluetooth/DebugBluetoothQrCoordinator.kt"):
            self.assertNotIn("DebugPeerMemoryStore(", (DEBUG / path).read_text())

    def test_storage_order_and_off_never_renews_previous_duration(self):
        for good in ("val activeDuration = retentionMs",
                     "activeDuration == LocalHistoryPolicy.SESSION_ONLY",
                     "history.save(", "activeDuration, System.currentTimeMillis()",
                     "storageMutex.withLock", "if (!current(ticket))",
                     "if (!current(ticket) || retentionMs == LocalHistoryPolicy.SESSION_ONLY)"):
            # The prior opt-out check was deliberately replaced with a
            # lock-protected current choice, not a captured stale duration.
            if "if (!current(ticket) || retentionMs ==" not in good:
                self.assertIn(good, self.owner, good)
        self.assertNotIn("duration, System.currentTimeMillis()", self.owner)
        self.assertIn("val ticket = revision", self.owner)
        self.assertIn("scope.cancel()", self.owner)

    def test_blutooth_public_id_only_after_real_confirmed_engine_and_current_token(self):
        approved = self.bt_controller.index("onApprovedChannel = { engine, wireInput, wireOutput ->")
        admitted = self.bt_controller.index("if (!admitted) {", approved)
        self.assertGreater(self.bt_controller.index("onApprovedIdentity(ownForUi, peerForUi)"), admitted)
        for good in ("liveText === approved", "gate.snapshot().phase ==",
                     "hasConnectPermission()", "peerForUi.fill(0)",
                     "onApprovedIdentity = { own, peer ->",
                     "localPrivacy.openVerified(own, peer)",
                     "localPrivacy.receiveEncryptedEvent(event)",
                     "DebugVerifiedNearbyPrivacyPanel(localPrivacy)",
                     "localPrivacy.close()", "localPrivacy.dispose()"):
            self.assertIn(good, self.bt_controller + self.bt_screen)
        self.assertNotIn("onApprovedIdentity", source_text(DEBUG / "bluetooth/DebugBluetoothQrCoordinator.kt"))

    def test_p2p_public_id_only_after_real_confirmed_engine_and_current_attempt(self):
        approved = self.p2p_session.index("onApprovedChannel = { engine, wireInput, wireOutput ->")
        admitted = self.p2p_session.index("if (!admitted) {", approved)
        self.assertGreater(self.p2p_session.index("onApprovedIdentity(ownForUi, peerForUi)"), admitted)
        for good in ("text === approved", "phase == Phase.CHAT", "canContinue()",
                     "peerForUi.fill(0)", "onApprovedIdentity = { self, peer ->"):
            self.assertIn(good, self.p2p_session + self.p2p_controller)
        self.assertIn("onApprovedIdentity(self, peer)", self.p2p_controller)
        for screen in (self.p2p_host, self.p2p_join):
            for good in ("DebugVerifiedNearbyPrivacy(applicationContext)",
                         "localPrivacy.openVerified(", "localPrivacy.receiveEncryptedEvent(",
                         "DebugVerifiedNearbyPrivacyPanel(localPrivacy)",
                         "localPrivacy.close()", "localPrivacy.dispose()"):
                self.assertIn(good, screen, good)

    def test_one_real_user_ui_shared_not_three_decorative_previews(self):
        for good in ("SAVE NEW MESSAGES:",
                     "LocalHistoryPolicy.choices.forEach",
                     "owner.chooseRetention(", "owner.viewSaved()",
                     "DELETE SAVED HISTORY FOR VERIFIED PHONE",
                     "owner.eraseSaved()", "DebugRememberPeerPanel(",
                     "onRemember = { owner.remember(it) }",
                     "onForget = { owner.forgetPeer() }",
                     "onForgetAll = { owner.forgetAll() }"):
            self.assertIn(good, self.panel)
        for forbidden in ("onApprovedIdentity", "onVerifiedPeer", "ConnectionMethod",
                          "BluetoothAdapter", "WifiP2pManager", "Socket"):
            self.assertNotIn(forbidden, self.panel)
        release = (MAIN / "model/ReleaseCapabilities.kt").read_text()
        for feature in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {feature} = false", release)


def source_text(path):
    return path.read_text()


if __name__ == "__main__":
    unittest.main()
