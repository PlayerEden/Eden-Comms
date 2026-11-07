package com.edenpractical.comms.model

/**
 * User-facing future-discovery explanation. This is NOT a discovery toggle,
 * Android runtime permission rationale screen or working scanner.
 *
 * Keep actual present-tense capabilities anchored to NearbyMethodCatalog.
 * Neither this policy nor its UI copy can authorize hardware operations.
 */
internal object NearbyDiscoveryPolicy {
    const val currentStatus =
        "Automatic Eden Comms peer discovery is OFF and NOT IMPLEMENTED. Developer Tools offers debug-only Bluetooth inquiry, anonymous signal selection, in-person two-QR preparation and a bounded pinned TLS 1.3 RFCOMM diagnostic. A debug-only TLS-encrypted QR/channel agreement and bilateral human APPROVE/DECLINE plus final confirmation experiment exists; it always closes the socket without Bluetooth chat or voice. Wi-Fi Direct now has a separate foreground 30-second debug-only radio discovery test that counts anonymous peer-list change events, but never reads peer identities or connects. Automatic trusted Eden Comms discovery remains OFF and NOT IMPLEMENTED in production. The production app has no Bluetooth or Wi-Fi Direct communication."

    const val optInStep =
        "First you will choose Bluetooth or Wi-Fi Direct and explicitly start a search."

    const val permissionStep =
        "The app will explain any required Android permission before requesting it. " +
        "Declining will keep discovery off."

    const val foregroundStep =
        "Search will run only while this screen is open, with a short time limit " +
        "and a visible Stop control. Leaving the screen ends it."

    const val trustStep =
        "Finding a nearby device will never make it a trusted contact. " +
        "Both people must separately verify and approve the new session."

    const val privacyStep =
        "No background scanning, public account directory, automatic reconnection, " +
        "or saved scan results are planned for the first version."

    fun availableNow(method: ConnectionMethod): Boolean = when (method) {
        ConnectionMethod.LAN,
        ConnectionMethod.BLUETOOTH,
        ConnectionMethod.WIFI_DIRECT -> false
    }
}
