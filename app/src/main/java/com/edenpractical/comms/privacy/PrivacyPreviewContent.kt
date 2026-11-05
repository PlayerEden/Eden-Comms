package com.edenpractical.comms.privacy

/**
 * User-facing facts about the current DEVELOPMENT preview only.
 *
 * Not a Google Play Privacy Policy, legal statement or security certification.
 * Update alongside any permission, persistence, transport, SDK or user-data
 * change. Formal release policy needs a verified publisher contact and URL.
 */
internal object PrivacyPreviewContent {
    const val heading = "Privacy & data"
    const val status = "DEVELOPMENT PREVIEW"
    const val introduction =
        "Eden Comms currently offers a temporary local QR/TLS connection test " +
        "and a separate text-screen layout preview. Text typed into the preview " +
        "never leaves this phone. You cannot send your own messages or voice to " +
        "another person yet. Do not use this build for sensitive conversations."

    data class Section(val heading: String, val description: String)

    val sections = listOf(
        Section(
            "On your phone",
            "If you create a device identity, its private key remains in Android " +
            "Keystore until reset or removed. The app also remembers your light " +
            "or dark appearance setting. Fixed test phrases and local text-preview " +
            "bubbles are temporary in-memory UI content; the app does not " +
            "intentionally save a conversation history. Preview text clears on " +
            "End, when leaving that screen, and when the app backgrounds."
        ),
        Section(
            "Shared when pairing",
            "A QR code shows your local Wi-Fi address, a public certificate " +
            "fingerprint and a short-lived pairing value to the other phone. " +
            "During the experimental test, the phones also exchange a " +
            "standard TLS channel-binding value inside their encrypted local " +
            "connection, then approval controls and fixed phrase IDs. No " +
            "exported values are intentionally saved or uploaded to a server. " +
            "The other person can photograph or retain what appears on screen."
        ),
        Section(
            "Permissions",
            "Local network access lets Android open a nearby Wi-Fi connection " +
            "(INTERNET and ACCESS_NETWORK_STATE permissions). Camera access is " +
            "requested for an in-app QR scan. The current preview does not " +
            "request microphone, contacts, Bluetooth or location permissions."
        ),
        Section(
            "Typing into a local preview",
            "The text composer is only a screen demonstration, not a secure " +
            "chat. Eden Comms does not intentionally save or transmit that text, " +
            "but the keyboard, Android snapshots, screen recording or another " +
            "observer may retain what you type or display. Avoid secrets."
        ),
        Section(
            "What privacy cannot hide",
            "The Wi-Fi network and nearby observers may detect connected devices " +
            "and connection timing. Encryption does not protect a compromised " +
            "phone or stop another person copying what they see. The phone's " +
            "operating system can make its own network connections."
        ),
        Section(
            "What is not available",
            "There are no Eden Comms accounts, user-written messages exchanged " +
            "with another phone, saved conversations, voice calls, or remembered " +
            "peer connections. The text layout allows local typing only; " +
            "real messaging and voice require separate reviews before enabling."
        )
    )

    const val policyNote =
        "This page explains the current preview; it is not the published " +
        "privacy policy. A full policy with verified publisher contact details " +
        "and an accessible public website will be added before Google Play release."
}
