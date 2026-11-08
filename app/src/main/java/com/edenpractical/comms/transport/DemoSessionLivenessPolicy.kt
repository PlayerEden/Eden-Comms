package com.edenpractical.comms.transport

/**
 * Deterministic watchdog policy for the DEBUG-ONLY LAN text session.
 *
 * Uses elapsed-realtime milliseconds, never wall-clock time. A caller supplies
 * snapshots; this policy cannot authenticate peers or authorize real messages.
 */
internal object DemoSessionLivenessPolicy {
    const val MAX_SESSION_MINUTES: Long = 30L
    const val MAX_SESSION_MS: Long = MAX_SESSION_MINUTES * 60_000L
    const val PEER_SILENCE_MS: Long = 32_000L

    enum class EndReason { SESSION_LIMIT, PEER_SILENT, INVALID_TIMING }

    fun endReason(nowMs: Long, startedMs: Long, lastReceivedMs: Long): EndReason? {
        // A monotonic-clock inconsistency must never extend a session.
        if (nowMs < startedMs || lastReceivedMs < startedMs) return EndReason.INVALID_TIMING
        if (nowMs - startedMs >= MAX_SESSION_MS) return EndReason.SESSION_LIMIT
        // A newer read may be posted concurrently after the caller sampled now.
        // Do not spuriously disconnect a responsive peer on that harmless race.
        if (lastReceivedMs > nowMs) return null
        if (nowMs - lastReceivedMs > PEER_SILENCE_MS) return EndReason.PEER_SILENT
        return null
    }
}
