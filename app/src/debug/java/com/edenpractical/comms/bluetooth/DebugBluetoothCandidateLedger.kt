package com.edenpractical.comms.bluetooth

/**
 * RAM-only collection of unverified Bluetooth inquiry handles for one debug
 * scan. The device HANDLE is intentionally opaque: no names, addresses,
 * advertising bytes, bond data or durable identifiers are exposed by the API.
 *
 * This is neither Eden Comms discovery nor proof of peer identity. A selected
 * handle cannot open a socket or authorize messages. Do not persist instances.
 */
internal class DebugBluetoothCandidateLedger<T : Any>(
    private val maxCandidates: Int = MAX_CANDIDATES
) {
    companion object { const val MAX_CANDIDATES = 12 }
    init { require(maxCandidates in 1..MAX_CANDIDATES) }

    enum class Phase { EMPTY, COLLECTING, REVIEW, SELECTED }

    /** Object identity prevents callers reusing a stale token after restart. */
    class Token internal constructor(val generation: Long)

    data class Row(val ordinal: Int, val label: String)

    private var generation = 0L
    private var token: Token? = null
    private var phase = Phase.EMPTY
    private val handles = ArrayList<T>()
    private var selection: Pair<Int, T>? = null

    fun phase(): Phase = phase
    fun selectedOrdinal(): Int? = selection?.first

    /** A fresh scan invalidates both prior results and prior selections. */
    fun begin(): Token? {
        if (phase == Phase.COLLECTING || generation == Long.MAX_VALUE) return null
        clear()
        val current = Token(++generation)
        token = current
        phase = Phase.COLLECTING
        return current
    }

    /** Duplicate inquiry events are not shown again. Entries max out at 12. */
    fun observe(current: Token, handle: T): Boolean {
        if (token !== current || phase != Phase.COLLECTING) return false
        if (handles.size >= maxCandidates || handles.any { it == handle }) return false
        handles.add(handle)
        return true
    }

    /** Natural scan completion retains choices until user clears/exits. */
    fun finish(current: Token): Boolean {
        if (token !== current || phase != Phase.COLLECTING) return false
        phase = Phase.REVIEW
        return true
    }

    fun rows(current: Token): List<Row> =
        if (token === current && phase in setOf(Phase.COLLECTING, Phase.REVIEW)) {
            handles.indices.map { Row(it + 1, "Unverified signal ${it + 1}") }
        } else emptyList()

    /**
     * Selecting is an explicit local staging action, NOT connection/trust.
     * Discard all other handles, retaining at most one opaque selected handle
     * in volatile memory for a future separately reviewed connection step.
     */
    fun select(current: Token, ordinal: Int): Boolean {
        if (token !== current ||
            phase !in setOf(Phase.COLLECTING, Phase.REVIEW) ||
            ordinal !in 1..handles.size
        ) return false
        val selected = handles[ordinal - 1]
        handles.clear()
        selection = ordinal to selected
        phase = Phase.SELECTED
        return true
    }

    /** Forget all temporary Bluetooth device objects and UI ordinals. */
    fun clear() {
        handles.clear()
        selection = null
        token = null
        phase = Phase.EMPTY
    }
}
