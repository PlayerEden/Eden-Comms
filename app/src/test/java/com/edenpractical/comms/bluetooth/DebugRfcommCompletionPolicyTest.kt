package com.edenpractical.comms.bluetooth

import com.edenpractical.comms.bluetooth.DebugRfcommAttemptGate.End
import com.edenpractical.comms.bluetooth.DebugRfcommAttemptGate.Phase
import com.edenpractical.comms.model.ReleaseCapabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugRfcommCompletionPolicyTest {
    @Test fun approvedTlsSessionCannotReportSuccessAfterForegroundOrPermissionLoss() {
        assertEquals(End.CONSENT_CONFIRMED,
            DebugRfcommCompletionPolicy.endAfterConsent(true, true))
        assertEquals(End.BACKGROUND,
            DebugRfcommCompletionPolicy.endAfterConsent(false, true))
        assertEquals(End.PERMISSION_LOST,
            DebugRfcommCompletionPolicy.endAfterConsent(true, false))
        // Background has deterministic priority if both checks are negative.
        assertEquals(End.BACKGROUND,
            DebugRfcommCompletionPolicy.endAfterConsent(false, false))
    }

    @Test fun consentButtonsRequireCurrentOpenLinkAndBothLivePrerequisites() {
        for (phase in Phase.entries) {
            for (foreground in listOf(false, true)) {
                for (permitted in listOf(false, true)) {
                    val expected = phase == Phase.LINK_OPEN && foreground && permitted
                    assertEquals(expected,
                        DebugRfcommCompletionPolicy.mayChooseConsent(
                            phase, foreground, permitted
                        ))
                }
            }
        }
    }

    @Test fun debugOnlyStatusPolicyNeverEnablesProductionCommunication() {
        assertFalse(ReleaseCapabilities.CAN_CONNECT)
        assertFalse(ReleaseCapabilities.CAN_SEND_MESSAGES)
        assertFalse(ReleaseCapabilities.CAN_TRANSMIT_VOICE)
        assertTrue(DebugRfcommCompletionPolicy.mayChooseConsent(
            Phase.LINK_OPEN, true, true
        ))
    }
}
