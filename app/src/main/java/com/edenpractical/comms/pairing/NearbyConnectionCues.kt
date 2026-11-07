package com.edenpractical.comms.pairing

/**
 * Read-only, novice-facing explanation of the ACTUAL local pairing UI stage.
 * Never derives transport security, trust, remote consent, or message authority
 * from a timer, radio observation or cosmetic progress cue.
 */
internal object NearbyConnectionCues {
    data class Cue(val phase: String, val heading: String, val next: String)

    fun forStage(
        stage: String,
        localApprovalObserved: Boolean = false,
        peerApprovalObserved: Boolean = false,
        localConfirmationObserved: Boolean = false,
        peerConfirmationObserved: Boolean = false
    ): Cue? = when (stage) {
        "showHost" -> Cue(
            "STEP 1 OF 3 • EXCHANGE CODES",
            "Show this code to the other person",
            "They scan it on their phone. Then tap Scan their code."
        )
        "scanHost" -> Cue(
            "STEP 1 OF 3 • EXCHANGE CODES",
            "Scan the code on the other phone",
            "Point your camera at their screen. Nothing is approved yet."
        )
        "scanReply" -> Cue(
            "STEP 1 OF 3 • EXCHANGE CODES",
            "Scan the reply on the other phone",
            "They must show their second code. This starts a connection test, not chat."
        )
        "showReply" -> Cue(
            "STEP 1 OF 3 • EXCHANGE CODES",
            "Keep this code visible",
            "The first phone needs to scan it. Stay here while the local test continues."
        )
        "connecting" -> Cue(
            "STEP 2 OF 3 • CHECK THE LINK",
            "Checking the local test connection",
            "Keep both apps open on the same Wi-Fi. Neither person is approved yet."
        )
        "approval" -> when {
            localApprovalObserved && peerApprovalObserved &&
                (localConfirmationObserved || peerConfirmationObserved) -> Cue(
                "STEP 3 OF 3 • FINAL CONFIRMATION",
                "Waiting for both phones to finish",
                "At least one final confirmation was observed. Do not send anything yet."
            )
            localApprovalObserved && peerApprovalObserved -> Cue(
                "STEP 3 OF 3 • FINAL CONFIRMATION",
                "Both test approvals observed",
                "The phones must still complete the final checks before test chat is available."
            )
            localApprovalObserved -> Cue(
                "STEP 3 OF 3 • BOTH PEOPLE DECIDE",
                "Waiting for the other phone's approval",
                "Your test approval was observed here. The other person must approve separately."
            )
            peerApprovalObserved -> Cue(
                "STEP 3 OF 3 • BOTH PEOPLE DECIDE",
                "The other phone approved",
                "Its approval was observed here. You still need to decide whether to approve."
            )
            else -> Cue(
                "STEP 3 OF 3 • BOTH PEOPLE DECIDE",
                "Both people must approve",
                "Only approve if you know the person holding the other phone."
            )
        }
        "demo" -> Cue(
            "TEST CONVERSATION • ACTIVE",
            "Temporary test chat is available",
            "Test text can be sent here. This is not reviewed secure messaging."
        )
        // No cue on an idle or terminal screen: never visually imply a link
        // while merely checking local Wi-Fi, showing an error, or after Stop.
        else -> null
    }
}
