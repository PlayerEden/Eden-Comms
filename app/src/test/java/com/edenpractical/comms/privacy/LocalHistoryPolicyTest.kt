package com.edenpractical.comms.privacy

import org.junit.Assert.*
import org.junit.Test

class LocalHistoryPolicyTest {
    private val policy = LocalHistoryPolicy
    private val epoch = 1_783_900_000_000L

    @Test fun noSavingUnlessExplicitAllowedDuration() {
        assertEquals(LocalHistoryPolicy.SESSION_ONLY, policy.choices.first().durationMs)
        assertNull(policy.expiry(epoch, LocalHistoryPolicy.SESSION_ONLY))
        assertNull(policy.expiry(epoch, 42L))
        assertNull(policy.expiry(epoch, -1L))
        assertEquals(epoch + policy.ONE_HOUR, policy.expiry(epoch, policy.ONE_HOUR))
    }

    @Test fun expiryBoundedAndAbsoluteNoRenewalOnRead() {
        val expiry = policy.expiry(epoch, policy.THIRTY_DAYS)!!
        assertEquals(epoch + policy.THIRTY_DAYS, expiry)
        assertEquals(null, policy.expiry(Long.MAX_VALUE - 500L, policy.ONE_HOUR))
        val saved = LocalHistoryPolicy.SavedMessage(
            LocalHistoryPolicy.Direction.RECEIVED_HERE, 1L,
            "Test message", epoch, expiry
        )
        assertEquals(listOf(saved), policy.visible(listOf(saved), epoch))
        assertTrue(policy.visible(listOf(saved), expiry).isEmpty())
        assertEquals(expiry, saved.expiresAtMs) // Reading never modifies deadline.
    }

    @Test fun shortPeriodsCanOnlyMakePreviouslySavedRecordsExpireEarlier() {
        val item = LocalHistoryPolicy.SavedMessage(
            LocalHistoryPolicy.Direction.WRITTEN_HERE, 5L,
            "Hello", epoch, epoch + policy.THIRTY_DAYS
        )
        val shorter = policy.shorten(listOf(item), epoch + 500L, policy.ONE_DAY).single()
        assertEquals(epoch + policy.ONE_DAY, shorter.expiresAtMs)
        assertEquals(shorter, policy.shorten(listOf(shorter), epoch + 900L,
            policy.THIRTY_DAYS).single())
        assertTrue(policy.shorten(listOf(item), epoch,
            policy.SESSION_ONLY).isEmpty())
        assertTrue(policy.shorten(listOf(item), epoch + policy.THIRTY_DAYS,
            policy.ONE_DAY).isEmpty())
    }

    @Test fun rollbackExpiredMalformedAndOverlongRecordsFailClosed() {
        assertTrue(policy.clockSafe(epoch, epoch))
        assertTrue(policy.clockSafe(epoch - 30_000, epoch))
        assertFalse(policy.clockSafe(epoch - 60_001, epoch))
        assertFalse(policy.clockSafe(-1L, epoch))
        assertFalse(policy.clockSafe(epoch, 0L))
        val invalid = listOf(
            LocalHistoryPolicy.SavedMessage(
                LocalHistoryPolicy.Direction.RECEIVED_HERE, 1L,
                "Hello", epoch, epoch + policy.THIRTY_DAYS + 1L
            ),
            LocalHistoryPolicy.SavedMessage(
                LocalHistoryPolicy.Direction.RECEIVED_HERE, 1L,
                "x".repeat(281), epoch, epoch + policy.ONE_DAY
            ),
            LocalHistoryPolicy.SavedMessage(
                LocalHistoryPolicy.Direction.RECEIVED_HERE, 0L,
                "Hello", epoch, epoch + policy.ONE_DAY
            ),
            LocalHistoryPolicy.SavedMessage(
                LocalHistoryPolicy.Direction.RECEIVED_HERE, 3L,
                "Hello\u0000", epoch, epoch + policy.ONE_DAY
            )
        )
        invalid.forEach { assertFalse(policy.valid(it)) }
        assertTrue(policy.visible(invalid, epoch).isEmpty())
    }

    @Test fun storedRowsAreCappedAndObservedFutureRecordsAreNotYetVisible() {
        val valid = (1L..100L).map {
            LocalHistoryPolicy.SavedMessage(
                LocalHistoryPolicy.Direction.WRITTEN_HERE,
                it, "test", epoch, epoch + policy.ONE_DAY
            )
        }
        assertEquals(LocalHistoryPolicy.MAX_RECORDS_PER_PEER,
            policy.visible(valid, epoch).size)
        assertTrue(policy.visible(valid, epoch - 1L).isEmpty())
    }
}
