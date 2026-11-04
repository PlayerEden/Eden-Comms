package com.edenpractical.comms.privacy

/**
 * Policy only — deliberately NO persistence or trust-list implementation yet.
 * Authentication must pass independent security review before storing peers.
 *
 * Each user's choice applies only to their own phone, separately from the
 * other user's selection and separately from message retention.
 */
internal object PeerTrustExpiry {
    const val MAX_DAYS = 30
    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    /** null means session-only: do not write any record to disk. */
    fun expiresAtOrNull(verifiedAtMillis: Long, rememberDays: Int?): Long? {
        if (rememberDays == null) return null
        require(verifiedAtMillis > 0L) { "Verification time must be positive" }
        require(rememberDays in 1..MAX_DAYS) { "Trust retention exceeds policy" }
        return Math.addExact(verifiedAtMillis, rememberDays.toLong() * DAY_MILLIS)
    }

    /** Never treat wall-clock rollback as a valid extension. */
    fun isValid(storedAtMillis: Long, expiryMillis: Long, nowMillis: Long): Boolean =
        storedAtMillis > 0L && expiryMillis > storedAtMillis &&
            nowMillis >= storedAtMillis && nowMillis < expiryMillis
}
