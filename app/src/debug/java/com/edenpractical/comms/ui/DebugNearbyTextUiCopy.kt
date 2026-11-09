package com.edenpractical.comms.ui

import com.edenpractical.comms.pairing.DebugTextDraftPolicy

/**
 * Display-only labels for ACTUAL approved TLS text/ACK events.
 *
 * A queue acceptance is not a transport write; a transport write is not
 * delivery; an app ACK is not a human read receipt. No network, storage,
 * authentication or message authority belongs in this copy helper.
 */
internal object DebugNearbyTextUiCopy {
    const val MAX_CODE_POINTS = DebugTextDraftPolicy.MAX_CODE_POINTS

    fun acceptsDraft(text: String): Boolean = DebugTextDraftPolicy.accepts(text)

    fun canSubmit(text: String): Boolean = DebugTextDraftPolicy.maySend(text)

    fun lineStatus(outgoing: Boolean, acknowledged: Boolean): String = when {
        !outgoing -> "Received on this phone"
        acknowledged -> "Other app returned ACK — not a read receipt"
        else -> "Written to encrypted test link here — no peer-app ACK observed"
    }

    fun queueFeedback(accepted: Boolean): String =
        if (accepted) {
            "Queued on this phone for encrypted sending. Not yet a write or delivery receipt."
        } else {
            "Not queued. Draft kept. Check the connection or try again."
        }
}
