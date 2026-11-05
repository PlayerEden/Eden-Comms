package com.edenpractical.comms.chat

/**
 * User-facing meanings for the planned secure 1:1 text experience.
 *
 * These are presentation *definitions*, NOT live transport state or proof.
 * Only LOCAL_PREVIEW can appear on real chat entries in the current APK.
 * A future trusted adapter will need separate reviewed message evidence
 * before any other delivery state can be populated from an actual event.
 */
internal object ConversationStatusGuide {
    enum class Step(
        val label: String,
        val explanation: String,
        val availableToday: Boolean
    ) {
        LOCAL_PREVIEW(
            "Local preview • not sent",
            "Text is displayed on this phone only. No connection or delivery is involved.",
            true
        ),
        AWAITING_APPROVAL(
            "Waiting for connection approval",
            "Future only: both people must explicitly approve a verified peer and session before sending.",
            false
        ),
        QUEUED(
            "Waiting to send",
            "Future only: the app accepted a message locally; it has not written it to a connection.",
            false
        ),
        WRITTEN_TO_CHANNEL(
            "Written to encrypted connection",
            "Future only: an authenticated transport completed its local write. This does not prove the other app received it.",
            false
        ),
        PEER_APP_ACKNOWLEDGED(
            "Other app acknowledged receipt",
            "Future only: requires a fresh authenticated acknowledgment from the intended peer app for this exact message and session. It never means the person read it.",
            false
        ),
        FAILED(
            "Could not deliver",
            "Future only: the attempt ended or failed. A new send must never happen silently.",
            false
        ),
        SESSION_ENDED(
            "Conversation ended",
            "The session is closed; temporary content should be cleared locally. Other people may still have copies.",
            false
        )
    }

    /** Pre-release UI may show future definitions as *help*, never as real receipts. */
    val currentLiveSteps: Set<Step> = setOf(Step.LOCAL_PREVIEW)
    val futureSteps: List<Step> = Step.values().filterNot { it.availableToday }

    fun mayShowAsActualStatus(step: Step): Boolean = step in currentLiveSteps
}
