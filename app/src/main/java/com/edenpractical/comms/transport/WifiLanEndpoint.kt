package com.edenpractical.comms.transport

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.Inet4Address

/**
 * Find a Wi-Fi interface even when its router has NO WAN/internet access.
 * No location scan, DNS, cloud rendezvous or mobile-network fallback.
 */
internal object WifiLanEndpoint {
    data class Link(
        val network: Network,
        val address: Inet4Address,
        val prefixLength: Int
    )

    fun current(context: Context): Link? {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        return manager.allNetworks.asSequence().mapNotNull { network ->
            val capabilities = manager.getNetworkCapabilities(network) ?: return@mapNotNull null
            if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return@mapNotNull null
            }
            val properties = manager.getLinkProperties(network) ?: return@mapNotNull null
            properties.linkAddresses.firstNotNullOfOrNull { item ->
                val ipv4 = item.address as? Inet4Address
                if (ipv4 == null || ipv4.isLoopbackAddress || ipv4.isAnyLocalAddress ||
                    item.prefixLength !in 1..31
                ) null else Link(network, ipv4, item.prefixLength)
            }
        }.firstOrNull()
    }
}
