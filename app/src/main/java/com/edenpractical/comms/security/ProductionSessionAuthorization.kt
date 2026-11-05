package com.edenpractical.comms.security

import com.edenpractical.comms.model.ReleaseCapabilities
import java.security.MessageDigest

/**
 * An in-memory authorization POLICY and lifecycle model, NOT a cryptographic
 * verifier. Only a future independently-reviewed secure-channel adapter may
 * report peer authentication and an authenticated remote approval.
 *
 * Intentionally NOT connected to LanDemoSession, the UI, or a send/audio API.
 * Production capability flags remain off regardless of phase.
 *
 * An attempt ID is an event-ordering marker, NOT a secret or an auth token.
 */
internal class ProductionSessionAuthorization {
    internal data class AttemptId internal constructor(val generation: Long)

    enum class Phase {
        DISCONNECTED, AWAITING_AUTHENTICATED_CHANNEL,
        AWAITING_BOTH_APPROVALS, APPROVALS_COMPLETE, BLOCKED
    }

    private var currentPhase = Phase.DISCONNECTED
    private var nextGeneration = 0L
    private var currentAttempt: AttemptId? = null
    private var expectedPeerPin: ByteArray? = null
    private var transcriptDigest: ByteArray? = null
    private var localApproval = false
    private var authenticatedRemoteApproval = false

    @get:Synchronized
    val phase: Phase get() = currentPhase

    /**
     * Report only that policy prerequisites completed. This does NOT mean
     * end-to-end security is proven or that production traffic is permitted.
     */
    @get:Synchronized
    val approvalModelComplete: Boolean
        get() = currentPhase == Phase.APPROVALS_COMPLETE

    @get:Synchronized
    val maySendProductionText: Boolean
        get() = ReleaseCapabilities.CAN_CONNECT &&
            ReleaseCapabilities.CAN_SEND_MESSAGES &&
            currentPhase == Phase.APPROVALS_COMPLETE

    @get:Synchronized
    val mayTransmitProductionVoice: Boolean
        get() = ReleaseCapabilities.CAN_CONNECT &&
            ReleaseCapabilities.CAN_TRANSMIT_VOICE &&
            currentPhase == Phase.APPROVALS_COMPLETE

    /**
     * The caller supplies public SHA-256 certificate pins obtained from fresh,
     * explicitly consented, in-person peer selection. NO device discovery alone.
     */
    @Synchronized
    fun begin(localPinSha256: ByteArray, expectedPeerPinSha256: ByteArray): AttemptId? {
        if (currentPhase != Phase.DISCONNECTED) return null
        if (!validDigest(localPinSha256) || !validDigest(expectedPeerPinSha256) ||
            MessageDigest.isEqual(localPinSha256, expectedPeerPinSha256) ||
            nextGeneration == Long.MAX_VALUE
        ) {
            block()
            return null
        }
        nextGeneration++
        val id = AttemptId(nextGeneration)
        currentAttempt = id
        expectedPeerPin = expectedPeerPinSha256.copyOf()
        currentPhase = Phase.AWAITING_AUTHENTICATED_CHANNEL
        return id
    }

    /**
     * Model notification ONLY: future independent adapter must first verify
     * mutual TLS 1.3, exact certificate pins, peer private-key possession,
     * QR/session binding, freshness and a channel-specific transcript hash.
     * This function cannot independently establish any of those properties.
     */
    @Synchronized
    fun onAuthenticatedChannel(
        attempt: AttemptId,
        authenticatedPeerPinSha256: ByteArray,
        channelTranscriptSha256: ByteArray
    ): Boolean {
        if (attempt != currentAttempt) return false // stale event; do not poison new attempt
        if (currentPhase != Phase.AWAITING_AUTHENTICATED_CHANNEL ||
            !validDigest(channelTranscriptSha256) ||
            !validDigest(authenticatedPeerPinSha256) ||
            expectedPeerPin?.let {
                MessageDigest.isEqual(it, authenticatedPeerPinSha256)
            } != true
        ) {
            block()
            return false
        }
        transcriptDigest = channelTranscriptSha256.copyOf()
        localApproval = false
        authenticatedRemoteApproval = false
        currentPhase = Phase.AWAITING_BOTH_APPROVALS
        return true
    }

    /** Explicit approval by the LOCAL human for this exact channel transcript. */
    @Synchronized
    fun localApproved(attempt: AttemptId, channelTranscriptSha256: ByteArray): Boolean =
        approve(attempt, channelTranscriptSha256, remoteAuthenticated = false)

    /**
     * Must be invoked ONLY after the independently reviewed adapter authenticates
     * the OTHER person's approval over the SAME verified, live TLS channel.
     * A UI event or an untrusted network packet MUST NEVER call this directly.
     */
    @Synchronized
    fun onAuthenticatedRemoteApproval(
        attempt: AttemptId,
        channelTranscriptSha256: ByteArray
    ): Boolean = approve(attempt, channelTranscriptSha256, remoteAuthenticated = true)

    private fun approve(
        attempt: AttemptId,
        channelTranscriptSha256: ByteArray,
        remoteAuthenticated: Boolean
    ): Boolean {
        if (attempt != currentAttempt) return false
        if (currentPhase != Phase.AWAITING_BOTH_APPROVALS ||
            !validDigest(channelTranscriptSha256) ||
            transcriptDigest?.let {
                MessageDigest.isEqual(it, channelTranscriptSha256)
            } != true
        ) {
            block()
            return false
        }
        if (remoteAuthenticated) authenticatedRemoteApproval = true
        else localApproval = true
        if (localApproval && authenticatedRemoteApproval)
            currentPhase = Phase.APPROVALS_COMPLETE
        return true
    }

    /** A stale previous connection may not revoke or authorize the new one. */
    @Synchronized
    fun transportLost(attempt: AttemptId) {
        if (attempt == currentAttempt) endSession()
    }

    @Synchronized
    fun authenticatedPeerChanged(attempt: AttemptId) {
        if (attempt == currentAttempt) block()
    }

    @Synchronized
    fun authenticationFailed(attempt: AttemptId) {
        if (attempt == currentAttempt) block()
    }

    /** App pause, user Cancel, or navigation away closes all session permissions. */
    @Synchronized
    fun endSession() {
        clear()
        currentPhase = Phase.DISCONNECTED
    }

    private fun validDigest(value: ByteArray): Boolean =
        value.size == 32 && value.any { it.toInt() != 0 }

    private fun block() {
        clear()
        currentPhase = Phase.BLOCKED
    }

    private fun clear() {
        // Best-effort cleanup of this object's copies, not guaranteed
        // erasure from JVM/Android memory or previous buffers.
        expectedPeerPin?.fill(0)
        transcriptDigest?.fill(0)
        expectedPeerPin = null
        transcriptDigest = null
        currentAttempt = null
        localApproval = false
        authenticatedRemoteApproval = false
    }
}
