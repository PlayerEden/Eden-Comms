package com.edenpractical.comms.pairing

import com.edenpractical.comms.protocol.TestPacketCodec

/**
 * Ephemeral, immutable screen ledger for the DEBUG-ONLY four-preset-phrase demo.
 *
 * This holds ONLY direction and fixed phrase IDs in RAM; no user-supplied text,
 * peer identity, packet bytes, socket or persistent history. It is NOT a
 * production conversation, authenticated receipt, or authorization policy.
 *
 * Each new pairing attempt gets a fresh ledger. It accepts activity only after
 * the real debug READY event, caps the visible tail, and clears that tail on
 * close while retaining local event totals for the optional QA summary.
 */
internal class DebugSessionTranscript private constructor(
    val state: State,
    val reachedReady: Boolean,
    val writtenToTls: Int,
    val receivedHere: Int,
    val recent: List<Entry>
) {
    enum class State { WAITING, ACTIVE, FINISHED }
    enum class Direction { WRITTEN_TO_TLS, RECEIVED_HERE }

    data class Entry(val direction: Direction, val phraseId: Int)

    companion object {
        const val MAX_VISIBLE = 8
        fun fresh(): DebugSessionTranscript =
            DebugSessionTranscript(State.WAITING, false, 0, 0, emptyList())
    }

    fun ready(): DebugSessionTranscript =
        if (state != State.WAITING) this
        else DebugSessionTranscript(State.ACTIVE, true, 0, 0, emptyList())

    /**
     * A local WRITE is not proof the other phone received or read the phrase.
     * Invalid IDs and out-of-phase activity are ignored, never rendered.
     */
    fun record(direction: Direction, phraseId: Int): DebugSessionTranscript {
        if (state != State.ACTIVE || TestPacketCodec.phrases.none { it.id == phraseId }) {
            return this
        }
        val tail = (recent + Entry(direction, phraseId)).takeLast(MAX_VISIBLE)
        return when (direction) {
            Direction.WRITTEN_TO_TLS -> DebugSessionTranscript(
                State.ACTIVE, true, saturatingIncrement(writtenToTls), receivedHere, tail
            )
            Direction.RECEIVED_HERE -> DebugSessionTranscript(
                State.ACTIVE, true, writtenToTls, saturatingIncrement(receivedHere), tail
            )
        }
    }

    /** Irreversible for this attempt: no late event can repopulate content. */
    fun finish(): DebugSessionTranscript =
        if (state == State.FINISHED) this
        else DebugSessionTranscript(
            State.FINISHED, reachedReady, writtenToTls, receivedHere, emptyList()
        )

    private fun saturatingIncrement(value: Int): Int =
        if (value == Int.MAX_VALUE) value else value + 1
}

/** An old transport callback cannot reopen a cancelled or replaced session. */
internal object DebugSessionEventAdmission {
    fun current(callbackAttempt: Int, activeAttempt: Int): Boolean =
        callbackAttempt == activeAttempt

    fun canRecord(
        callbackAttempt: Int,
        activeAttempt: Int,
        visibleStage: String,
        transcript: DebugSessionTranscript
    ): Boolean =
        current(callbackAttempt, activeAttempt) &&
            visibleStage == "demo" &&
            transcript.state == DebugSessionTranscript.State.ACTIVE
}
