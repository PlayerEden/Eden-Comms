package com.edenpractical.comms.bluetooth

/**
 * Pure, per-attempt debug RFCOMM link state. Even LINK_OPEN is NOT an Eden
 * Comms verified session and never permits text, keys, or voice.
 * Late worker completions must not resurrect a closed link.
 */
internal class DebugRfcommAttemptGate {
    enum class Role { HOST, JOIN }
    enum class Phase { IDLE, CONNECTING, LINK_OPEN, STOPPED }
    enum class End {
        USER, BACKGROUND, PERMISSION_LOST, TIMEOUT, FAILED, EXPIRED,
        CONSENT_CONFIRMED, CONSENT_DECLINED
    }
    class Token internal constructor(val generation: Long, val role: Role)
    data class Snapshot(val phase: Phase, val role: Role?, val reason: End?)

    private var generation = 0L
    private var token: Token? = null
    private var phase = Phase.IDLE
    private var end: End? = null

    @Synchronized
    fun snapshot(): Snapshot = Snapshot(phase, token?.role, end)

    @Synchronized
    fun begin(role: Role, foreground: Boolean, permitted: Boolean): Token? {
        if (!foreground || !permitted || phase != Phase.IDLE ||
            generation == Long.MAX_VALUE
        ) return null
        val started = Token(++generation, role)
        token = started
        phase = Phase.CONNECTING
        end = null
        return started
    }

    @Synchronized
    fun isConnecting(current: Token): Boolean =
        token === current && phase == Phase.CONNECTING

    @Synchronized
    fun opened(current: Token, foreground: Boolean, permitted: Boolean): Boolean {
        if (!isConnecting(current)) return false
        if (!foreground || !permitted) {
            stop(current, if (!foreground) End.BACKGROUND else End.PERMISSION_LOST)
            return false
        }
        phase = Phase.LINK_OPEN
        return true
    }

    @Synchronized
    fun stop(current: Token, why: End): Boolean {
        if (token !== current || phase !in setOf(Phase.CONNECTING, Phase.LINK_OPEN)) return false
        phase = Phase.STOPPED
        end = why
        return true
    }

    @Synchronized
    fun isLive(current: Token): Boolean =
        token === current && phase in setOf(Phase.CONNECTING, Phase.LINK_OPEN)

    @Synchronized
    fun reset(): Boolean {
        if (phase != Phase.STOPPED) return false
        token = null
        phase = Phase.IDLE
        end = null
        return true
    }
}
