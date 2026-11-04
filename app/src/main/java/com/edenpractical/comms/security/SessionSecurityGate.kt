package com.edenpractical.comms.security

/**
 * UI / application *permission gate*, not a cryptographic protocol or proof.
 *
 * A future reviewed encrypted transport MUST authenticate the handshake and
 * transcript binding before it calls [authenticatedChannelEstablished].
 * No network or message transport is wired to this class in the current APK.
 */
enum class SessionPhase {
    DISCONNECTED,
    NEGOTIATING,
    AWAITING_BOTH_CONFIRMATIONS,
    VERIFIED,
    BLOCKED
}

class SessionSecurityGate {
    var phase: SessionPhase = SessionPhase.DISCONNECTED
        private set

    private var expectedPeer: String? = null
    private var authenticatedTranscriptId: String? = null
    private var confirmedLocal: Boolean = false
    private var confirmedRemote: Boolean = false

    val maySendMessage: Boolean
        get() = phase == SessionPhase.VERIFIED

    val mayTransmitAudio: Boolean
        get() = phase == SessionPhase.VERIFIED

    /**
     * Select a particular peer; never silently reuse previous session trust.
     */
    fun begin(peerIdentity: String): Boolean {
        if (phase != SessionPhase.DISCONNECTED || peerIdentity.isBlank()) return false
        expectedPeer = peerIdentity
        phase = SessionPhase.NEGOTIATING
        return true
    }

    /**
     * This event must ONLY originate from the future audited crypto adapter
     * AFTER its authenticated handshake succeeds. This gate cannot verify
     * the authenticity of [transcriptId] itself.
     */
    fun authenticatedChannelEstablished(peerIdentity: String, transcriptId: String): Boolean {
        if (phase != SessionPhase.NEGOTIATING ||
            expectedPeer != peerIdentity ||
            transcriptId.isBlank()
        ) {
            block()
            return false
        }
        authenticatedTranscriptId = transcriptId
        confirmedLocal = false
        confirmedRemote = false
        phase = SessionPhase.AWAITING_BOTH_CONFIRMATIONS
        return true
    }

    fun localCodeConfirmed(transcriptId: String): Boolean =
        confirm(transcriptId, isLocal = true)

    /**
     * The future crypto adapter must authenticate remote confirmation as
     * belonging to the same peer and secure session transcript.
     */
    fun remoteCodeConfirmed(transcriptId: String): Boolean =
        confirm(transcriptId, isLocal = false)

    private fun confirm(transcriptId: String, isLocal: Boolean): Boolean {
        if (phase != SessionPhase.AWAITING_BOTH_CONFIRMATIONS ||
            authenticatedTranscriptId == null ||
            authenticatedTranscriptId != transcriptId
        ) {
            block()
            return false
        }
        if (isLocal) confirmedLocal = true else confirmedRemote = true
        if (confirmedLocal && confirmedRemote) phase = SessionPhase.VERIFIED
        return true
    }

    /**
     * There is no automatic restoration of trust on a reconnect or transport
     * switch; the next session must repeat security requirements.
     */
    fun transportLost() = endSession()
    fun peerIdentityChanged() = block()
    fun authenticationFailed() = block()

    fun endSession() {
        clearSensitiveBindings()
        phase = SessionPhase.DISCONNECTED
    }

    private fun block() {
        clearSensitiveBindings()
        phase = SessionPhase.BLOCKED
    }

    private fun clearSensitiveBindings() {
        expectedPeer = null
        authenticatedTranscriptId = null
        confirmedLocal = false
        confirmedRemote = false
    }
}
