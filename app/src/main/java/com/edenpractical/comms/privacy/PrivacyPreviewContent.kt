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
        "Eden Comms currently offers a local, temporary QR and encrypted-link " +
        "test. You cannot send your own messages or voice yet. Do not use " +
        "this experimental build for sensitive conversations."

    data class Section(val heading: String, val description: String)

    val sections = listOf(
        Section(
            "On your phone",
            "If you create a device identity, its private key remains in Android " +
            "Keystore until reset or removed. The app also remembers your light " +
            "or dark appearance setting. Test phrases and counters are temporary; " +
            "the app does not save a conversation history."
        ),
        Section(
            "Shared when pairing",
            "A QR code shows your local Wi-Fi address, a public certificate " +
            "fingerprint and a short-lived pairing value to the other phone. " +
            "The two phones exchange test control packets and fixed phrase IDs " +
            "over the local connection. The other person can photograph or " +
            "retain what appears on either screen."
        ),
        Section(
            "Permissions",
            "Local network access lets Android open a nearby Wi-Fi connection " +
            "(INTERNET and ACCESS_NETWORK_STATE permissions). Camera access is " +
            "requested for an in-app QR scan. The current preview does not " +
            "request microphone, contacts, Bluetooth or location permissions."
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
            "There are no Eden Comms accounts, user-written messages, " +
            "saved conversations, voice calls, or remembered peer connections " +
            "in this preview. These features must receive separate reviews " +
            "before they are added."
        )
    )

    const val policyNote =
        "This page explains the current preview; it is not the published " +
        "privacy policy. A full policy with verified publisher contact details " +
        "and an accessible public website will be added before Google Play release."
}
