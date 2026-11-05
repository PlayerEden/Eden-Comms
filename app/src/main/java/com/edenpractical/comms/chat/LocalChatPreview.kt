package com.edenpractical.comms.chat

/**
 * A strictly on-device UI sandbox for designing Eden Comms' future text screen.
 *
 * NOT an authenticated conversation or transport adapter. There is no send API,
 * packet encoder, peer ID, persistence, recovery, or network dependency.
 * The only allowed bubbles are text typed locally and explicitly tagged fake
 * remote samples. This code may not claim that messages were sent or delivered.
 */
internal class LocalChatPreview private constructor(
    val entries: List<Entry>,
    val nextId: Long,
    val ended: Boolean
) {
    enum class Origin {
        TYPED_HERE, EXAMPLE_NOT_RECEIVED
    }

    enum class DisplayState(val label: String) {
        LOCAL_ONLY("Local preview • not sent"),
        FAKE_SAMPLE("Example only • not received")
    }

    data class Entry(
        val id: Long,
        val text: String,
        val timeLabel: String,
        val origin: Origin
    ) {
        val displayState: DisplayState
            get() = if (origin == Origin.TYPED_HERE)
                DisplayState.LOCAL_ONLY else DisplayState.FAKE_SAMPLE
    }

    companion object {
        const val MAX_CHARACTERS = 280
        const val MAX_VISIBLE = 12
        private const val MAX_TIME_LABEL = 16

        fun newSession(): LocalChatPreview =
            LocalChatPreview(emptyList(), 1L, ended = false)
    }

    val canEnterText: Boolean get() = !ended

    /** Only typed local preview: NO transmission and NO delivery receipt. */
    fun addLocallyTyped(text: String, timeLabel: String): LocalChatPreview {
        if (ended) return this
        val clean = text.trim()
        if (clean.isEmpty() || clean.length > MAX_CHARACTERS) return this
        return append(clean, checkedTimeLabel(timeLabel), Origin.TYPED_HERE)
    }

    /**
     * Opt-in fictional example to inspect incoming bubble layout. Neither
     * this text nor the local text is ever supplied by a real remote peer.
     */
    fun addExampleLayout(): LocalChatPreview {
        if (ended || entries.any { it.origin == Origin.EXAMPLE_NOT_RECEIVED }) return this
        var state = this
        state = state.append(
            "Example conversation layout — no phone connected.",
            "SAMPLE",
            Origin.EXAMPLE_NOT_RECEIVED
        )
        state = state.append(
            "Real messages will appear here only after verified pairing.",
            "SAMPLE",
            Origin.EXAMPLE_NOT_RECEIVED
        )
        return state
    }

    /** Erase the local UI payload now, regardless of whether it was a sample. */
    fun clear(): LocalChatPreview =
        if (ended || entries.isEmpty()) this
        else LocalChatPreview(emptyList(), nextId, ended = false)

    /**
     * Terminal for this screen instance. Resuming a session always creates
     * a brand-new LocalChatPreview; old references cannot add new text.
     */
    fun end(): LocalChatPreview =
        if (ended) this else LocalChatPreview(emptyList(), nextId, ended = true)

    private fun append(text: String, timeLabel: String, origin: Origin): LocalChatPreview {
        if (ended || nextId == Long.MAX_VALUE) return this
        val updated = (entries + Entry(nextId, text, timeLabel, origin)).takeLast(MAX_VISIBLE)
        return LocalChatPreview(updated, nextId + 1L, ended = false)
    }

    private fun checkedTimeLabel(label: String): String =
        if (label.length in 1..MAX_TIME_LABEL && label.all { it.isDigit() || it == ':' || it == ' ' ||
                it == 'A' || it == 'P' || it == 'M' || it == 'a' || it == 'p' || it == 'm' }) {
            label
        } else "NOW"
}
