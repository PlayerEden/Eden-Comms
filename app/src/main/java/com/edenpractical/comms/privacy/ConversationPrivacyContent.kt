package com.edenpractical.comms.privacy

/**
 * Honest, non-interactive status for the current development chat.
 *
 * No retention choice here authorizes storing any messages or peers.
 * Storage/timer options become real controls ONLY after encrypted storage,
 * expiry filtering, deletion and on-device privacy QA are implemented.
 */
internal object ConversationPrivacyContent {
    const val currentTitle = "Message history"
    const val currentState = "Session only • currently active"
    const val currentExplanation =
        "Test chat text stays in temporary app memory during the connection. " +
        "The app does not intentionally save a conversation history. " +
        "Disconnecting or leaving the app clears the visible session text."

    const val futureHistoryTitle = "Encrypted history"
    const val futureHistoryState = "Not available yet"
    const val futureHistoryExplanation =
        "Future per-conversation opt-in may keep encrypted messages on this " +
        "phone for up to 30 days. There is no saving switch in this build."

    const val futureTimerTitle = "Disappearing messages"
    const val futureTimerState = "Not available yet"
    const val futureTimerExplanation =
        "Future timers would control local copies on each person's device. " +
        "No timer, remote deletion or guaranteed erasure is active today."

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
        "Remove visible test messages and the unsent draft from this phone. " +
        "The connection stays active, so newly arriving or pending test " +
        "messages may appear afterward. This cannot delete another person's " +
        "copies, keyboard history or screenshots."

    const val privacyLimits =
        "Your keyboard, Android snapshots, memory and the other person's " +
        "phone may still hold copies. This is not secure erasure. " +
        "Do not use the unreviewed debug build for sensitive conversations."
}
