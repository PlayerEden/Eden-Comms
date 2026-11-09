package com.edenpractical.comms.privacy

import org.junit.Assert.*
import org.junit.Test

class PeerMemoryPolicyTest {
    private val now = 1_783_900_000_000L
    private val pin = ByteArray(32) { (it + 1).toByte() }

    @Test fun sessionOnlyDoesNotGenerateExpiryAndBoundsAreOneToThirtyDays() {
        assertNull(PeerTrustExpiry.expiresAtOrNull(now, null))
        assertEquals(now + PeerMemoryPolicy.ONE_DAY_MS,
            PeerMemoryPolicy.expiryForExplicitChoice(now, 1))
        assertEquals(now + PeerMemoryPolicy.MAX_AGE_MS,
            PeerMemoryPolicy.expiryForExplicitChoice(now, 30))
        assertNull(PeerMemoryPolicy.expiryForExplicitChoice(now, 0))
        assertNull(PeerMemoryPolicy.expiryForExplicitChoice(now, 31))
        assertNull(PeerMemoryPolicy.expiryForExplicitChoice(0L, 1))
        assertNull(PeerMemoryPolicy.expiryForExplicitChoice(Long.MAX_VALUE - 2L, 30))
    }

    @Test fun validRecordExpiresAtExactBoundaryAndRejectsFutureOrForgedLifetime() {
        val expiry = now + 7L * PeerMemoryPolicy.ONE_DAY_MS
        val row = PeerMemoryPolicy.Saved(pin, now, expiry)
        assertTrue(PeerMemoryPolicy.valid(row, now))
        assertTrue(PeerMemoryPolicy.valid(row, expiry - 1))
        assertFalse(PeerMemoryPolicy.valid(row, expiry))
        assertFalse(PeerMemoryPolicy.valid(row, now - 1))
        assertFalse(PeerMemoryPolicy.valid(row.copy(
            expiresAtMs = now + PeerMemoryPolicy.MAX_AGE_MS + 1L
        ), now))
        assertFalse(PeerMemoryPolicy.valid(row.copy(pin = ByteArray(32)), now))
        assertFalse(PeerMemoryPolicy.valid(row.copy(pin = byteArrayOf(1)), now))
    }

    @Test fun savingAgainCannotRenewAndShorteningWorks() {
        val original = now + PeerMemoryPolicy.ONE_DAY_MS
        val later = now + PeerMemoryPolicy.MAX_AGE_MS
        assertEquals(original, PeerMemoryPolicy.retainedExpiry(original, later))
        assertEquals(original, PeerMemoryPolicy.retainedExpiry(later, original))
        assertEquals(later, PeerMemoryPolicy.retainedExpiry(null, later))
    }

    @Test fun clockRollbackAndUnknownPeerFailClosed() {
        assertTrue(PeerMemoryPolicy.clockSafe(now, now))
        assertTrue(PeerMemoryPolicy.clockSafe(now - 5_000L, now))
        assertFalse(PeerMemoryPolicy.clockSafe(now - 60_001L, now))
        assertFalse(PeerMemoryPolicy.clockSafe(0L, now))
        assertFalse(PeerMemoryPolicy.clockSafe(now, 0L))
        assertEquals(8, PeerMemoryPolicy.MAX_PEERS)
        assertEquals(32, PeerMemoryPolicy.PIN_BYTES)
    }
}
