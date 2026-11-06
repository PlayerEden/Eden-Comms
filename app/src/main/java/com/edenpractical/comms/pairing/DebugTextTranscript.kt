package com.edenpractical.comms.pairing

/**
 * Ephemeral 1:1 text displayed ONLY within the debuggable test session.
 *
 * Each entry comes from the actual current TLS writer or receiver callback;
 * neither the UI nor tests can manufacture a peer receipt. No database,
 * clipboard export, app backup, notification, saved-state or history.
 * Finishing or starting a new attempt discards all visible text references.
 */
internal class DebugTextTranscript private constructor(
    val state: State,
    val visible: List<Entry>,
    val writtenHere: Int,
    val receivedHere: Int
) {
    enum class State { WAITING, ACTIVE, FINISHED }
    enum class Direction { WRITTEN_TO_TLS, RECEIVED_HERE }

    data class Entry(
        val direction: Direction,
        val id: Long,
        val text: String
    )

    companion object {
        const val MAX_VISIBLE = 12
        fun fresh() = DebugTextTranscript(State.WAITING, emptyList(), 0, 0)
    }

    fun ready(): DebugTextTranscript =
        if (state == State.WAITING)
            DebugTextTranscript(State.ACTIVE, emptyList(), 0, 0)
        else this

    /** Reject stale callbacks, duplicate IDs, blank or control-heavy content. */
    fun record(direction: Direction, id: Long, text: String): DebugTextTranscript {
        if (state != State.ACTIVE || id <= 0 || text.isBlank() ||
            text.codePointCount(0, text.length) > 280 ||
            text.codePoints().anyMatch { cp ->
                (cp < 0x20 && cp != 0x09 && cp != 0x0A) || cp in 0x7F..0x9F
            }
        ) return this
        if (visible.any { it.direction == direction && it.id == id }) return this
        val tail = (visible + Entry(direction, id, text)).takeLast(MAX_VISIBLE)
        return when (direction) {
            Direction.WRITTEN_TO_TLS ->
                DebugTextTranscript(State.ACTIVE, tail, increment(writtenHere), receivedHere)
            Direction.RECEIVED_HERE ->
                DebugTextTranscript(State.ACTIVE, tail, writtenHere, increment(receivedHere))
        }
    }

    /** Keep non-content counts for screen QA while discarding text. */
    fun finish(): DebugTextTranscript =
        if (state == State.FINISHED) this
        else DebugTextTranscript(State.FINISHED, emptyList(), writtenHere, receivedHere)

    private fun increment(count: Int): Int =
        if (count == Int.MAX_VALUE) count else count + 1
}
