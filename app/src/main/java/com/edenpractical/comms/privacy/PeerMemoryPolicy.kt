package com.edenpractical.comms.privacy

/**
 * F5-B: recording an explicitly verified public peer identity is independent
 * of both saved message history and permission to connect. A stored pin is
 * NEVER an authenticated live session and NEVER waives fresh QR, pinned
 * mutual TLS, live channel binding or the two people's confirmations.
 */
internal object PeerMemoryPolicy {
    const val PIN_BYTES = 32
    const val MAX_PEERS = 8
    const val ONE_DAY_MS = 86_400_000L
    const val MAX_AGE_MS = PeerTrustExpiry.MAX_DAYS * ONE_DAY_MS
    const val MAX_CLOCK_ROLLBACK_MS = 60_000L

    data class Saved(
        val pin: ByteArray,
        val savedAtMs: Long,
        val expiresAtMs: Long
    )

    fun validPin(pin: ByteArray): Boolean =
        pin.size == PIN_BYTES && pin.any { it != 0.toByte() }

    fun expiryForExplicitChoice(nowMs: Long, days: Int): Long? =
        try {
            if (nowMs <= 0 || days !in 1..PeerTrustExpiry.MAX_DAYS) null
            else PeerTrustExpiry.expiresAtOrNull(nowMs, days)
        } catch (_: ArithmeticException) { null }

    fun valid(saved: Saved, nowMs: Long): Boolean =
        validPin(saved.pin) &&
            saved.expiresAtMs > saved.savedAtMs &&
            saved.expiresAtMs - saved.savedAtMs <= MAX_AGE_MS &&
            PeerTrustExpiry.isValid(saved.savedAtMs, saved.expiresAtMs, nowMs)

    fun clockSafe(nowMs: Long, previouslySeenMs: Long): Boolean =
        nowMs > 0 && previouslySeenMs > 0 &&
            (nowMs >= previouslySeenMs ||
                previouslySeenMs - nowMs <= MAX_CLOCK_ROLLBACK_MS)

    /**
     * A second Save never lengthens an unexpired existing identity's time.
     * Explicitly choosing fewer days shortens it; expiration/new consent
     * permits a new record, but background reads/reconnects never renew it.
     */
    fun retainedExpiry(existingExpiryMs: Long?, requestedExpiryMs: Long): Long =
        if (existingExpiryMs == null) requestedExpiryMs
        else minOf(existingExpiryMs, requestedExpiryMs)
}
