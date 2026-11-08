"""F3-B3-A: only the OS P2P group interface may source a TCP test socket.

This is an untrusted, ZERO-user-data radio diagnostic, not authenticated
Wi-Fi Direct transport, a TLS certificate test or physical Android evidence.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
D = ROOT / "app/src/debug/java/com/edenpractical/comms/wifidirect"
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"


class F3B3P2pInterfaceRouteBoundary(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.policy = (D / "DebugWifiDirectP2pInterface.kt").read_text()
        cls.probe = (D / "DebugWifiDirectTcpProbe.kt").read_text()
        cls.host = (D / "DebugWifiDirectGroupHostActivity.kt").read_text()
        cls.join = (D / "DebugWifiDirectTargetLinkController.kt").read_text()
        cls.tests = (ROOT / "app/src/test/java/com/edenpractical/comms/"
                     "wifidirect/DebugWifiDirectP2pInterfaceTest.kt").read_text()
        cls.socket_tests = (ROOT / "app/src/test/java/com/edenpractical/comms/"
                            "wifidirect/DebugWifiDirectTcpProbeTest.kt").read_text()
        cls.release = (MAIN / "model/ReleaseCapabilities.kt").read_text()

    def test_os_group_named_interface_no_scan_default_route_or_secret_log(self):
        for marker in (
            "NetworkInterface.getByName(osGroupInterfaceName)",
            "if (!device.isUp || device.isLoopback) return null",
            "select(isHost, groupOwnerAddress, entries)",
            "return viable.singleOrNull()?.address",
            "item.prefixLength in 8..30",
            "owner != local",
            "sameNetwork(local, owner, item.prefixLength)",
            "groupOwnerAddress.isAnyLocalAddress",
        ):
            self.assertIn(marker, self.policy, marker)
        for forbidden in (
            "getNetworkInterfaces()", "getHardwareAddress(",
            "getPassphrase(", "getNetworkName(", "Log.", "println(",
            "SharedPreferences", "openFileOutput(", "SessionProofExchange",
            "SSLEngine", "Tls13PeerContext",
        ):
            self.assertNotIn(forbidden, self.policy, forbidden)

    def test_host_uses_current_p2p_group_interface_even_when_owner_redacted(self):
        for marker in (
            "m.requestConnectionInfo(c)", "m.requestGroupInfo(c)",
            "!info.groupFormed || !info.isGroupOwner",
            "group?.isGroupOwner != true",
            "group.getInterface()",
            "DebugWifiDirectP2pInterface.resolve(",
            "groupOwnerAddress = info.groupOwnerAddress",
            "DebugWifiDirectTcpProbe.Role.HOST",
            "localP2pAddress = local",
            "active !== token", "tcpProbe?.close()",
        ):
            self.assertIn(marker, self.host, marker)
        self.assertNotIn("NetworkInterface.getNetworkInterfaces()", self.host)
        self.assertNotIn("getPassphrase(", self.host)

    def test_joiner_binds_source_before_connect_and_only_after_group_verification(self):
        for marker in (
            "m.requestConnectionInfo(c)", "m.requestGroupInfo(c)",
            "!info.groupFormed || info.isGroupOwner",
            "group == null || group.isGroupOwner",
            "group.getInterface()", "DebugWifiDirectP2pInterface.resolve(",
            "groupOwnerAddress = ownerAddress",
            "DebugWifiDirectTcpProbe.Role.JOIN",
            "localP2pAddress = local", "current !== token",
            "readyAndForeground()", "tcpProbe?.close()",
        ):
            self.assertIn(marker, self.join, marker)
        for marker in (
            "outbound.bind(InetSocketAddress(local, 0))",
            "outbound.connect(",
            "outbound.localAddress == local",
            "listener.bind(InetSocketAddress(local, port), 1)",
            "if (forJvmLoopback && groupOwnerAddress?.isLoopbackAddress != true)",
            "if (local == null || !testAddressAllowed(local, forJvmLoopback))",
        ):
            self.assertIn(marker, self.probe, marker)
        for forbidden in (
            ".getInputStream(", ".getOutputStream(", ".read(", ".write(",
            "SSLEngine", "Tls13PeerContext", "AudioRecord",
            "sendDebugText(", "sendPacket(", "SharedPreferences",
        ):
            self.assertNotIn(forbidden, self.probe, forbidden)

    def test_negative_policy_and_no_release_grant(self):
        for marker in (
            "hostAcceptsRealLocalInterfaceWhenAndroidRedactsOwnerAddress",
            "joinerMustHaveDifferentLocalIpInsideExactOwnerSubnet",
            "subnetPrefixIsRespectedIncludingNonByteAlignedMasks",
            "ambiguousInvalidAndPublicInterfacesFailClosed",
            "lookupDoesNotDefaultToLoopbackOrUnknownInterfaceName",
        ):
            self.assertIn(marker, self.tests, marker)
        self.assertIn("androidP2pProbeCannotUseDefaultRouteWithoutLocalInterfaceBinding",
                      self.socket_tests)
        for flag in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {flag} = false", self.release)
        self.assertFalse((MAIN.parent /
                          "wifidirect/DebugWifiDirectP2pInterface.kt").exists())
        for source in (self.probe, self.host, self.join):
            self.assertNotIn("DebugWifiDirectFutureQrProofSocketLab", source)
            self.assertNotIn("DebugWifiDirectFutureQrTlsExpectation", source)


if __name__ == "__main__":
    unittest.main()
