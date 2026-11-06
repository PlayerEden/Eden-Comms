package com.edenpractical.comms.pairing

/**
 * Ephemeral 1:1 text displayed ONLY within the debuggable test session.
 *
 * Entries come from real current TLS writer/receiver callbacks, not fabricated
 * receipts. Only a bounded in-memory tail is kept. No database, clipboard
 * export, backup, notifications or persisted message history.
 *
 * Clear/finish drop UI references to text but DO NOT guarantee physical
 * erasure from RAM, keyboards, screenshots or another participant's device.
 */
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

    data class Entry(val direction: Direction, val id: Long, val text: String)

    companion object {
        const val MAX_VISIBLE = 12

        fun fresh(): DebugTextTranscript =
            DebugTextTranscript(State.WAITING, emptyList(), 0, 0, 0L, 0L)
    }

    fun ready(): DebugTextTranscript =
        if (state != State.WAITING) this
        else DebugTextTranscript(State.ACTIVE, emptyList(), 0, 0, 0L, 0L)

    /**
     * Ignore duplicate/late IDs even after older entries scroll off or are
     * explicitly cleared. IDs are monotonic separately for each direction.
     * The transport performs its own per-socket checks and consent validation.
     */
    fun record(direction: Direction, id: Long, text: String): DebugTextTranscript {
        val lastId = when (direction) {
            Direction.WRITTEN_TO_TLS -> lastWrittenId
            Direction.RECEIVED_HERE -> lastReceivedId
        }
        if (state != State.ACTIVE || id <= lastId || text.isBlank() ||
            text.codePointCount(0, text.length) > 280 ||
            text.codePoints().anyMatch { cp ->
                (cp < 0x20 && cp != 0x09 && cp != 0x0A) || cp in 0x7F..0x9F ||
                    cp in 0xD800..0xDFFF
            }
        ) return this

        val tail = (visible + Entry(direction, id, text)).takeLast(MAX_VISIBLE)
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
     * Clear only this phone's visible content. Keep QA counters and high-water
     * message IDs so old event callbacks cannot resurrect already-cleared text.
     * New incoming text or writes queued but not yet observed may still appear.
     */
    fun clearVisible(): DebugTextTranscript =
        if (state != State.ACTIVE || visible.isEmpty()) this
        else DebugTextTranscript(
            State.ACTIVE, emptyList(), writtenHere, receivedHere,
            lastWrittenId, lastReceivedId
        )

    /** Irreversibly end this UI session; keep only aggregate non-content counts. */
    fun finish(): DebugTextTranscript =
        if (state == State.FINISHED) this
        else DebugTextTranscript(
            State.FINISHED, emptyList(), writtenHere, receivedHere,
            lastWrittenId, lastReceivedId
        )

    private fun increment(count: Int): Int =
        if (count == Int.MAX_VALUE) count else count + 1
}
