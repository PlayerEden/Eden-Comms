package com.edenpractical.comms.privacy

/**
 * Experimental debug-build privacy disclosures, NOT a Google Play policy,
 * remote erasure promise or security certification. Update along with every
 * new data flow. All production communication flags remain off.
 */
internal object PrivacyPreviewContent {
    const val heading = "Privacy & data"
    const val status = "DEVELOPMENT PREVIEW"
    const val introduction =
        "Eden Comms has experimental nearby two-phone test connections over " +
        "Same Wi-Fi, Bluetooth Classic and Wi-Fi Direct. Fresh in-person QR " +
        "checks and both phones approve and confirm before short typed TEST messages " +
        "or live push-to-talk can travel on the approved local TLS connection. " +
        "Debug LAN push-to-talk, Bluetooth and Wi-Fi Direct test voice are " +
        "unreviewed and voice is not a production feature. Nothing here is " +
        "accepted as secure private messaging; do not use secrets or rely " +
        "on this app for emergencies."

    data class Section(val heading: String, val description: String)

    val sections = listOf(
        Section(
            "On your phone",
            "If you choose to create a local identity, its private key is " +
                "managed in Android Keystore; your appearance setting is " +
                "saved on this phone. Every new test conversation starts " +
                "session-only, with short typed text in memory by default. " +
                "AFTER a verified session, a separate explicit per-conversation choice " +
                "can retain NEW text locally for 1 hour, 24 hours, 7, 14 or " +
                "30 days maximum. Debug history uses Keystore AES-GCM " +
                "in app-private no-backup storage; it does not intentionally " +
                "save microphone audio or another person's copy. Reading or " +
                "reconnecting does not extend the stored message expiry. " +
                "The optional remember an already verified peer choice stores " +
                "only the public peer fingerprint and bounded expiry (1–30 days) " +
                "in separate encrypted local storage. This is historical metadata, " +
                "not a login, approval or automatic reconnection. You can " +
                "delete saved local history and forget one or all remembered " +
                "phones. Both controls are available in verified Same Wi-Fi, " +
                "Bluetooth Classic and Wi-Fi Direct debug conversations. " +
                "File deletion is best effort, never guaranteed physical " +
                "erasure from flash, backups, the recipient or screenshots."
        ),
        Section(
            "Shared when pairing and chatting",
            "A fresh in-person QR exchange shares public certificate identity " +
                "information and short-lived proof values; Same Wi-Fi QR may " +
                "include a local Wi-Fi address needed by that connection. " +
                "Other methods use their own Android Bluetooth or Wi-Fi Direct " +
                "radio setup, not that Wi-Fi address. The phones must pass " +
                "pinned TLS channel-binding checks, current QR proof and " +
                "encrypted approval controls, with both people approving " +
                "before final confirmation. Only the current approved " +
                "encrypted test channel sends short typed test messages and " +
                "bounded live voice frames. The other phone receives those " +
                "bytes, but a local peer-app ACK is not proof the other person " +
                "read the message or heard the audio. No content is " +
                "intentionally uploaded to an Eden Comms server; another " +
                "person can photograph, copy or record their screen."
        ),
        Section(
            "Permissions",
            "Android INTERNET and ACCESS_NETWORK_STATE permit nearby Wi-Fi " +
                "communication; they do not require an Eden server. Camera " +
                "permission is requested for QR scanning. In DEBUG builds " +
                "only, Bluetooth SCAN/CONNECT/ADVERTISE access, nearby " +
                "Wi-Fi permission on newer Android, and location permission " +
                "on some older Android versions may be requested explicitly " +
                "for the selected radio method. Location Mode may be " +
                "needed for discovery, but the app does not intentionally " +
                "read or upload location coordinates. Microphone permission " +
                "is requested only when you choose to enable an approved " +
                "debug voice test; it is not needed merely to open Connect. " +
                "No background microphone, always-on radio discovery, " +
                "contacts access or automatic reconnection is required. " +
                "Bluetooth discovery exposes unverified nearby signals, " +
                "not trusted Eden Comms identities. Android Bluetooth " +
                "discoverability may continue for the OS-approved period " +
                "even after the app closes its socket. Experimental nearby " +
                "radio and voice permissions are absent from the release app."
        ),
        Section(
            "Local-only sandbox versus real test text",
            "The separate developer text-layout sandbox is local-only: " +
                "it does not intentionally save or transmit test messages. " +
                "The Home Text pathway instead starts Connect and can " +
                "exchange typed test messages after fresh QR checks, " +
                "mutual TLS and both approvals. Keyboard suggestions, " +
                "operating system snapshots, screenshots, screen recording " +
                "or an observer can still retain what you type or display."
        ),
        Section(
            "What privacy cannot hide",
            "The Wi-Fi network, Bluetooth radio and nearby observers may " +
                "detect device presence or connection timing. Encryption " +
                "does not protect a compromised phone or stop the other " +
                "person saving content. The operating system can make " +
                "its own network connections and may keep snapshots. " +
                "A Hide conversation privacy curtain is only a visual " +
                "shield, not encryption, remote deletion or a guarantee " +
                "of permanent erasure. Timed disappearing controls " +
                "affect local display/retention only, not the other phone."
        ),
        Section(
            "What is not available",
            "There are no Eden Comms accounts, company cloud backups, " +
                "automatic remembered peer connections or authorized " +
                "remote Internet relay. Group messaging, file transfer " +
                "and mesh networking are not part of this debug core. " +
                "Short in-memory disappearing timers and experimental " +
                "Bluetooth Classic, Same Wi-Fi and Wi-Fi Direct TEXT/ACK " +
                "and foreground live voice exist only after active " +
                "verified consent; they are not reviewed communication " +
                "services. Optional encrypted test text history and " +
                "public-peer memory never bypass fresh QR, TLS or " +
                "approvals. Production secure messaging remains disabled " +
                "pending independent protocol review, Android physical " +
                "two-phone radio/voice tests and privacy/accessibility QA. " +
                "Clearing this phone cannot remotely delete another " +
                "person's messages or recordings."
        )
    )

    const val policyNote =
        "This page explains the current preview; it is not the published " +
        "privacy policy. A full policy with verified publisher contact details " +
        "and an accessible public website will be added before Google Play release."
}
