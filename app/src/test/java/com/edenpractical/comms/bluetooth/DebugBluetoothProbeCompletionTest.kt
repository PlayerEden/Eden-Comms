package com.edenpractical.comms.bluetooth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** F2-E unit policy, NOT evidence that the synthetic caller ran real TLS. */
class DebugBluetoothProbeCompletionTest {
    private fun completion(
        host: Boolean = true,
        issued: Long = 100L,
        expires: Long = 200L
    ) = DebugBluetoothProbeCompletion.afterVerifiedDiagnostic(
        hostRole = host,
        issuedAtSeconds = issued,
        qrExpiresAtSeconds = expires
    )

    @Test fun matchingFreshRoleAndPrerequisitesCanBeAcknowledgedExactlyOnce() {
        val receipt = completion()
        assertTrue(receipt.acknowledgeOnce(
            expectedHostRole = true,
            observedAtSeconds = 102,
            foreground = true,
            connectPermission = true
        ))
        assertFalse(receipt.acknowledgeOnce(true, 102, true, true))
        receipt.close()
        assertFalse(receipt.acknowledgeOnce(true, 103, true, true))
    }

    @Test fun joinRoleRequiresJoinObservationNotHost() {
        val receipt = completion(host = false)
        assertTrue(receipt.acknowledgeOnce(false, 100, true, true))
        val mismatch = completion(host = false)
        assertFalse(mismatch.acknowledgeOnce(true, 101, true, true))
        // Failed consumption is permanent, cannot retry by changing the role.
        assertFalse(mismatch.acknowledgeOnce(false, 101, true, true))
    }

    @Test fun backgroundPermissionLossOrExpiryBurnsReceipt() {
        val invalid = listOf(
            Triple(101L, false, true),
            Triple(101L, true, false),
            Triple(104L, true, true),
            Triple(99L, true, true)
        )
        for ((now, foreground, permitted) in invalid) {
            val receipt = completion()
            assertFalse(receipt.acknowledgeOnce(
                expectedHostRole = true,
                observedAtSeconds = now,
                foreground = foreground,
                connectPermission = permitted
            ))
            assertFalse(receipt.acknowledgeOnce(true, 101, true, true))
        }

        val qrExpired = completion(issued = 100, expires = 101)
        assertFalse(qrExpired.acknowledgeOnce(true, 102, true, true))
        assertFalse(qrExpired.acknowledgeOnce(true, 100, true, true))
    }

    @Test fun earlyCloseAndInvalidClockCannotBeReactivated() {
        val earlyClose = completion()
        earlyClose.close()
        assertFalse(earlyClose.acknowledgeOnce(true, 100, true, true))
        earlyClose.close()

        for ((issued, expires) in listOf(
            -1L to 100L,
            1L to 0L,
            200L to 100L
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                completion(issued = issued, expires = expires)
            }
        }
    }

    @Test fun overflowSafeDeadlineAndUpperTimeBoundary() {
        val receipt = completion(issued = Long.MAX_VALUE - 2, expires = Long.MAX_VALUE)
        assertTrue(receipt.acknowledgeOnce(true, Long.MAX_VALUE, true, true))
        assertFalse(receipt.acknowledgeOnce(true, Long.MAX_VALUE, true, true))
        assertTrue(DebugBluetoothProbeCompletion.MAX_HANDOFF_AGE_SECONDS == 3L)
    }
}
