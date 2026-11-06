package com.edenpractical.comms.model

/**
 * Source of truth for the connection-method chooser.
 *
 * A method's presence in the product plan is NEVER proof that its Android
 * transport, discovery, permissions or authentication exist. Only LAN's
 * existing foreground-only, QR-pinned, *debug* test may be entered today.
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
            description = "A future nearby phone-to-phone option without a shared router.",
            availability = Availability.PLANNED,
            requirements = "Future device support, explicit permission and fresh verification.",
            futureDiscovery = "Discovery and radio permission prompts are not implemented."
        ),
        Method(
            id = ConnectionMethod.WIFI_DIRECT,
            title = "Wi-Fi Direct",
            description = "A future direct Wi-Fi link without joining the same router.",
            availability = Availability.PLANNED,
            requirements = "Future Wi-Fi Direct setup, explicit permission and fresh verification.",
            futureDiscovery = "Discovery and peer-to-peer connection are not implemented."
        )
    )

    /**
     * UI can enter only the existing debug LAN QR flow. A future method must
     * not become actionable by simply adding its name or a misleading READY
     * flag to a UI. No production sending follows from this result.
     */
    sealed interface Selection {
        data object LaunchDebugLanQr : Selection
        data object NotAvailable : Selection
    }

    fun select(method: ConnectionMethod, debuggable: Boolean): Selection =
        if (debuggable && method == ConnectionMethod.LAN) Selection.LaunchDebugLanQr
        else Selection.NotAvailable

    fun current(method: ConnectionMethod): Method =
        methods.first { it.id == method }

    fun canEnterDebugLan(debuggable: Boolean): Boolean =
        select(ConnectionMethod.LAN, debuggable) == Selection.LaunchDebugLanQr
}
