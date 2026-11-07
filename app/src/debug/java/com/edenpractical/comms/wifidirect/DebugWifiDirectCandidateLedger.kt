package com.edenpractical.comms.wifidirect

/**
 * One discovery attempt's volatile, anonymous Wi-Fi Direct candidate staging.
 * T is an opaque Android device object held ONLY in private memory. Do not log,
 * serialize, display, return handles, or infer an Eden Comms identity from it.
 *
 * Even an explicitly selected ordinal has no connect or send authority.
 */
internal class DebugWifiDirectCandidateLedger<T : Any> {
    companion object { const val MAX_CANDIDATES = 8 }
    class Token internal constructor(val generation: Long)
    data class Row(val ordinal: Int, val label: String, val selected: Boolean)

    private var generation = 0L
    private var token: Token? = null
    private val handles = ArrayList<T>(MAX_CANDIDATES)
    private var selected: Int? = null

    fun begin(): Token? {
        if (token != null || generation == Long.MAX_VALUE) return null
        val active = Token(++generation)
        token = active
        return active
    }

    /**
     * Deduplicates by Android device-object equality without asking for or
     * retaining an address string. Existing row numbers NEVER reorder.
     */
    fun observe(current: Token, snapshot: Collection<T>): Boolean {
        if (token !== current || selected != null) return false
        var added = false
        for (handle in snapshot) {
            if (handles.size >= MAX_CANDIDATES) break
            if (handles.none { it == handle }) {
                handles.add(handle)
                added = true
            }
        }
        return added
    }

    fun rows(current: Token): List<Row> =
        if (token === current) handles.indices.map { index ->
            Row(index + 1, "Unverified device ${index + 1}", selected == index + 1)
        } else emptyList()

    fun choose(current: Token, ordinal: Int): Boolean {
        if (token !== current || ordinal !in 1..handles.size) return false
        selected = ordinal
        return true
    }

    fun selectedOrdinal(current: Token): Int? =
        if (token === current) selected else null

    /** Must execute on every Stop/background/error/timeout. */
    fun clear() {
        handles.clear()
        selected = null
        token = null
    }
}
