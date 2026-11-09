package com.edenpractical.comms.model

/**
 * Source of truth for the connection-method chooser.
 *
 * A method's presence in the product plan is NEVER proof that its Android
 * transport, discovery, permissions or authentication exist. M6-A gives
 * each method an entry to its already implemented, foreground-only DEBUG
 * test. A selected method is NOT a verified conversation or production right.
 * Production capability flags remain disabled regardless of UI selection.
 *
 * No scans, sockets, Android radio access, peer discovery or stored method
 * preference are performed by this side-effect-free catalog.
 */
internal object NearbyMethodCatalog {
    enum class Availability { DEBUG_TEST_ONLY, PLANNED }

    data class Method(
        val id: ConnectionMethod,
        val title: String,
        val description: String,
        val availability: Availability,
        val requirements: String,
        val futureDiscovery: String
    )

    val methods: List<Method> = listOf(
        Method(
            id = ConnectionMethod.LAN,
            title = "Same Wi-Fi",
            description = "Both phones use the same nearby Wi-Fi. Internet is not needed.",
            availability = Availability.DEBUG_TEST_ONLY,
            requirements = "Two QR scans, pinned TLS and both people's approvals.",
            futureDiscovery = "Currently uses in-person QR codes, not automatic device search."
        ),
        Method(
            id = ConnectionMethod.BLUETOOTH,
            title = "Bluetooth",
            description = "Nearby phone-to-phone test; no shared Wi-Fi router needed.",
            availability = Availability.DEBUG_TEST_ONLY,
            requirements = "Bluetooth permissions, fresh QR identity checks, TLS and both approvals.",
            futureDiscovery = "Only an opt-in foreground Bluetooth Classic debug session can test text and live voice. No production discovery."
        ),
        Method(
            id = ConnectionMethod.WIFI_DIRECT,
            title = "Wi-Fi Direct",
            description = "Direct Wi-Fi test without a shared router; both phones stay nearby.",
            availability = Availability.DEBUG_TEST_ONLY,
            requirements = "Device capability and permission preflight, host/join, fresh QR, TLS and both approvals.",
            futureDiscovery = "Only a temporary foreground Wi-Fi Direct debug group and authenticated test chat exist. Not production discovery."
        )
    )

    /**
     * Selects an existing debug entrypoint, NOT a connected/approved state.
     * Actual permissions, QR, radio formation, TLS and human consent remain
     * separately enforced in each transport activity. Release always refuses.
     */
    sealed interface Selection {
        data object LaunchDebugLanQr : Selection
        data object LaunchDebugBluetooth : Selection
        data object LaunchDebugWifiDirectPreflight : Selection
        data object NotAvailable : Selection
    }

    fun select(method: ConnectionMethod, debuggable: Boolean): Selection {
        if (!debuggable) return Selection.NotAvailable
        return when (method) {
            ConnectionMethod.LAN -> Selection.LaunchDebugLanQr
            ConnectionMethod.BLUETOOTH -> Selection.LaunchDebugBluetooth
            ConnectionMethod.WIFI_DIRECT -> Selection.LaunchDebugWifiDirectPreflight
        }
    }

    fun current(method: ConnectionMethod): Method =
        methods.first { it.id == method }

    fun canEnterDebugLan(debuggable: Boolean): Boolean =
        select(ConnectionMethod.LAN, debuggable) == Selection.LaunchDebugLanQr

    fun isDebugEntry(method: ConnectionMethod, debuggable: Boolean): Boolean =
        select(method, debuggable) != Selection.NotAvailable
}
