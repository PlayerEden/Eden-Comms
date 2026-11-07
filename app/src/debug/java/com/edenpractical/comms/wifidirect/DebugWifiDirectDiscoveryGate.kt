package com.edenpractical.comms.wifidirect

/**
 * M3q: generation-fenced, one-shot discovery only. A successful Android
 * discovery request does not mean any device was found or authenticated.
 * This gate never owns a platform radio, peer identifier or message.
 */
internal class DebugWifiDirectDiscoveryGate {
    enum class Phase { IDLE, WAITING_FOR_P2P, READY, STARTING, SCANNING, STOPPED }
    enum class End {
        STOP_BUTTON, BACKGROUND, TIMEOUT, PERMISSION_LOST,
        RADIO_LOST, PLATFORM_FAILURE, CHANNEL_LOST, EVENT_LIMIT
    }
    class Token internal constructor(val generation: Long)
    data class Snapshot(val phase: Phase, val generation: Long, val events: Int, val end: End?)
    companion object {
        const val MAX_MS = 30_000L
        const val MAX_EVENTS = 32
    }
    private var generation = 0L
    private var current: Token? = null
    private var phase = Phase.IDLE
    private var events = 0
    private var end: End? = null

    fun snapshot() = Snapshot(phase, generation, events, end)
    fun active(token: Token): Boolean = current === token &&
        phase in setOf(Phase.WAITING_FOR_P2P, Phase.READY, Phase.STARTING, Phase.SCANNING)

    fun begin(foreground: Boolean, prerequisites: Boolean): Token? {
        if (!foreground || !prerequisites || phase != Phase.IDLE ||
            generation == Long.MAX_VALUE) return null
        val token = Token(++generation)
        current = token
        phase = Phase.WAITING_FOR_P2P
        events = 0
        end = null
        return token
    }

    fun observedRadio(token: Token, enabled: Boolean): Boolean {
        if (!active(token)) return false
        if (!enabled) { stop(token, End.RADIO_LOST); return false }
        if (phase == Phase.WAITING_FOR_P2P) phase = Phase.READY
        return phase == Phase.READY
    }

    fun requestSent(token: Token): Boolean {
        if (!active(token) || phase != Phase.READY) return false
        phase = Phase.STARTING
        return true
    }

    fun discoveryAccepted(token: Token): Boolean {
        if (!active(token) || phase != Phase.STARTING) return false
        phase = Phase.SCANNING
        return true
    }

    fun discoveryFailed(token: Token): Boolean = stop(token, End.PLATFORM_FAILURE)

    fun peerListChanged(token: Token): Boolean {
        if (!active(token) || phase != Phase.SCANNING) return false
        if (events >= MAX_EVENTS) { stop(token, End.EVENT_LIMIT); return false }
        events++
        return true
    }

    fun stop(token: Token, reason: End): Boolean {
        if (!active(token)) return false
        phase = Phase.STOPPED
        end = reason
        return true
    }

    fun reset(): Boolean {
        if (phase != Phase.STOPPED) return false
        current = null
        phase = Phase.IDLE
        events = 0
        end = null
        // generation intentionally never resets; stale callbacks cannot revive.
        return true
    }
}
