package com.edenpractical.comms.pairing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM screen-admission tests; not Android lifecycle integration,
 * physical camera/TLS connectivity or production identity authorization.
 */
class PairingRestartAdmissionTest {
    private val policy = PairingRestartAdmission

    @Test fun validForegroundConsentCompletionMayContinueOnlyChosenRole() {
        for (role in listOf("show", "scan")) {
            assertTrue(policy.mayContinueConsent(
                7, 7, role, role, "choose", true
            ))
            assertFalse(policy.mayContinueConsent(
                7, 7, role, role, "choose", false
            ))
            assertFalse(policy.mayContinueConsent(
                7, 8, role, role, "choose", true
            ))
            assertFalse(policy.mayContinueConsent(
                7, 7, role, role, "result", true
            ))
            assertFalse(policy.mayContinueConsent(
                7, 7, role, role, "scanHost", true
            ))
            assertFalse(policy.mayContinueConsent(
                7, 7, role, null, "choose", true
            ))
        }
        assertFalse(policy.mayContinueConsent(7, 7, "show", "scan", "choose", true))
        assertFalse(policy.mayContinueConsent(7, 7, "scan", "show", "choose", true))
        assertFalse(policy.mayContinueConsent(7, 7, "other", "other", "choose", true))
        assertFalse(policy.mayContinueConsent(7, 7, null, null, "choose", true))
    }

    @Test fun cancelledScanCallbackCannotArriveIntoIdenticalStageNextAttempt() {
        for (stage in listOf("scanHost", "scanReply")) {
            assertTrue(policy.mayAcceptScan(12, 12, 5, 5, stage))
            assertFalse(policy.mayAcceptScan(12, 13, 5, 5, stage))
            assertFalse(policy.mayAcceptScan(12, 12, 5, 6, stage))
            assertFalse(policy.mayAcceptScan(12, 13, 5, 6, stage))
            assertFalse(policy.mayAcceptScan(12, 12, 5, 5, "choose"))
            assertFalse(policy.mayAcceptScan(12, 12, 5, 5, "result"))
        }
        assertFalse(policy.mayAcceptScan(1, 1, 2, 2, "approval"))
        assertFalse(policy.mayAcceptScan(1, 1, 2, 2, "demo"))
        assertFalse(policy.mayAcceptScan(1, 1, 2, 2, "showReply"))
    }

    @Test fun resetAndBackgroundInvalidateBothIndependentCallbackFamilies() {
        val oldAttempt = 40
        val oldRevision = 90
        assertTrue(policy.mayAcceptScan(oldAttempt, oldAttempt,
            oldRevision, oldRevision, "scanHost"))
        assertTrue(policy.mayContinueConsent(oldAttempt, oldAttempt,
            "scan", "scan", "choose", true))

        val resetAttempt = oldAttempt + 1
        val resetRevision = oldRevision + 1
        assertFalse(policy.mayAcceptScan(oldAttempt, resetAttempt,
            oldRevision, resetRevision, "scanHost"))
        assertFalse(policy.mayContinueConsent(oldAttempt, resetAttempt,
            "scan", null, "choose", true))
        assertFalse(policy.mayContinueConsent(oldAttempt, oldAttempt,
            "scan", "scan", "choose", false))
        assertTrue(policy.mayAcceptScan(resetAttempt, resetAttempt,
            resetRevision, resetRevision, "scanHost"))
    }
}
