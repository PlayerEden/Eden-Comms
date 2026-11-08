package com.edenpractical.comms.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugBluetoothContinuationBudgetTest {
    private fun observations() = DebugBluetoothContinuationBudget.Observations(
        peerPinChecked = true,
        qrChallengeChecked = true,
        exporterChecked = true,
        bilateralApprovals = true,
        bilateralConfirmations = true,
        foreground = true,
        connectPermission = true
    )

    @Test fun everyObservationIsRequiredButStillNotAnAuthorization() {
        val good = observations()
        val missing = listOf(
            good.copy(peerPinChecked = false),
            good.copy(qrChallengeChecked = false),
            good.copy(exporterChecked = false),
            good.copy(bilateralApprovals = false),
            good.copy(bilateralConfirmations = false),
            good.copy(foreground = false),
            good.copy(connectPermission = false)
        )
        for (ob in missing) {
            val budget = DebugBluetoothContinuationBudget()
            assertFalse(budget.beginObservation(1, 100, ob))
            assertEquals(DebugBluetoothContinuationBudget.Phase.NOT_STARTED, budget.snapshot())
        }
        val budget = DebugBluetoothContinuationBudget()
        assertTrue(budget.beginObservation(1, 100, good))
        assertFalse(budget.beginObservation(2, 200, good))
        assertTrue(budget.stillObserved(1, 101, true, true))
    }

    @Test fun onlyMatchingGenerationAndFiniteMonotonicDeadlineSurvive() {
        val budget = DebugBluetoothContinuationBudget(maxLifetimeMs = 5)
        assertFalse(budget.beginObservation(0, 1, observations()))
        assertFalse(budget.beginObservation(1, -1, observations()))
        assertTrue(budget.beginObservation(9, 50, observations()))
        assertFalse(budget.stillObserved(10, 51, true, true))
        assertTrue(budget.stillObserved(9, 54, true, true))
        assertFalse(budget.stillObserved(9, 55, true, true))
        assertEquals(DebugBluetoothContinuationBudget.Phase.TERMINATED, budget.snapshot())
        assertFalse(budget.beginObservation(11, 60, observations()))
        assertFalse(budget.stillObserved(9, 52, true, true))
    }

    @Test fun clockMovingBackwardEndsObservationAndDoesNotRearm() {
        val budget = DebugBluetoothContinuationBudget(maxLifetimeMs = 100)
        assertTrue(budget.beginObservation(7, 500, observations()))
        assertFalse(budget.stillObserved(7, 499, true, true))
        assertEquals(DebugBluetoothContinuationBudget.Phase.TERMINATED, budget.snapshot())
        assertFalse(budget.beginObservation(7, 501, observations()))
    }

    @Test fun rollbackAfterMultipleValidObservationsAlsoTerminatesPermanently() {
        val budget = DebugBluetoothContinuationBudget(maxLifetimeMs = 100)
        assertTrue(budget.beginObservation(77, 500, observations()))
        assertTrue(budget.stillObserved(77, 540, true, true))
        assertTrue(budget.stillObserved(77, 565, true, true))
        // A rollback to 555 is still after the original 500 start, but is
        // BEFORE the last valid observation and must fail closed.
        assertFalse(budget.stillObserved(77, 555, true, true))
        assertEquals(DebugBluetoothContinuationBudget.Phase.TERMINATED, budget.snapshot())
        assertFalse(budget.stillObserved(77, 570, true, true))
        assertFalse(budget.beginObservation(78, 590, observations()))
    }

    @Test fun backgroundPermissionLossAndUserEndAreTerminal() {
        val budget = DebugBluetoothContinuationBudget()
        assertTrue(budget.beginObservation(3, 10, observations()))
        assertFalse(budget.stillObserved(3, 11, false, true))
        assertFalse(budget.stillObserved(3, 12, true, true))
        assertFalse(budget.terminate(3))

        val permissionLost = DebugBluetoothContinuationBudget()
        assertTrue(permissionLost.beginObservation(4, 10, observations()))
        assertFalse(permissionLost.stillObserved(4, 11, true, false))
        assertFalse(permissionLost.beginObservation(4, 12, observations()))

        val ended = DebugBluetoothContinuationBudget()
        assertTrue(ended.beginObservation(5, 10, observations()))
        assertFalse(ended.terminate(4))
        assertTrue(ended.terminate(5))
        assertFalse(ended.stillObserved(5, 11, true, true))
        assertFalse(ended.beginObservation(6, 12, observations()))
    }

    @Test fun invalidClockAndLifetimeCannotProduceUnboundedLease() {
        for (length in listOf(-1L, 0L, 120_001L, Long.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) {
                DebugBluetoothContinuationBudget(length)
            }
        }
        val budget = DebugBluetoothContinuationBudget()
        assertFalse(budget.beginObservation(1, Long.MAX_VALUE, observations()))
        assertTrue(budget.beginObservation(1, 1, observations()))
        assertFalse(budget.stillObserved(1, -1, true, true))
    }
}
