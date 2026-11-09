package com.edenpractical.comms.pairing

/**
 * F4-S: monotonic, bounded waiting for one foreground live-mic turn.
 *
 * AudioRecord.READ_NON_BLOCKING can return zero samples while the driver
 * catches up. Wait briefly rather than hot-spinning, but NEVER wait forever
 * for a dead microphone. Any clock rollback fails closed. The hard overall
 * deadline is the existing 3-second turn; progress never extends that limit.
 *
 * One capture worker owns this object. It stores time only, not PCM, and has
 * no networking, permissions, service, file, or peer-identity authority.
 */
internal class DebugLiveMicReadBudget(startedAtMs: Long) {
    companion object {
        const val MAX_TURN_MS = 3_000L
        const val MAX_SILENCE_MS = 450L
        const val RETRY_PAUSE_MS = 5L
    }

    private val startedAt = startedAtMs
    private var lastProgressMs = startedAtMs
    private var lastCheckedMs = startedAtMs
    private var stopped = startedAtMs < 0L

    fun mayContinue(nowMs: Long): Boolean {
        if (stopped) return false
        if (nowMs < lastCheckedMs || nowMs < startedAt ||
            nowMs - startedAt >= MAX_TURN_MS ||
            nowMs - lastProgressMs >= MAX_SILENCE_MS
        ) {
            stopped = true
            return false
        }
        lastCheckedMs = nowMs
        return true
    }

    fun observedBytes(count: Int, nowMs: Long): Boolean {
        if (count <= 0 || !mayContinue(nowMs)) return false
        lastProgressMs = nowMs
        return true
    }

    fun stop() {
        stopped = true
    }
}
