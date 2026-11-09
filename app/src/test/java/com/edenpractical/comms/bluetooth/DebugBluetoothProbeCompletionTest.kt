package com.edenpractical.comms.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F2-L real diagnostic completion clock policy. Synthetic construction of
 * this marker is NOT proof of actual TLS, QR binding, or two human approvals.
 */
class DebugBluetoothProbeCompletionTest {
    private fun completion(
        host: Boolean = true,
        issued: Long = 100L,
        expires: Long = 200L,
        issuedElapsedMs: Long = 10_000L
    ) = DebugBluetoothProbeCompletion.afterVerifiedDiagnostic(
        hostRole = host,
        issuedAtSeconds = issued,
        qrExpiresAtSeconds = expires,
        issuedAtElapsedMs = issuedElapsedMs
    )

    private fun consumed(
        receipt: DebugBluetoothProbeCompletion,
        host: Boolean = true,
        wall: Long = 101L,
        elapsed: Long = 11_000L,
        foreground: Boolean = true,
        permitted: Boolean = true
    ): Boolean = receipt.acknowledgeOnce(
        expectedHostRole = host,
        observedAtSeconds = wall,
        observedAtElapsedMs = elapsed,
        foreground = foreground,
        connectPermission = permitted
    )

    @Test fun freshMatchingRoleAndBothClocksCanCompleteExactlyOnce() {
        val receipt = completion()
        assertTrue(consumed(receipt, wall = 102, elapsed = 12_000))
        assertFalse(consumed(receipt, wall = 102, elapsed = 12_000))
        receipt.close()
        assertFalse(consumed(receipt, wall = 102, elapsed = 12_000))
        assertEquals(3_000L, DebugBluetoothProbeCompletion.MAX_HANDOFF_AGE_MS)
    }

    @Test fun hostJoinMismatchBurnsReceiptWithoutRetry() {
        val joiner = completion(host = false)
        assertFalse(consumed(joiner, host = true))
        assertFalse(consumed(joiner, host = false))
        val correct = completion(host = false)
        assertTrue(consumed(correct, host = false))
    }

    @Test fun monotonicAgeRejectsWallClockRewindThatLooksFreshAgain() {
        // Human/device manually restores the wall clock to the original
        // issued time after the real 3-second window has already elapsed.
        val stale = completion(issuedElapsedMs = 50_000)
        assertFalse(consumed(stale, wall = 100, elapsed = 54_001))
        assertFalse(consumed(stale, wall = 101, elapsed = 50_100))
    }

    @Test fun elapsedClockRollbackCannotReviveReceipt() {
        val receipt = completion()
        assertFalse(consumed(receipt, wall = 101, elapsed = 9_999))
        assertFalse(consumed(receipt, wall = 101, elapsed = 10_100))
    }

    @Test fun independentQrWallExpiryStillCheckedWhenMonotonicTimeIsFresh() {
        val qr = completion(issued = 100, expires = 101, issuedElapsedMs = 5_000)
        assertFalse(consumed(qr, wall = 102, elapsed = 5_100))
        val wallMoved = completion(issuedElapsedMs = 8_000)
        assertFalse(consumed(wallMoved, wall = 104, elapsed = 8_100))
        val wallRolledBack = completion(issuedElapsedMs = 8_000)
        assertFalse(consumed(wallRolledBack, wall = 99, elapsed = 8_100))
    }

    @Test fun foregroundPermissionAndMonotonicTimeoutEachBurnReceipt() {
        for (triple in listOf(
            Triple(11_000L, false, true),
            Triple(11_000L, true, false),
            Triple(13_001L, true, true)
        )) {
            val receipt = completion()
            assertFalse(consumed(
                receipt,
                wall = 101,
                elapsed = triple.first,
                foreground = triple.second,
                permitted = triple.third
            ))
            assertFalse(consumed(receipt, wall = 101, elapsed = 10_100))
        }
    }

    @Test fun exactThreeSecondUpperBoundIsAllowedOnlyOnce() {
        val receipt = completion()
        assertTrue(consumed(receipt, wall = 103, elapsed = 13_000))
        assertFalse(consumed(receipt, wall = 103, elapsed = 13_000))
    }

    @Test fun closedOrInvalidReceiptCanNeverBecomeAuthorizing() {
        val receipt = completion()
        receipt.close()
        receipt.close()
        assertFalse(consumed(receipt))
        for ((wall, expiry, elapsed) in listOf(
            Triple(-1L, 100L, 1L),
            Triple(1L, 0L, 1L),
            Triple(200L, 100L, 1L),
            Triple(100L, 200L, -1L)
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                completion(issued = wall, expires = expiry, issuedElapsedMs = elapsed)
            }
        }
    }

    @Test fun longMaxWallAndElapsedDoNotOverflowSubtractions() {
        val issued = Long.MAX_VALUE - 2
        val elapsed = Long.MAX_VALUE - 3_000L
        val receipt = completion(issued = issued, expires = Long.MAX_VALUE,
            issuedElapsedMs = elapsed)
        assertTrue(consumed(receipt,
            wall = Long.MAX_VALUE, elapsed = Long.MAX_VALUE))
        val elapsedTooOld = completion(issued = issued, expires = Long.MAX_VALUE,
            issuedElapsedMs = elapsed - 1)
        assertFalse(consumed(elapsedTooOld,
            wall = Long.MAX_VALUE, elapsed = Long.MAX_VALUE))
    }
}
