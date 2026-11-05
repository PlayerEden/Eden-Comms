package com.edenpractical.comms.transport

import java.net.Inet4Address
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LocalIpv4PolicyTest {
    private val me = LocalIpv4Policy.parseNumeric("192.168.20.12")

    @Test fun numericPrivateWifiPeerInSameSubnetIsAllowed() {
        val peer = LocalIpv4Policy.requireSamePrivateSubnet("192.168.20.99", me, 24)
        assertEquals("192.168.20.99", peer.hostAddress)
    }

    @Test fun neverPerformsHostnameResolution() {
        for (input in listOf("example.org", "localhost", "192.168.20.5.example", "192.168.20.4:42519")) {
            assertThrows(IllegalArgumentException::class.java) {
                LocalIpv4Policy.parseNumeric(input)
            }
        }
    }

    @Test fun rejectsInvalidIpv4BytesAndAmbiguousSyntax() {
        for (input in listOf("192.168.20.256", "192.168.20.-1", "192.168.20",
            "192.168.020.5", "192.168.20.3.1", "192.168.20.x")) {
            assertThrows(IllegalArgumentException::class.java) {
                LocalIpv4Policy.parseNumeric(input)
            }
        }
    }

    @Test fun rejectsOutsideLocalWifiSubnet() {
        for (input in listOf("192.168.21.8", "10.1.4.5", "8.8.8.8", "127.0.0.1",
            "192.168.20.12", "224.0.0.1")) {
            assertThrows(IllegalArgumentException::class.java) {
                LocalIpv4Policy.requireSamePrivateSubnet(input, me, 24)
            }
        }
    }

    @Test fun cidrBoundariesAreCorrect() {
        val local = LocalIpv4Policy.parseNumeric("10.8.12.10")
        assertEquals("10.8.12.80",
            LocalIpv4Policy.requireSamePrivateSubnet("10.8.12.80", local, 24).hostAddress)
        assertThrows(IllegalArgumentException::class.java) {
            LocalIpv4Policy.requireSamePrivateSubnet("10.8.13.5", local, 24)
        }
    }

    @Test fun impossibleSubnetPrefixCannotAllowAnyPeer() {
        for (prefix in listOf(-1, 0, 32, 33)) {
            assertThrows(IllegalArgumentException::class.java) {
                LocalIpv4Policy.requireSamePrivateSubnet("192.168.20.33", me, prefix)
            }
        }
    }
}
