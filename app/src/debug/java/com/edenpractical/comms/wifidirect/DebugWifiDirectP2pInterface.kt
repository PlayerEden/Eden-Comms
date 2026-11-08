package com.edenpractical.comms.wifidirect

import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException

/**
 * F3-B3-A: DEBUG-ONLY selection of a local IPv4 address from the exact P2P
 * interface name reported by Android's WifiP2pGroup.getInterface().
 *
 * The host's WifiP2pInfo.groupOwnerAddress may be anonymized by Android.
 * Never guess a fixed 192.168.49.x address or use a default Wi-Fi/cellular
 * interface as fallback. No SSID, MAC, passphrase, IP, interface name or
 * peer identity is shown, logged, persisted or encoded into a QR.
 *
 * A valid local interface is NOT proof of the intended peer, QR identity,
 * private routing, human approval or an authorized communication session.
 */
internal object DebugWifiDirectP2pInterface {
    data class AddressCandidate(val address: InetAddress, val prefixLength: Int)

    /**
     * Pure policy for JVM tests. Multiple eligible IPv4 addresses are treated
     * as ambiguous, not silently selected. Prefix lengths are from the OS
     * interface, never inferred from the owner's address.
     */
    fun select(
        isHost: Boolean,
        groupOwnerAddress: InetAddress?,
        entries: List<AddressCandidate>
    ): InetAddress? {
        val viable = entries.filter { item ->
            val local = item.address
            val valid = DebugWifiDirectTcpProbe.testAddressAllowed(local) &&
                item.prefixLength in 8..30
            if (!valid) return@filter false
            if (isHost) {
                // Android may anonymize the owner address on the host.
                // A reported concrete address must agree with this exact
                // group interface, while null/wildcard may be redacted.
                groupOwnerAddress == null ||
                    groupOwnerAddress.isAnyLocalAddress ||
                    groupOwnerAddress == local
            } else {
                val owner = groupOwnerAddress
                owner != null &&
                    DebugWifiDirectTcpProbe.testAddressAllowed(owner) &&
                    owner != local &&
                    sameNetwork(local, owner, item.prefixLength)
            }
        }
        return viable.singleOrNull()?.address
    }

    private fun sameNetwork(a: InetAddress, b: InetAddress, prefix: Int): Boolean {
        if (a.address.size != 4 || b.address.size != 4 || prefix !in 8..30) return false
        val first = a.address
        val second = b.address
        for (index in first.indices) {
            val bits = (prefix - index * 8).coerceIn(0, 8)
            val mask = if (bits == 0) 0 else (0xff shl (8 - bits)) and 0xff
            if ((first[index].toInt() and mask) != (second[index].toInt() and mask))
                return false
        }
        return true
    }

    /**
     * Platform lookup ONLY for OS-reported WifiP2pGroup.interface. Never
     * enumerate other interfaces or fall back when this one is absent.
     */
    fun resolve(
        osGroupInterfaceName: String?,
        isHost: Boolean,
        groupOwnerAddress: InetAddress?
    ): InetAddress? {
        if (osGroupInterfaceName.isNullOrBlank() ||
            osGroupInterfaceName.length > 128 ||
            osGroupInterfaceName.any { it.isWhitespace() }
        ) return null
        return try {
            val device = NetworkInterface.getByName(osGroupInterfaceName) ?: return null
            if (!device.isUp || device.isLoopback) return null
            val entries = device.interfaceAddresses.mapNotNull { assigned ->
                val ip = assigned.address ?: return@mapNotNull null
                AddressCandidate(ip, assigned.networkPrefixLength.toInt())
            }
            select(isHost, groupOwnerAddress, entries)
        } catch (_: SocketException) {
            null
        } catch (_: SecurityException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }
}
