package com.edenpractical.comms.pairing

/**
 * Read-only navigation and link-status wording for the local debug QR flow.
 *
 * Status is derived from the actual screen stage, never elapsed time or
 * inferred peer authorization. No sockets, credentials, saved routes or
 * messaging capability are owned here.
 */
internal object PairingSessionUi {
    fun needsLeaveWarning(stage: String): Boolean = when (stage) {
        "showHost", "scanHost", "showReply", "scanReply",
        "connecting", "approval", "demo" -> true
        else -> false
    }

    fun statusLabel(stage: String): String = when (stage) {
        "checking" -> "CHECKING THIS PHONE"
        "choose", "identityError" -> "NOT CONNECTED"
        "showHost", "scanHost", "showReply", "scanReply" -> "EXCHANGING QR CODES"
        "connecting" -> "CHECKING TEST LINK"
        "approval" -> "APPROVAL AND CONFIRMATION PENDING"
        "demo" -> "TEMPORARY TEST CHAT ACTIVE"
        "result" -> "TEST LINK ENDED"
        else -> "NOT CONNECTED"
    }

    fun leaveExplanation(stage: String): String =
        if (stage == "demo") {
            "Leaving closes this temporary development chat on this phone. " +
                "Its visible messages and draft are cleared as best effort; " +
                "the other phone may still retain copies. To talk again, both " +
                "people must start a fresh QR pairing. Stay to keep testing."
        } else {
            "Leaving cancels the current QR or local connection attempt " +
                "and closes its test socket, if open. Codes and approvals " +
                "cannot be resumed; both phones must start again with fresh " +
                "QR codes. Stay to continue setting up."
        }
}
