package com.edenpractical.comms.wifidirect

/**
 * One explicit debug-only Wi-Fi Direct link attempt to an anonymous candidate.
 * This gate never owns or certifies an Android device identity. A formed group
 * is NOT mutual TLS, QR-target binding, human approval, or message authority.
 */
internal class DebugWifiDirectTargetLinkGate {
    companion object {
        const val MAX_LINK_MS = 18_000L
        const val MAX_CLEANUP_MS = 4_000L
    }
    enum class Phase { IDLE, CHECKING_EXISTING, READY, REQUESTING, WAITING, GROUP_OBSERVED,
        CLEANING, STOPPED }
    enum class End { EXISTING_GROUP, USER_STOP, BACKGROUND, TIMEOUT, PERMISSION_LOST,
        RADIO_LOST, CHANNEL_LOST, PLATFORM_FAILURE, CLEANUP_UNCONFIRMED, CLEANUP_ABSENT }
    class Token internal constructor(val generation: Long)
    data class Snapshot(val phase: Phase, val reason: End?, val submitted: Boolean,
                        val groupObserved: Boolean, val generation: Long)
    private var generation = 0L
    private var current: Token? = null
    private var phase = Phase.IDLE
    private var reason: End? = null
    private var submitted = false
    private var groupObserved = false
    fun snapshot() = Snapshot(phase, reason, submitted, groupObserved, generation)
    fun active(token: Token): Boolean = current === token && phase in setOf(
        Phase.CHECKING_EXISTING, Phase.READY, Phase.REQUESTING, Phase.WAITING,
        Phase.GROUP_OBSERVED
    )
    fun pendingCleanup(token: Token): Boolean = current === token && phase == Phase.CLEANING

    fun start(confirmed: Boolean, foreground: Boolean, ready: Boolean, api: Int): Token? {
        if (!confirmed || !foreground || !ready || api < 29 || phase != Phase.IDLE ||
            generation == Long.MAX_VALUE) return null
        val token = Token(++generation)
        current = token
        phase = Phase.CHECKING_EXISTING
        reason = null
        submitted = false
        groupObserved = false
        return token
    }

    fun checkedExisting(token: Token, groupAlreadyExists: Boolean): Boolean {
        if (current !== token || phase != Phase.CHECKING_EXISTING) return false
        if (groupAlreadyExists) {
            phase = Phase.STOPPED
            reason = End.EXISTING_GROUP
            return false
        }
        phase = Phase.READY
        return true
    }

    fun submit(token: Token): Boolean {
        if (current !== token || phase != Phase.READY) return false
        submitted = true
        phase = Phase.REQUESTING
        return true
    }

    fun requestAccepted(token: Token): Boolean {
        if (current !== token || phase != Phase.REQUESTING) return false
        phase = Phase.WAITING
        return true
    }

    fun observedGroup(token: Token, formed: Boolean): Boolean {
        if (current !== token || !formed ||
            phase !in setOf(Phase.REQUESTING, Phase.WAITING)) return false
        groupObserved = true
        phase = Phase.GROUP_OBSERVED
        return true
    }

    fun stop(token: Token, why: End): Boolean {
        if (!active(token)) return false
        reason = why
        phase = if (submitted) Phase.CLEANING else Phase.STOPPED
        return true
    }

    fun cleanupCompleted(token: Token, confirmedAbsent: Boolean): Boolean {
        if (!pendingCleanup(token)) return false
        phase = Phase.STOPPED
        reason = if (confirmedAbsent) End.CLEANUP_ABSENT else End.CLEANUP_UNCONFIRMED
        return true
    }

    fun reset(): Boolean {
        if (phase != Phase.STOPPED) return false
        current = null
        phase = Phase.IDLE
        reason = null
        submitted = false
        groupObserved = false
        // generation never resets; old platform callbacks have no authority.
        return true
    }
}
