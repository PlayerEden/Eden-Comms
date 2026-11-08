package com.edenpractical.comms.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class DebugLocalPttPlaybackBudgetTest {
    @Test fun usableDeadlineCoversAtMostFiveSecondLocalSample() {
        val budget = DebugLocalPttPlaybackBudget(10_000L)
        assertTrue(budget.mayContinue(10_000L))
        assertTrue(budget.mayContinue(15_000L))
        assertTrue(budget.mayContinue(17_999L))
        assertFalse(budget.mayContinue(18_000L))
        assertFalse(budget.mayContinue(17_999L))
        assertEquals(8_000L, DebugLocalPttPlaybackBudget.MAX_PLAYBACK_MILLIS)
        assertEquals(10L, DebugLocalPttPlaybackBudget.RETRY_PAUSE_MILLIS)
    }

    @Test fun monotonicClockRollbackFailsClosedEvenWhenWithinDeadline() {
        val budget = DebugLocalPttPlaybackBudget(1_000L)
        assertTrue(budget.mayContinue(1_100L))
        assertFalse(budget.mayContinue(1_099L))
        assertFalse(budget.mayContinue(1_200L))
    }

    @Test fun explicitStopIsTerminal() {
        val budget = DebugLocalPttPlaybackBudget(0L)
        budget.close()
        assertFalse(budget.mayContinue(1L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeStartClockCannotBeAccepted() {
        DebugLocalPttPlaybackBudget(-1L)
    }
}
