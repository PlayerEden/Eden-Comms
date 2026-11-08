package com.edenpractical.comms.wifidirect

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure JVM address/subnet policy permutations. Does NOT emulate Android
 * WifiP2pGroup, prove that the interface exists or authenticate any peer.
 */
class DebugWifiDirectP2pInterfaceTest {
    private fun ip(value: String) = InetAddress.getByName(value)
    private fun entry(value: String, prefix: Int = 24) =
        DebugWifiDirectP2pInterface.AddressCandidate(ip(value), prefix)

    @Test fun hostAcceptsRealLocalInterfaceWhenAndroidRedactsOwnerAddress() {
        val local = entry("192.168.49.1")
        assertEquals(local.address,
            DebugWifiDirectP2pInterface.select(true, null, listOf(local)))
        assertEquals(local.address,
            DebugWifiDirectP2pInterface.select(true, ip("0.0.0.0"), listOf(local)))
        assertEquals(local.address,
            DebugWifiDirectP2pInterface.select(true, local.address, listOf(local)))
        assertNull(DebugWifiDirectP2pInterface.select(
            true, ip("192.168.49.10"), listOf(local)))
    }

    @Test fun joinerMustHaveDifferentLocalIpInsideExactOwnerSubnet() {
        val joining = entry("192.168.49.24")
        assertEquals(joining.address, DebugWifiDirectP2pInterface.select(
            false, ip("192.168.49.1"), listOf(joining)))
        // Do not accidentally route to another local Wi-Fi or VPN subnet.
        assertNull(DebugWifiDirectP2pInterface.select(
            false, ip("192.168.49.1"), listOf(entry("192.168.50.24"))))
        assertNull(DebugWifiDirectP2pInterface.select(
            false, ip("192.168.49.1"), listOf(entry("192.168.49.1"))))
        assertNull(DebugWifiDirectP2pInterface.select(
            false, null, listOf(joining)))
        assertNull(DebugWifiDirectP2pInterface.select(
            false, ip("0.0.0.0"), listOf(joining)))
    }

    @Test fun subnetPrefixIsRespectedIncludingNonByteAlignedMasks() {
        assertEquals(ip("172.20.4.50"), DebugWifiDirectP2pInterface.select(
            false, ip("172.20.4.1"), listOf(entry("172.20.4.50", 23))))
        assertEquals(ip("172.20.5.50"), DebugWifiDirectP2pInterface.select(
            false, ip("172.20.4.1"), listOf(entry("172.20.5.50", 23))))
        assertNull(DebugWifiDirectP2pInterface.select(
            false, ip("172.20.6.1"), listOf(entry("172.20.4.50", 23))))
        assertNull(DebugWifiDirectP2pInterface.select(
            false, ip("172.20.5.1"), listOf(entry("172.20.4.50", 24))))
    }

    @Test fun ambiguousInvalidAndPublicInterfacesFailClosed() {
        val good = entry("192.168.49.23")
        assertNull(DebugWifiDirectP2pInterface.select(
            false, ip("192.168.49.1"), listOf(good, entry("192.168.49.24"))))
        assertNull(DebugWifiDirectP2pInterface.select(
            true, null, listOf(entry("192.168.49.1"), entry("10.20.30.1"))))
        for (candidate in listOf(
            entry("8.8.8.8"), entry("0.0.0.0"), entry("127.0.0.1"),
            entry("169.254.4.6"), entry("224.0.0.1"), entry("192.168.49.2", 0),
            entry("192.168.49.2", 31), entry("192.168.49.2", 32)
        )) {
            assertNull(DebugWifiDirectP2pInterface.select(
                true, null, listOf(candidate)))
            assertNull(DebugWifiDirectP2pInterface.select(
                false, ip("192.168.49.1"), listOf(candidate)))
        }
    }

    @Test fun lookupDoesNotDefaultToLoopbackOrUnknownInterfaceName() {
        assertNull(DebugWifiDirectP2pInterface.resolve(null, false, ip("192.168.49.1")))
        assertNull(DebugWifiDirectP2pInterface.resolve("", true, null))
        assertNull(DebugWifiDirectP2pInterface.resolve("lo", true, null))
        assertNull(DebugWifiDirectP2pInterface.resolve("invalid p2p name", true, null))
        assertNull(DebugWifiDirectP2pInterface.resolve(
            "ec_p2p_interface_which_does_not_exist", false, ip("192.168.49.1")))
    }
}
