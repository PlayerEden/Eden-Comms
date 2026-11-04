package com.edenpractical.comms.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PeerTrustExpiryTest {
    private val now = 1_800_000_000_000L
    private val day = 24L * 60L * 60L * 1000L

    @Test fun sessionOnlyWritesNoExpiryRecord() {
        assertNull(PeerTrustExpiry.expiresAtOrNull(now, null))
    }

    @Test fun everyDayFromOneToThirtyIsAccepted() {
        for (days in 1..30) {
            assertEquals(now + days * day, PeerTrustExpiry.expiresAtOrNull(now, days))
        }
    }

    @Test fun durationCannotBeZeroOrLongerThanThirtyDays() {
        for (days in listOf(-1, 0, 31, 365)) {
            assertThrows(IllegalArgumentException::class.java) {
                PeerTrustExpiry.expiresAtOrNull(now, days)
            }
        }
    }

    @Test fun expiresAtBoundaryWithoutAutomaticRenewal() {
        val expiry = PeerTrustExpiry.expiresAtOrNull(now, 7)!!
        assertTrue(PeerTrustExpiry.isValid(now, expiry, expiry - 1))
        assertFalse(PeerTrustExpiry.isValid(now, expiry, expiry))
        assertFalse(PeerTrustExpiry.isValid(now, expiry, expiry + day))
    }

    @Test fun clockRollbackDoesNotExtendTrust() {
        val expiry = PeerTrustExpiry.expiresAtOrNull(now, 1)!!
        assertFalse(PeerTrustExpiry.isValid(now, expiry, now - 1))
    }
}
