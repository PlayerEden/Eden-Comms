package com.edenpractical.comms.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingFreshRetryTest {
    @Test fun presenterCanRepeatOnlyTheNonSensitiveShowRole() {
        val choice = requireNotNull(PairingFreshRetry.forLastRole(LocalQaReport.Role.PRESENTER))
        assertEquals("show", choice.role)
        assertTrue(choice.buttonLabel.contains("new code"))
        assertFalse(choice.buttonLabel.contains("reconnect", ignoreCase = true))
    }

    @Test fun joinerCanRepeatOnlyTheNonSensitiveScanRole() {
        val choice = requireNotNull(PairingFreshRetry.forLastRole(LocalQaReport.Role.JOINER))
        assertEquals("scan", choice.role)
        assertTrue(choice.buttonLabel.contains("new code"))
        assertFalse(choice.buttonLabel.contains("approved", ignoreCase = true))
    }

    @Test fun neverOffersRetryWithoutARecordedRole() {
        assertNull(PairingFreshRetry.forLastRole(LocalQaReport.Role.NOT_SELECTED))
    }
}
