package com.edenpractical.comms.privacy

/**
 * Explicitly requested local help, never a consent decision or trust marker.
 * These words describe current debug-only behavior and future intent separately.
 */
internal object ContextualPrivacyHelp {
    const val PAIRING_TITLE = "Why two QR codes?"
    const val PAIRING_BODY =
        "The first phone displays an invitation, then the second phone returns its own " +
        "code. Scanning both lets the experimental test verify which two device " +
        "certificates and fresh session values are expected before the local TLS check. " +
        "One scanned QR by itself is not enough to verify both phones. " +
        "This is development testing, not approval for real messaging or voice."

    const val SESSION_ONLY_TITLE = "What does session-only mean?"
    const val SESSION_ONLY_BODY =
        "Today's development build can exchange short typed test messages over " +
        "a QR-pinned TLS connection only after both phones approve and confirm. " +
        "It does not save a chat history. The current session-only mode keeps " +
        "conversation text in temporary app memory and clears the screen on " +
        "disconnect or background. An optional encrypted local history feature " +
        "is planned but unavailable. Your Android Keystore device key may " +
        "persist separately from chat content. Another person can still " +
        "record or photograph a conversation; local deletion cannot erase their copy. " +
        "Device memory, keyboards and screenshots may also retain text."
}
