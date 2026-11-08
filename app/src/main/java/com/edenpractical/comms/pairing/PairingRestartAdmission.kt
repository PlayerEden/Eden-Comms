package com.edenpractical.comms.pairing

/**
 * M5-E: fail-closed admission for screen-only restart callbacks.
 *
 * This policy does not create/verify identities, QR codes, socket sessions or
 * message authority. The caller must still check Android foreground state and
 * the actual session's separate TLS/QR/two-person approval protections.
 */
internal object PairingRestartAdmission {
    /**
     * Local KeyStore creation/legacy upgrade may finish long after a user
     * initiated it. It is safe to retain the resulting local identity, but
     * never auto-open another QR flow after backgrounding, reset, or role swap.
     */
    fun mayContinueConsent(
        callbackAttempt: Int,
        activeAttempt: Int,
        requestedRole: String?,
        pendingRole: String?,
        visibleStage: String,
        foregroundResumed: Boolean
    ): Boolean =
        callbackAttempt == activeAttempt &&
            visibleStage == "choose" &&
            foregroundResumed &&
            (requestedRole == "show" || requestedRole == "scan") &&
            requestedRole == pendingRole

    /**
     * A camera listener from a removed/restarted scanner must not supply QR
     * metadata or failure messages to a new attempt, even if it uses the same
     * scanning stage. Generation and scanner revision must both match.
     */
    fun mayAcceptScan(
        callbackAttempt: Int,
        activeAttempt: Int,
        callbackRevision: Int,
        activeRevision: Int,
        visibleStage: String
    ): Boolean =
        callbackAttempt == activeAttempt &&
            callbackRevision == activeRevision &&
            (visibleStage == "scanHost" || visibleStage == "scanReply")
}
