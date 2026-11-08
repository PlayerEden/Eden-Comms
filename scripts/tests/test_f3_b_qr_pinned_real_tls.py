"""F3-B1: verify 3-QR identity expectations reach *real JVM TLS* only.

No actual P2P Wi-Fi Direct socket, approval, chat, consent, on-device
identity persistence, production feature, or security assessment is implied.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
WIFI = ROOT / "app/src/debug/java/com/edenpractical/comms/wifidirect"
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"


class F3BQrPinnedTlsLabBoundary(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.expectation = (WIFI / "DebugWifiDirectFutureQrTlsExpectation.kt").read_text()
        cls.lab = (WIFI / "DebugWifiDirectFutureTlsSocketLab.kt").read_text()
        cls.test = (ROOT / "app/src/test/java/com/edenpractical/comms/wifidirect/"
                    "DebugWifiDirectQrPinnedTlsSocketTest.kt").read_text()
        cls.live = [
            (WIFI / file).read_text()
            for file in ("DebugWifiDirectTargetLinkController.kt",
                         "DebugWifiDirectGroupHostActivity.kt",
                         "DebugWifiDirectQrCoordinator.kt",
                         "DebugWifiDirectTcpProbe.kt")
        ]
        cls.release = (MAIN / "model/ReleaseCapabilities.kt").read_text()

    def test_expected_peers_derived_from_all_three_qrs_and_not_trusted_by_itself(self):
        for mark in (
            "DebugWifiDirectQrCodec.checkFresh(host, now)",
            "DebugWifiDirectQrCodec.decodeReply(",
            "DebugWifiDirectQrCodec.makeAck(",
            "DebugWifiDirectQrCodec.decodeAck(",
            "MessageDigest.isEqual(host.pin, ownPin)",
            "MessageDigest.isEqual(ack.transcript, expected.transcript)",
            "Role.HOST, reply.pin, digest(host, reply, ack)",
            "Role.JOIN, host.pin, digest(host, reply, ack)",
            "private val pin = peerPin.copyOf()",
            "private val transcript = transcriptDigest.copyOf()",
            "fun copyPeerPinForTls(): ByteArray",
            "check(!closed)",
            "pin.fill(0)", "transcript.fill(0)",
            "eden-comms/wifi-direct/future-pinned-tls-lab/v1"
        ):
            self.assertIn(mark, self.expectation, mark)

    def test_real_tls_lab_requires_loopback_mutual_auth_and_bounded_handshake(self):
        for mark in (
            "address.isLoopbackAddress && address.address.size == 4",
            "require(port in 1_024..65_535)",
            "ACCEPT_TIMEOUT_MS = 4_000", "CONNECT_TIMEOUT_MS = 2_000",
            "HANDSHAKE_TIMEOUT_MS = 3_000",
            "Tls13PeerContext(identity, peerPin).unboundServerSocket()",
            "Tls13PeerContext(identity, peerPin).unconnectedClientSocket()",
            "server.bind(InetSocketAddress(address, port), 1)",
            "server.accept() as SSLSocket",
            "client.connect(InetSocketAddress(address, port), CONNECT_TIMEOUT_MS)",
            "incoming.startHandshake()", "client.startHandshake()",
            "PinnedTlsPeer.requirePinnedTls13(incoming.session, peerPin)",
            "PinnedTlsPeer.requirePinnedTls13(client.session, peerPin)",
            "peerPin.fill(0)",
        ):
            self.assertIn(mark, self.lab, mark)
        for bad in (
            "getInputStream(", "getOutputStream(", ".read(", ".write(",
            "sendDebugText(", "sendPacket(", "DebugBluetoothTextLedger",
            "DebugBluetoothTextFrameCodec", "AudioRecord", "SharedPreferences",
            "openFileOutput(", "Log.", "println(",
        ):
            self.assertNotIn(bad, self.lab, bad)

    def test_real_jvm_socket_and_negative_qr_tests(self):
        for name in (
            "createTemporaryIdentities",
            "threeFreshQrsThenTwoRealTcpTlsPeersMutuallyAuthenticateAndClose",
            "wrongPeerFingerprintInScannedQrFailsRealMutualTls",
            "alteredAckWrongOwnPinAndExpiredQrFailBeforeTcp",
            "tlsLabRejectsRealRadioAddressesWrongRolesAndClosedContext",
            "DebugWifiDirectQrCodec.decodeAck(",
            "DebugWifiDirectFutureTlsSocketLab.hostOnce(",
            "DebugWifiDirectFutureTlsSocketLab.joinOnce(",
            "client.get(10, TimeUnit.SECONDS)",
        ):
            self.assertIn(name, self.test, name)

    def test_real_radio_remains_zero_data_and_no_release_feature_activated(self):
        for source in self.live:
            self.assertNotIn("DebugWifiDirectFutureTlsSocketLab", source)
            self.assertNotIn("DebugWifiDirectFutureQrTlsExpectation", source)
        for flag in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {flag} = false", self.release)
        self.assertFalse((MAIN.parent / "wifidirect/DebugWifiDirectFutureTlsSocketLab.kt").exists())
        self.assertIn("No application data", self.lab)


if __name__ == "__main__":
    unittest.main()
