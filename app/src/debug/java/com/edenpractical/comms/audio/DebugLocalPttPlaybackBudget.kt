package com.edenpractical.comms.audio

/**
 * F4-D3 finite monotonic deadline for a one-phone debug playback experiment.
 *
 * This clock model does not grant remote audio permission, schedule network packets,
 * guarantee hardware latency or authorize a retained communication session.
 */
internal class DebugLocalPttPlaybackBudget(startElapsedMs: Long) {
    private val started = startElapsedMs
    private var lastObserved = startElapsedMs
    private var closed = false

    init {
        require(startElapsedMs >= 0L)
    }

    /** Reject a stall, clock rollback, late event, or a callback after cancellation. */
    fun mayContinue(nowElapsedMs: Long): Boolean {
        if (closed || nowElapsedMs < lastObserved ||
            nowElapsedMs - started >= MAX_PLAYBACK_MILLIS
        ) {
            closed = true
            return false
        }
        lastObserved = nowElapsedMs
        return true
    }

    fun close() {
        closed = true
    }

    companion object {
        // Local capture is at most five seconds; this is only a bounded output margin.
        const val MAX_PLAYBACK_MILLIS = 8_000L
        const val RETRY_PAUSE_MILLIS = 10L
    }
}
