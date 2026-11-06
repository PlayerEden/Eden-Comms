package com.edenpractical.comms.privacy

/**
 * Current development-build privacy disclosure, not a Google Play policy,
 * security certification, claim of remote erasure, or consent mechanism.
 *
 * Revise whenever the actual code gains a new data flow/retention pathway.
 */
internal object PrivacyPreviewContent {
    const val heading = "Privacy & data"
    const val status = "DEVELOPMENT PREVIEW"
    const val introduction =
        "Eden Comms has an experimental same-Wi-Fi, two-phone QR/TLS connection. " +
        "After BOTH phones approve and confirm, short typed TEST messages can " +
        "travel between the phones over the test connection. A separate " +
        "local-only text layout sandbox never transmits. This is unreviewed, " +
        "not physically accepted secure messaging. Voice is disabled. " +
        "Do not use the development build for sensitive conversations."

    data class Section(val heading: String, val description: String)

    val sections = listOf(
        Section(
            "On your phone",
            "If you explicitly create a device identity, its private key stays " +
                "in Android Keystore until reset or removal. The app also stores " +
                "your light or dark appearance setting. Short typed test chat " +
                "and fixed test phrases are temporary in-memory session content: " +
                "the app does not intentionally save a chat history. Ending, " +
                "leaving or backgrounding the session clears visible messages " +
                "and drafts as best effort. Clearing the chat screen is not " +
                "guaranteed erasure of device memory."
        ),
        Section(
            "Shared when pairing and chatting",
            "QR pairing exposes the local Wi-Fi address, public certificate " +
                "fingerprint and short-lived pairing value to the other phone. " +
                "The phones exchange TLS channel-binding proof and bilateral " +
                "approval controls inside the encrypted local connection. " +
                "Only after approvals and final confirmations can the DEBUG " +
                "test exchange fixed phrase IDs and short typed test messages. " +
                "The other phone receives the text; the other person can " +
                "photograph or retain copies. No chat content is intentionally " +
                "uploaded to an Eden Comms server."
        ),
        Section(
            "Permissions",
            "Local network access uses INTERNET and ACCESS_NETWORK_STATE " +
                "permissions for a nearby Wi-Fi connection. Camera permission " +
                "is requested for QR scanning. This preview does not request " +
                "microphone, Bluetooth, contacts or location permissions."
        ),
        Section(
            "Local-only sandbox versus real test text",
            "The developer Text layout sandbox does not intentionally save or " +
                "transmit that text. The Home Text pathway instead pairs phones " +
                "and can transmit typed test messages over a pinned TLS " +
                "connection. The keyboard, Android snapshots, screen recording " +
                "or an observer may retain what you type or display. Avoid secrets."
        ),
        Section(
            "What privacy cannot hide",
            "The Wi-Fi network and nearby observers may detect connected devices " +
                "and connection timing. Encryption does not protect a compromised " +
                "phone or stop another person copying what they see. The phone's " +
                "operating system can make its own network connections. " +
                "Hide conversation is a visual curtain, not encryption or deletion."
        ),
        Section(
            "What is not available",
            "There are no Eden Comms accounts, saved conversations, remembered " +
                "peer connections, encrypted on-device chat history, disappearing " +
                "timers, Bluetooth/Wi-Fi Direct transport or voice calls. " +
                "Production secure messaging remains disabled pending independent " +
                "protocol review and real two-phone tests. Clearing this phone " +
                "cannot remotely delete another person's messages."
        )
    )

    const val policyNote =
        "This page explains the current preview; it is not the published " +
        "privacy policy. A full policy with verified publisher contact details " +
        "and an accessible public website will be added before Google Play release."
}
