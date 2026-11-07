package com.edenpractical.comms.wifidirect

/**
 * Read-only M3o preflight model; never interprets a device feature as a
 * discovered, connected, authenticated or consented peer.
 *
 * No Wi-Fi Direct API action, runtime permission request, socket, network
 * association, background radio operation or saved identifier is permitted.
 */
internal object DebugWifiDirectPreflightPolicy {
    enum class PermissionFamily {
        NEARBY_WIFI_DEVICES,
        ACCESS_FINE_LOCATION
    }

    data class Observation(
        val apiLevel: Int,
        val featureAdvertised: Boolean,
        val managerPresent: Boolean
    )

    enum class Support {
        NOT_ADVERTISED,
        MANAGER_MISSING,
        POTENTIALLY_SUPPORTED
    }

    data class Assessment(
        val support: Support,
        val permissionFamily: PermissionFamily,
        val discoveryAllowed: Boolean,
        val description: String
    )

    fun assess(value: Observation): Assessment {
        require(value.apiLevel >= 26) { "Below the app's minimum supported API" }
        val permission = if (value.apiLevel >= 33) {
            PermissionFamily.NEARBY_WIFI_DEVICES
        } else {
            PermissionFamily.ACCESS_FINE_LOCATION
        }
        val support = when {
            !value.featureAdvertised -> Support.NOT_ADVERTISED
            !value.managerPresent -> Support.MANAGER_MISSING
            else -> Support.POTENTIALLY_SUPPORTED
        }
        val description = when (support) {
            Support.NOT_ADVERTISED ->
                "This phone does not advertise Wi-Fi Direct support."
            Support.MANAGER_MISSING ->
                "Wi-Fi Direct is advertised, but the Android service was unavailable."
            Support.POTENTIALLY_SUPPORTED ->
                "This phone advertises Wi-Fi Direct and exposes its Android service. " +
                    "This does not prove Wi-Fi Direct is enabled or that another phone can connect."
        }
        // C1 read-only preflight has NO privilege to start discovery, regardless
        // of handset capabilities or the permission that a future test needs.
        return Assessment(support, permission, discoveryAllowed = false, description)
    }
}
