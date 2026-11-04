package com.edenpractical.comms.model

/**
 * Explicitly distinguishes the M0 foundation UI from a functioning encrypted
 * communications client. This must not be flipped until end-to-end tests pass.
 */
object ReleaseCapabilities {
    const val CAN_CONNECT = false
    const val CAN_SEND_MESSAGES = false
    const val CAN_TRANSMIT_VOICE = false
}

enum class ConnectionMethod(
    val displayName: String,
    val simpleDescription: String
) {
    LAN("Local Wi-Fi", "Connect through the same nearby Wi-Fi network."),
    BLUETOOTH("Bluetooth", "Communicate directly with nearby phones."),
    WIFI_DIRECT("Wi-Fi Direct", "Connect directly using Wi-Fi, without a router.")
}
