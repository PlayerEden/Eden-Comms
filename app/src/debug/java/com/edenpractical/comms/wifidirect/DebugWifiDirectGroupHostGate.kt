package com.edenpractical.comms.wifidirect

/**
 * M3s: one user-initiated, nonpersistent LOCAL group-host diagnostic.
 * No target peer, candidate handle, QR, trust, socket, message or audio.
 * Android success callbacks only report OS request acceptance.
 */
internal class DebugWifiDirectGroupHostGate {
    enum class Phase { IDLE, CHECKING_EXISTING, READY, CREATING, HOST_OBSERVED, CLEANING, STOPPED }
    enum class End { EXISTING_GROUP, USER_STOP, BACKGROUND, TIMEOUT, RADIO_LOST,
        PERMISSION_LOST, PLATFORM_FAILURE, CHANNEL_LOST, CLEANUP_UNCONFIRMED, CLEANUP_CONFIRMED }
    class Token internal constructor(val generation: Long)
    data class Snapshot(val phase: Phase, val generation: Long, val reason: End?,
                        val creationSubmitted: Boolean, val radioHostObserved: Boolean)
    private var generation = 0L
    private var current: Token? = null
    private var phase = Phase.IDLE
    private var reason: End? = null
    private var creationSubmitted = false
    private var radioHostObserved = false

    fun snapshot() = Snapshot(phase, generation, reason, creationSubmitted, radioHostObserved)
    fun active(token: Token) = current === token && phase in
        setOf(Phase.CHECKING_EXISTING, Phase.READY, Phase.CREATING, Phase.HOST_OBSERVED)
    fun pending(token: Token) = current === token && phase == Phase.CLEANING

    fun start(humanTap: Boolean, foreground: Boolean, ready: Boolean, api: Int): Token? {
        if (!humanTap || !foreground || !ready || api < 29 || phase != Phase.IDLE ||
            generation == Long.MAX_VALUE) return null
        val token = Token(++generation)
        current = token
        phase = Phase.CHECKING_EXISTING
        reason = null
        creationSubmitted = false
        radioHostObserved = false
        return token
    }

    fun observedExisting(token: Token, groupExists: Boolean): Boolean {
        if (current !== token || phase != Phase.CHECKING_EXISTING) return false
        if (groupExists) {
            phase = Phase.STOPPED
            reason = End.EXISTING_GROUP
            return false
        }
        phase = Phase.READY
        return true
    }

    fun submitCreation(token: Token): Boolean {
        if (current !== token || phase != Phase.READY) return false
        creationSubmitted = true
        phase = Phase.CREATING
        return true
    }

    fun platformAccepted(token: Token): Boolean =
        current === token && phase == Phase.CREATING

    fun groupSeen(token: Token, isOwner: Boolean): Boolean {
        if (!platformAccepted(token) || !isOwner) return false
        phase = Phase.HOST_OBSERVED
        radioHostObserved = true
        return true
    }

    fun stop(token: Token, why: End): Boolean {
        if (!active(token)) return false
        reason = why
        phase = if (creationSubmitted) Phase.CLEANING else Phase.STOPPED
        return true
    }

    fun cleaned(token: Token, confirmedAbsent: Boolean): Boolean {
        if (!pending(token)) return false
        phase = Phase.STOPPED
        reason = if (confirmedAbsent) End.CLEANUP_CONFIRMED else End.CLEANUP_UNCONFIRMED
        return true
    }

    fun reset(): Boolean {
        if (phase != Phase.STOPPED) return false
        current = null
        phase = Phase.IDLE
        reason = null
        creationSubmitted = false
        radioHostObserved = false
        // generation not reused; callbacks from prior attempts are inert.
        return true
    }
}
