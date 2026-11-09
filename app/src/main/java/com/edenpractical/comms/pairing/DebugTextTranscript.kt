package com.edenpractical.comms.pairing

/**
 * Ephemeral 1:1 text displayed ONLY within the debuggable test session.
 *
 * Message events come from this phone's actual authenticated TLS callbacks.
 * The optional disappearing timer acts ONLY on this phone's bounded RAM view.
 * No timer is transmitted to a peer, and a local removal is not remote
 * deletion, disk erasure, or a guarantee about keyboard/OS/recipient copies.
 */
internal object DebugLocalDisappearingPolicy {
    data class Choice(val durationMs: Long, val label: String)
    const val OFF = 0L
    const val ONE_MINUTE = 60_000L
    const val FIVE_MINUTES = 5L * ONE_MINUTE
    const val FIFTEEN_MINUTES = 15L * ONE_MINUTE

    val choices: List<Choice> = listOf(
        Choice(OFF, "Off"),
        Choice(ONE_MINUTE, "1 minute"),
        Choice(FIVE_MINUTES, "5 minutes"),
        Choice(FIFTEEN_MINUTES, "15 minutes")
    )

    fun supports(durationMs: Long): Boolean =
        choices.any { it.durationMs == durationMs }

    fun label(durationMs: Long): String =
        choices.firstOrNull { it.durationMs == durationMs }?.label ?: "Off"

    /** Monotonic age; overflow/invalid input fails closed to immediate expiry. */
    fun deadline(nowElapsedMs: Long, durationMs: Long): Long? {
        require(supports(durationMs)) { "Unsupported local expiry duration" }
        if (durationMs == OFF) return null
        if (nowElapsedMs < 0 || nowElapsedMs > Long.MAX_VALUE - durationMs)
            return nowElapsedMs
        return nowElapsedMs + durationMs
    }
}

internal class DebugTextTranscript private constructor(
    val state: State,
    val visible: List<Entry>,
    val writtenHere: Int,
    val receivedHere: Int,
    private val lastWrittenId: Long,
    private val lastReceivedId: Long
) {
    enum class State { WAITING, ACTIVE, FINISHED }
    enum class Direction { WRITTEN_TO_TLS, RECEIVED_HERE }

    data class Entry(
        val direction: Direction,
        val id: Long,
        val text: String,
        val observedAtElapsedMs: Long = 0L,
        val expiresAtElapsedMs: Long? = null
    )

    companion object {
        const val MAX_VISIBLE = 12
        fun fresh(): DebugTextTranscript =
            DebugTextTranscript(State.WAITING, emptyList(), 0, 0, 0L, 0L)
    }

    fun ready(): DebugTextTranscript =
        if (state != State.WAITING) this
        else DebugTextTranscript(State.ACTIVE, emptyList(), 0, 0, 0L, 0L)

    /**
     * Only the active, real session's UI callbacks can add text. Old IDs are
     * rejected even if entries have expired, scrolled off, or been cleared.
     * A timer starts at THIS PHONE's display observation, independently of
     * the other person's setting or delivery/read state.
     */
    fun record(
        direction: Direction,
        id: Long,
        text: String,
        observedAtElapsedMs: Long = 0L,
        disappearAfterMs: Long = DebugLocalDisappearingPolicy.OFF
    ): DebugTextTranscript {
        val lastId = when (direction) {
            Direction.WRITTEN_TO_TLS -> lastWrittenId
            Direction.RECEIVED_HERE -> lastReceivedId
        }
        if (state != State.ACTIVE || id <= lastId || text.isBlank() ||
            !DebugLocalDisappearingPolicy.supports(disappearAfterMs) ||
            text.codePointCount(0, text.length) > 280 ||
            text.codePoints().anyMatch { cp ->
                (cp < 0x20 && cp != 0x09 && cp != 0x0A) || cp in 0x7F..0x9F ||
                    cp in 0xD800..0xDFFF
            }
        ) return this

        val deadline = DebugLocalDisappearingPolicy.deadline(
            observedAtElapsedMs, disappearAfterMs
        )
        val tail = (visible + Entry(
            direction, id, text, observedAtElapsedMs, deadline
        )).takeLast(MAX_VISIBLE)
        return when (direction) {
            Direction.WRITTEN_TO_TLS -> DebugTextTranscript(
                State.ACTIVE, tail, increment(writtenHere), receivedHere,
                id, lastReceivedId
            )
            Direction.RECEIVED_HERE -> DebugTextTranscript(
                State.ACTIVE, tail, writtenHere, increment(receivedHere),
                lastWrittenId, id
            )
        }
    }

    /**
     * If a user shortens the setting, old visible messages must never gain
     * more lifetime. Switching OFF or selecting a longer interval affects
     * future messages only; it cannot bring back anything already expired.
     */
    fun shortenVisibleExpiry(durationMs: Long): DebugTextTranscript {
        require(DebugLocalDisappearingPolicy.supports(durationMs))
        if (state != State.ACTIVE || visible.isEmpty() ||
            durationMs == DebugLocalDisappearingPolicy.OFF) return this
        val shortened = visible.map { entry ->
            val newDeadline = checkNotNull(DebugLocalDisappearingPolicy.deadline(
                entry.observedAtElapsedMs, durationMs
            ))
            entry.copy(expiresAtElapsedMs =
                entry.expiresAtElapsedMs?.let { minOf(it, newDeadline) }
                    ?: newDeadline)
        }
        if (shortened == visible) return this
        return DebugTextTranscript(
            state, shortened, writtenHere, receivedHere, lastWrittenId, lastReceivedId
        )
    }

    fun nextExpiryElapsedMs(): Long? =
        if (state != State.ACTIVE) null
        else visible.mapNotNull { it.expiresAtElapsedMs }.minOrNull()

    /**
     * The host UI schedules a wakeup at the nearest deadline. A monotonic
     * clock rollback is treated as expiry, never as extended retention.
     * Counts and high-water IDs survive removal; no message is resurrected.
     */
    fun pruneExpired(nowElapsedMs: Long): DebugTextTranscript {
        if (state != State.ACTIVE || visible.isEmpty()) return this
        val kept = visible.filter { entry ->
            val expiry = entry.expiresAtElapsedMs
            expiry == null ||
                (nowElapsedMs >= entry.observedAtElapsedMs && nowElapsedMs < expiry)
        }
        if (kept.size == visible.size) return this
        return DebugTextTranscript(
            state, kept, writtenHere, receivedHere, lastWrittenId, lastReceivedId
        )
    }

    /** Drop only this phone's visible text, not transport ACK state/counters. */
    fun clearVisible(): DebugTextTranscript =
        if (state != State.ACTIVE || visible.isEmpty()) this
        else DebugTextTranscript(
            State.ACTIVE, emptyList(), writtenHere, receivedHere,
            lastWrittenId, lastReceivedId
        )

    /** Terminal sessions cannot be reopened or deliver late text. */
    fun finish(): DebugTextTranscript =
        if (state == State.FINISHED) this
        else DebugTextTranscript(
            State.FINISHED, emptyList(), writtenHere, receivedHere,
            lastWrittenId, lastReceivedId
        )

    private fun increment(count: Int): Int =
        if (count == Int.MAX_VALUE) count else count + 1
}
