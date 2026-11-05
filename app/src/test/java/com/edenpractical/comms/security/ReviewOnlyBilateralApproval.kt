package com.edenpractical.comms.security

import java.security.MessageDigest

/**
 * REVIEW ONLY: deterministic two-person decision ordering model in JVM test
 * sources. Never packaged in an APK, never receives live network input and
 * NEVER grants permission to send, receive or store app messages.
 *
 * In particular simulatePeerFrame() does NOT authenticate a network sender.
 * A real trusted TLS transport adapter requires independent protocol review.
 */
internal class ReviewOnlyBilateralApproval {
    enum class Phase { IDLE, AWAITING_DECISIONS, SIMULATED_AGREEMENT, DENIED }

    internal data class AttemptId(val number: Long)
    private var counter = 0L
    private var current: AttemptId? = null
    private var role: ReviewOnlyApprovalFrame.Role? = null
    private var expectedCommitment: ByteArray? = null
    private var localDecisionSeen = false
    private var remoteDecisionSeen = false

    var phase = Phase.IDLE
        private set

    /** SIMULATED only; this is not permission for production or demo IO. */
    val bothDecisionsSimulated: Boolean
        get() = phase == Phase.SIMULATED_AGREEMENT

    fun begin(localRole: ReviewOnlyApprovalFrame.Role, commitment: ByteArray): AttemptId? {
        if (phase != Phase.IDLE) return null
        if (counter == Long.MAX_VALUE ||
            commitment.size != 32 || commitment.all { it == 0.toByte() }
        ) {
            deny()
            return null
        }
        counter++
        val id = AttemptId(counter)
        current = id
        role = localRole
        expectedCommitment = commitment.copyOf()
        localDecisionSeen = false
        remoteDecisionSeen = false
        phase = Phase.AWAITING_DECISIONS
        return id
    }

    fun localDecision(
        attempt: AttemptId,
        commitment: ByteArray,
        decision: ReviewOnlyApprovalFrame.Decision
    ): Boolean {
        if (attempt != current) return false
        if (!inProgress(commitment) || localDecisionSeen) {
            deny()
            return false
        }
        if (decision == ReviewOnlyApprovalFrame.Decision.DECLINE) {
            deny()
            return true
        }
        localDecisionSeen = true
        finishIfBoth()
        return true
    }

    /**
     * ASSUMPTION FOR SIMULATION ONLY: caller pretends the remote packet has
     * already been authenticated over a live, pinned, channel-bound TLS socket.
     * A decoded packet BY ITSELF cannot establish such authentication.
     */
    fun simulatePeerFrame(
        attempt: AttemptId,
        packet: ReviewOnlyApprovalFrame.Packet
    ): Boolean {
        if (attempt != current) return false
        val expectedRole = when (role) {
            ReviewOnlyApprovalFrame.Role.PRESENTER -> ReviewOnlyApprovalFrame.Role.JOINER
            ReviewOnlyApprovalFrame.Role.JOINER -> ReviewOnlyApprovalFrame.Role.PRESENTER
            null -> {
                deny()
                return false
            }
        }
        if (!inProgress(packet.commitment) || remoteDecisionSeen ||
            packet.role != expectedRole
        ) {
            deny()
            return false
        }
        if (packet.decision == ReviewOnlyApprovalFrame.Decision.DECLINE) {
            deny()
            return true
        }
        remoteDecisionSeen = true
        finishIfBoth()
        return true
    }

    /** Local cancellation or disconnect resets all simulation permissions. */
    fun transportLost(attempt: AttemptId) {
        if (attempt == current) reset()
    }

    /** Hypothetical deadline enforcement would be performed by an app adapter. */
    fun deadlineElapsed(attempt: AttemptId) {
        if (attempt == current) deny()
    }

    /** Explicit user cancellation resets even when waiting for peer response. */
    fun cancel() = reset()

    private fun inProgress(proposed: ByteArray): Boolean {
        val expected = expectedCommitment
        return phase == Phase.AWAITING_DECISIONS &&
            expected != null && proposed.size == expected.size &&
            MessageDigest.isEqual(expected, proposed)
    }

    private fun finishIfBoth() {
        if (localDecisionSeen && remoteDecisionSeen) {
            phase = Phase.SIMULATED_AGREEMENT
        }
    }

    private fun deny() {
        clear()
        phase = Phase.DENIED
    }

    private fun reset() {
        clear()
        phase = Phase.IDLE
    }

    private fun clear() {
        expectedCommitment?.fill(0)
        expectedCommitment = null
        current = null
        role = null
        localDecisionSeen = false
        remoteDecisionSeen = false
    }
}
