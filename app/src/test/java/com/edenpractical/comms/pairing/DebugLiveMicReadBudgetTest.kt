package com.edenpractical.comms.pairing

import org.junit.Assert.*
import org.junit.Test

class DebugLiveMicReadBudgetTest {
    @Test fun zeroByteReadsDoNotRefreshStallTimer() {
        val budget = DebugLiveMicReadBudget(10_000)
        assertTrue(budget.mayContinue(10_000))
        assertFalse(budget.observedBytes(0, 10_020))
        assertTrue(budget.mayContinue(10_449))
        assertFalse(budget.mayContinue(10_450))
        assertFalse(budget.observedBytes(640, 10_451))
    }

    @Test fun successfulPartialReadsRefreshOnlyStallDeadline() {
        val budget = DebugLiveMicReadBudget(1_000)
        assertTrue(budget.observedBytes(73, 1_400))
        assertTrue(budget.mayContinue(1_800))
        assertTrue(budget.observedBytes(567, 1_820))
        assertTrue(budget.mayContinue(2_200))
        assertFalse(budget.mayContinue(2_270))
        assertFalse(budget.observedBytes(1, 2_271))
    }

    @Test fun repeatedProgressNeverExtendsHardThreeSecondTurn() {
        val budget = DebugLiveMicReadBudget(200)
        var now = 200L
        while (now < 3_200L) {
            assertTrue(budget.observedBytes(1, now))
            now += 100L
        }
        assertFalse(budget.mayContinue(3_200))
        assertFalse(budget.observedBytes(640, 3_201))
    }

    @Test fun backwardClockAfterAValidReadClosesImmediately() {
        val budget = DebugLiveMicReadBudget(90_000)
        assertTrue(budget.observedBytes(320, 90_020))
        assertFalse(budget.mayContinue(90_019))
        assertFalse(budget.mayContinue(90_100))
    }

    @Test fun negativeReadCountCannotReviveTurn() {
        val budget = DebugLiveMicReadBudget(1)
        assertFalse(budget.observedBytes(-3, 2))
        assertTrue(budget.mayContinue(3))
        budget.stop()
        assertFalse(budget.mayContinue(4))
        assertFalse(budget.observedBytes(320, 5))
    }

    @Test fun explicitStopIsTerminalAndIdempotent() {
        val budget = DebugLiveMicReadBudget(1_000)
        assertTrue(budget.observedBytes(640, 1_001))
        budget.stop()
        budget.stop()
        assertFalse(budget.mayContinue(1_002))
    }

    @Test fun monotonicTimeIsRequiredFromFirstCall() {
        val invalid = DebugLiveMicReadBudget(-1)
        assertFalse(invalid.mayContinue(0))
        val budget = DebugLiveMicReadBudget(200)
        assertFalse(budget.mayContinue(199))
        assertFalse(budget.observedBytes(50, 200))
    }

    @Test fun constantsKeepPollBoundedWithoutBusySpin() {
        assertEquals(3_000L, DebugLiveMicReadBudget.MAX_TURN_MS)
        assertTrue(DebugLiveMicReadBudget.MAX_SILENCE_MS in 100L..800L)
        assertTrue(DebugLiveMicReadBudget.RETRY_PAUSE_MS in 1L..20L)
        assertTrue(DebugLiveMicReadBudget.RETRY_PAUSE_MS <
            DebugLiveMicReadBudget.MAX_SILENCE_MS)
    }
}
