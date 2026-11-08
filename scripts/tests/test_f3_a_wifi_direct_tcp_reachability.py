"""F3-A: allow only explicit, bounded, zero-data TCP handshake over OS P2P owner.

This is a physical connectivity probe only: not authenticated, secure
messaging, a trusted contact or evidence of two-phone interoperability.
"""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
D = ROOT / "app/src/debug/java/com/edenpractical/comms/wifidirect"
M = ROOT / "app/src/main"
A = "{http://schemas.android.com/apk/res/android}"


class F3AReachabilityBoundary(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tcp = (D / "DebugWifiDirectTcpProbe.kt").read_text()
        cls.host = (D / "DebugWifiDirectGroupHostActivity.kt").read_text()
        cls.join = (D / "DebugWifiDirectTargetLinkController.kt").read_text()
        cls.join_ui = (D / "DebugWifiDirectDiscoveryActivity.kt").read_text()
        cls.tests = (ROOT / "app/src/test/java/com/edenpractical/comms/wifidirect/"
                     "DebugWifiDirectTcpProbeTest.kt").read_text()
        cls.release = (M / "java/com/edenpractical/comms/model/ReleaseCapabilities.kt").read_text()

    def test_one_actual_bounded_tcp_listener_and_connector_with_zero_payload(self):
        for marker in (
            "TCP_TEST_PORT = 39_761", "ACCEPT_TIMEOUT_MS = 5_000",
            "CONNECT_TIMEOUT_MS = 2_000", "Role { HOST, JOIN }",
            "testAddressAllowed(", "address.address.size == 4",
            "address.isSiteLocalAddress", "forJvmLoopback && address.isLoopbackAddress",
            "listener.bind(InetSocketAddress(groupOwnerAddress, port), 1)",
            "listener.soTimeout = ACCEPT_TIMEOUT_MS",
            "listener.accept()", "outbound.connect(",
            "CONNECT_TIMEOUT_MS", "synchronized(lock)", "stopped = true",
            "closeQuietly(socketToClose)", "closeQuietly(serverToClose)",
            "if (resultAllowed) onCompleted(success)"
        ):
            self.assertIn(marker, self.tcp, marker)
        for forbidden in (
            ".getInputStream(", ".getOutputStream(", ".inputStream",
            ".outputStream", ".read(", ".write(", ".send(",
            "SSLEngine", "SSLContext", "DebugApprovalGate", "DebugBluetooth",
            "SharedPreferences", "openFileOutput(", "Log.", "println(",
            "sendDebugText(", "sendPacket(", "requestDeviceInfo(",
        ):
            self.assertNotIn(forbidden, self.tcp, forbidden)

    def test_android_group_roles_are_checked_before_user_requested_socket(self):
        for marker in (
            "requestTcpHostReachability()", "m.requestConnectionInfo(c)",
            "info.groupFormed", "info.isGroupOwner", "info.groupOwnerAddress",
            "DebugWifiDirectTcpProbe.Role.HOST", "tcpProbe?.close()",
            "RUN 5-SECOND TCP LISTENER TEST — NO DATA",
            "gate.snapshot().phase != DebugWifiDirectGroupHostGate.Phase.HOST_OBSERVED",
            "active !== token", "!foreground", "tcpAttempted = true",
        ):
            self.assertIn(marker, self.host, marker)
        for marker in (
            "fun requestTcpJoinReachability(): Boolean",
            "m.requestConnectionInfo(c)", "info.groupFormed",
            "info.isGroupOwner", "info.groupOwnerAddress",
            "DebugWifiDirectTcpProbe.Role.JOIN",
            "gate.snapshot().phase == DebugWifiDirectTargetLinkGate.Phase.GROUP_OBSERVED",
            "current !== token", "tcpAttempted = true", "tcpProbe?.close()",
            "No user text, voice or identity is transmitted."
        ):
            self.assertIn(marker, self.join, marker)
        for marker in (
            "TEST ONE TCP CONNECTION — NO DATA OR TRUST",
            "targetController?.requestTcpJoinReachability()",
            "targetController?.canAttemptTcp() == true",
            "targetController?.stop(DebugWifiDirectTargetLinkGate.End.BACKGROUND)"
        ):
            self.assertIn(marker, self.join_ui, marker)

    def test_actual_jvm_socket_pair_and_cancellation_covered(self):
        for marker in (
            "realHostAcceptAndJoinConnectUseZeroApplicationBytes",
            "blockedHostAcceptCanBeCanceledWithoutLateSuccessCallback",
            "rejectPublicWildcardMulticastLinkLocalAndDefaultLoopback",
            "duplicateAdmissionIsRejectedAndNoUserPayloadApiExists",
            "CountDownLatch", "forJvmLoopback = true",
        ):
            self.assertIn(marker, self.tests, marker)

    def test_release_flags_permissions_and_qr_boundary_unchanged(self):
        for flag in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn("const val " + flag + " = false", self.release)
        # Check only the real QR metadata/scan UI. Future isolated
        # JVM-only QR/TLS labs deliberately own test sockets; separate F3-B1
        # and F3-B2 guards ensure they remain disconnected from live P2P.
        for name in (
            "DebugWifiDirectQrCodec.kt",
            "DebugWifiDirectQrCoordinator.kt",
            "DebugWifiDirectQrActivity.kt",
        ):
            path = D / name
            text = path.read_text()
            for forbidden in (
                "DebugWifiDirectTcpProbe", "groupOwnerAddress", "ServerSocket(",
                "Socket(", "requestConnectionInfo(", "sendDebugText("
            ):
                self.assertNotIn(forbidden, text, (path.name, forbidden))
        main = ET.parse(M / "AndroidManifest.xml").getroot()
        debug = ET.parse(ROOT / "app/src/debug/AndroidManifest.xml").getroot()
        release_perm = {v.get(A + "name") for v in main.findall("uses-permission")}
        debug_perm = {v.get(A + "name") for v in debug.findall("uses-permission")}
        self.assertEqual(release_perm, {
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.CAMERA"
        })
        self.assertEqual(len(debug_perm), 9)
        self.assertFalse((M / "java/com/edenpractical/comms/wifidirect").exists())


if __name__ == "__main__":
    unittest.main()
