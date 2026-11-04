package com.edenpractical.comms.transport

import java.net.Inet4Address
import java.net.InetAddress

/**
 * Pure local IPv4 policy: numeric input only (never DNS) and only peers on
 * the current Wi-Fi interface's subnet. Remote networks are not in scope.
 */
internal object LocalIpv4Policy {
    fun parseNumeric(address: String): Inet4Address {
        val groups = address.trim().split('.')
        require(groups.size == 4) { "Enter four IPv4 numbers" }
        val parts = groups.map { part ->
            require(part.isNotEmpty() && part.length <= 3 &&
                part.all { it in '0'..'9' } &&
                (part.length == 1 || part[0] != '0')) {
                "Numeric IPv4 address required (no hostnames)"
            }
            val n = part.toIntOrNull()
            require(n != null && n in 0..255) { "Invalid IPv4 byte" }
            n
        }
        return InetAddress.getByAddress(parts.map(Int::toByte).toByteArray()) as Inet4Address
    }

    fun requireSamePrivateSubnet(
        host: String,
        local: Inet4Address,
        prefixLength: Int
    ): Inet4Address {
        require(prefixLength in 1..31) { "Unsupported Wi-Fi subnet" }
        val peer = parseNumeric(host)
        require(peer.isSiteLocalAddress || peer.isLinkLocalAddress) {
            "Only local/private Wi-Fi addresses are permitted"
        }
        require(peer != local && !peer.isAnyLocalAddress && !peer.isLoopbackAddress &&
            !peer.isMulticastAddress) { "Not a remote Wi-Fi peer" }
        val shift = 32 - prefixLength
        val a = toInt(local)
        val b = toInt(peer)
        require((a ushr shift) == (b ushr shift)) {
            "The other phone must be on this Wi-Fi subnet"
        }
        return peer
    }

    private fun toInt(address: Inet4Address): Int =
        address.address.fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) }
}
