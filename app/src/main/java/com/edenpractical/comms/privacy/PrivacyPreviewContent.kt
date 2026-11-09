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
        "After both phones approve and confirm, short typed TEST messages can " +
        "travel between the phones over the test connection. A separate " +
        "local-only text layout sandbox never transmits. This is unreviewed, " +
        "not physically accepted secure messaging. Debug LAN push-to-talk " +
        "is experimental and voice is not a production feature. " +
        "Do not use the development build for sensitive conversations."

    data class Section(val heading: String, val description: String)

    val sections = listOf(
        Section(
            "On your phone",
            "If you explicitly create a device identity, its private key stays " +
                "in Android Keystore until reset or removal. The app also stores " +
                "your light or dark appearance setting. Short typed test chat " +
                "and fixed test phrases are temporary in-memory session content " +
                "by default. In debug LAN chat ONLY, a separate explicit " +
                "per-conversation choice can save up to 30 days of message " +
                "ciphertext in app-private no-backup storage using Android " +
                "Keystore AES-GCM. The option resets to session-only every " +
                "new connection. Leaving clears the live view but does not " +
                "delete expressly saved history. Use Delete saved history " +
                "for that verified person. File deletion cannot guarantee " +
                "physical erasure of Android/flash, recipient copies or screenshots."
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
                "is requested for QR scanning. Debug builds also declare " +
                "Nearby devices permissions BLUETOOTH_SCAN for explicitly started " +
                "Classic inquiry, BLUETOOTH_CONNECT for a separate unverified " +
                "RFCOMM socket test, and BLUETOOTH_ADVERTISE for a user-approved " +
                "Android discoverability request under Developer Tools. " +
                "These are not requested merely by opening the main app. " +
                "The inquiry " +
                "counts unverified radio observations and temporarily keeps " +
                "up to 12 opaque discovered-device objects in memory for " +
                "numbered, unverified local selection. It reads no device names " +
                "or addresses and saves no peer history. A separate user-guided two-QR " +
                "exchange can prepare public device certificate pins/nonces in " +
                "RAM; the scanned Bluetooth signal is NOT authenticated by these codes. " +
                "A separate explicit host/join " +
                "test can open a Bluetooth socket, require pinned TLS 1.3, " +
                "mutual QR proof, exporter/commitment and bilateral approval, " +
                "and exchange short debug encrypted TEXT/ACK after both " +
                "confirmations. Wi-Fi Direct also has experimental verified " +
                "group/socket/TLS text. Neither path has independently passed " +
                "two-phone or cryptographic review. There is NO saved peer " +
                "trust, Bluetooth voice, or production messaging permission. " +
                "This test is not an independently reviewed secure messenger. " +
                "Android may continue device discoverability for its approved " +
                "duration after a socket closes. All these tools are absent " +
                "from release builds. " +
                "The release app does not request microphone, contacts or " +
                "device location permission. Debug Wi-Fi Direct tests may " +
                "request nearby Wi-Fi permission on Android 13+ or location " +
                "permission on older Android versions; no coordinates are " +
                "read. Location Mode may be required for nearby discovery."
        ),
        Section(
            "Local-only sandbox versus real test text",
            "The developer Text layout sandbox is local-only and does not intentionally " +
                "save or transmit that text. The Home Text pathway instead pairs phones " +
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
            "There are no Eden Comms accounts, server backups or remembered " +
                "peer connections. An explicit DEBUG LAN-only history option " +
                "can retain locally encrypted test text for at most 30 days; " +
                "the default remains session-only. Short in-memory disappearing " +
                "timers and debug LAN PTT exist; Bluetooth and Wi-Fi Direct " +
                "text are experimental debug paths, not reviewed services. " +
                "Bluetooth and Wi-Fi Direct voice remain unavailable. " +
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
