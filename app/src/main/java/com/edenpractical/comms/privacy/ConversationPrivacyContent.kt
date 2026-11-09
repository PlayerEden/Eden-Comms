package com.edenpractical.comms.privacy

/**
 * Honest explanation of session-only DEFAULT debug chat and the F5-A
 * separately opt-in LOCAL encrypted history. The short RAM disappearing
 * timer remains independent and cannot remotely erase anything. Remembered
 * peers/trust remain unbuilt; no reviewed security assurance.
 */
internal object ConversationPrivacyContent {
    const val currentTitle = "Message history"
    const val currentState = "Session only • currently active"
    const val currentExplanation =
        "Test chat text stays in temporary app memory during the connection. " +
        "The app does not intentionally save a conversation history. " +
        "Disconnecting or leaving the app clears the visible session text."

    const val futureHistoryTitle = "Encrypted history"
    const val futureHistoryState = "Opt-in test • LAN debug only"
    const val futureHistoryExplanation =
        "After genuine bilateral LAN approval, you can explicitly save NEW " +
        "short test text in encrypted local device storage for 1 hour, 24 hours, " +
        "7, 14 or 30 days. Session-only is the default for every connection. " +
        "Viewing saved messages requires reconnecting to the verified peer, " +
        "and you can delete that phone's saved history. The feature is NOT " +
        "security-reviewed, never saves voice, and is unavailable in release."

    const val futureTimerTitle = "Disappearing messages"
    const val futureTimerState = "Available in nearby test chat"
    const val futureTimerExplanation =
        "In an active test chat, you can choose Off, 1, 5 or 15 minutes. " +
        "The timer removes visible messages from this phone only; it does " +
        "not tell the other phone to delete anything or erase Android copies. " +
        "The choice resets when the connection ends and is never saved."

    const val futureTrustTitle = "Remember nearby devices"
    const val futureTrustState = "Not available yet"
    const val futureTrustExplanation =
        "The app can keep its own Android Keystore identity, but it does not " +
        "save an approved peer as a trusted contact. Any future peer " +
        "remembering must be separate from message history."

    const val curtainExplanation =
        "Hide conversation conceals this screen's messages and editor without " +
        "disconnecting or deleting anything. Incoming test text can continue " +
        "to arrive until you disconnect."

    const val clearTitle = "Clear messages on this phone?"
    const val clearExplanation =
        "Remove only the live visible messages and unsent draft from this phone. " +
        "If you explicitly saved encrypted local history, this button DOES " +
        "NOT erase that history; use Delete saved history separately. " +
        "The connection stays active. Nothing deletes the other phone's " +
        "copies, keyboard history or screenshots."

    const val privacyLimits =
        "Your keyboard, Android snapshots, memory and the other person's " +
        "phone may still hold copies. This is not secure erasure. " +
        "Do not use the unreviewed debug build for sensitive conversations."
}
