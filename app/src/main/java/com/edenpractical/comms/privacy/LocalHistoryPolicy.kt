package com.edenpractical.comms.privacy

/**
 * F5-A: bounded per-conversation, opt-in, on-device message retention rules.
 *
 * Session-only is ALWAYS the default; a choice is not persisted or inherited
 * by a later session. This policy does not grant peer trust/transport access.
 * History cannot promise secure deletion of flash, OS, peer or screenshots.
 */
internal object LocalHistoryPolicy {
    const val SESSION_ONLY = 0L
    const val ONE_HOUR = 60L * 60_000L
    const val ONE_DAY = 24L * ONE_HOUR
    const val SEVEN_DAYS = 7L * ONE_DAY
    const val FOURTEEN_DAYS = 14L * ONE_DAY
    const val THIRTY_DAYS = 30L * ONE_DAY
    const val MAX_RECORDS_PER_PEER = 64
    const val MAX_PEERS = 8
    const val MAX_MESSAGE_CODEPOINTS = 280
    const val MAX_CLOCK_ROLLBACK_MS = 60_000L

    enum class Direction { WRITTEN_HERE, RECEIVED_HERE }
    data class Choice(val durationMs: Long, val label: String)
    data class SavedMessage(
        val direction: Direction,
        val sourceId: Long,
        val text: String,
        val createdAtMs: Long,
        val expiresAtMs: Long
    )

    val choices = listOf(
        Choice(SESSION_ONLY, "Session only"),
        Choice(ONE_HOUR, "1 hour"),
        Choice(ONE_DAY, "24 hours"),
        Choice(SEVEN_DAYS, "7 days"),
        Choice(FOURTEEN_DAYS, "14 days"),
        Choice(THIRTY_DAYS, "30 days")
    )

    fun supported(durationMs: Long): Boolean =
        choices.any { it.durationMs == durationMs }

    fun label(durationMs: Long): String =
        choices.firstOrNull { it.durationMs == durationMs }?.label ?: "Session only"

    /** No overflowing arithmetic and no lifetime over 30 days. */
    fun expiry(createdAtMs: Long, durationMs: Long): Long? {
        if (createdAtMs <= 0L || !supported(durationMs) ||
            durationMs == SESSION_ONLY ||
            createdAtMs > Long.MAX_VALUE - durationMs
        ) return null
        return createdAtMs + durationMs
    }

    fun valid(message: SavedMessage): Boolean =
        message.sourceId > 0L &&
            message.createdAtMs > 0L &&
            message.expiresAtMs > message.createdAtMs &&
            message.expiresAtMs - message.createdAtMs <= THIRTY_DAYS &&
            message.text.isNotBlank() &&
            message.text.codePointCount(0, message.text.length) <= MAX_MESSAGE_CODEPOINTS &&
            message.text.codePoints().noneMatch { cp ->
                (cp < 0x20 && cp != 0x09 && cp != 0x0A) ||
                    cp in 0x7f..0x9f || cp in 0xd800..0xdfff
            }

    fun clockSafe(nowMs: Long, lastObservedMs: Long): Boolean =
        nowMs > 0L && lastObservedMs > 0L &&
            (nowMs >= lastObservedMs ||
                lastObservedMs - nowMs <= MAX_CLOCK_ROLLBACK_MS)

    /**
     * Filter BEFORE rendering; read/open/reconnect never changes expiry.
     * When clock moves backwards beyond small jitter, caller purges all.
     */
    fun visible(messages: List<SavedMessage>, nowMs: Long): List<SavedMessage> =
        messages.filter { valid(it) && nowMs >= it.createdAtMs && nowMs < it.expiresAtMs }
            .takeLast(MAX_RECORDS_PER_PEER)

    /**
     * Shortening applies immediately; lengthening never updates old records.
     * Session-only means purge the peer's local history.
     */
    fun shorten(messages: List<SavedMessage>, nowMs: Long, choiceMs: Long)
        : List<SavedMessage> {
        require(supported(choiceMs))
        if (choiceMs == SESSION_ONLY) return emptyList()
        return visible(messages.mapNotNull { entry ->
            val shorter = expiry(entry.createdAtMs, choiceMs) ?: return@mapNotNull null
            entry.copy(expiresAtMs = minOf(entry.expiresAtMs, shorter))
        }, nowMs)
    }
}
