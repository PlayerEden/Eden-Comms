package com.edenpractical.comms.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugLocalDisappearingPolicyTest {
    private val direction = DebugTextTranscript.Direction.RECEIVED_HERE

    @Test fun timerChoicesAreBoundedAndDisabledByDefault() {
        val policy = DebugLocalDisappearingPolicy
        assertEquals(listOf(0L, 60_000L, 300_000L, 900_000L),
            policy.choices.map { it.durationMs })
        assertNull(policy.deadline(50L, 0L))
        assertTrue(!policy.supports(-1L))
        assertTrue(!policy.supports(86_400_000L))
    }

    @Test fun actualInMemoryMessagesExpireAtMonotonicDeadlineAndKeepSequence() {
        val original = DebugTextTranscript.fresh().ready()
        val received = original.record(direction, 1L, "short message",
            observedAtElapsedMs = 1_000L, disappearAfterMs = 60_000L)
        assertEquals(61_000L, received.nextExpiryElapsedMs())
        assertSame(received, received.pruneExpired(60_999L))
        val elapsed = received.pruneExpired(61_000L)
        assertTrue(elapsed.visible.isEmpty())
        assertEquals(1, elapsed.receivedHere)
        assertNull(elapsed.nextExpiryElapsedMs())
        assertSame(elapsed, elapsed.record(direction, 1L, "replayed",
            61_001L, 60_000L))
        val next = elapsed.record(direction, 2L, "fresh", 61_010L, 60_000L)
        assertEquals("fresh", next.visible.single().text)
        assertEquals(2, next.receivedHere)
    }

    @Test fun shorterChoiceShortensExistingButLongerChoiceNeverRevivesThem() {
        val started = DebugTextTranscript.fresh().ready()
            .record(direction, 1L, "one", 1_000L, 900_000L)
        assertEquals(901_000L, started.nextExpiryElapsedMs())
        val shortened = started.shortenVisibleExpiry(60_000L)
        assertEquals(61_000L, shortened.nextExpiryElapsedMs())
        val laterSelection = shortened.shortenVisibleExpiry(900_000L)
        assertSame(shortened, laterSelection)
        val off = shortened.shortenVisibleExpiry(0L)
        assertSame(shortened, off)
        val expired = off.pruneExpired(61_000L)
        assertTrue(expired.visible.isEmpty())
        assertSame(expired, expired.shortenVisibleExpiry(300_000L))
        assertTrue(expired.visible.isEmpty())
    }

    @Test fun enablingTimerAfterOffShortensExistingLocalCopies() {
        val off = DebugTextTranscript.fresh().ready()
            .record(direction, 1L, "visible", 20_000L)
        assertNull(off.nextExpiryElapsedMs())
        val enabled = off.shortenVisibleExpiry(60_000L)
        assertEquals(80_000L, enabled.nextExpiryElapsedMs())
        assertTrue(enabled.pruneExpired(80_001L).visible.isEmpty())
        assertEquals("visible", off.visible.single().text) // immutable old state
    }

    @Test fun clockRollbackAndOverflowFailClosed() {
        val state = DebugTextTranscript.fresh().ready()
            .record(direction, 1L, "local", 40_000L, 60_000L)
        assertTrue(state.pruneExpired(39_999L).visible.isEmpty())
        val over = DebugTextTranscript.fresh().ready()
            .record(direction, 1L, "overflow", Long.MAX_VALUE, 60_000L)
        assertTrue(over.pruneExpired(Long.MAX_VALUE).visible.isEmpty())
        val negative = DebugTextTranscript.fresh().ready()
            .record(direction, 1L, "negative", -1L, 60_000L)
        assertTrue(negative.pruneExpired(-1L).visible.isEmpty())
    }

    @Test fun terminalCleanupDropsContentAndNoTimerCanRestoreIt() {
        val active = DebugTextTranscript.fresh().ready()
            .record(direction, 1L, "ephemeral", 1_000L, 60_000L)
        val erased = active.clearVisible()
        assertTrue(erased.visible.isEmpty())
        assertNull(erased.nextExpiryElapsedMs())
        val ended = erased.finish()
        assertSame(ended, ended.pruneExpired(1_000_000L))
        assertSame(ended, ended.record(direction, 2L, "late", 2_000L, 60_000L))
        assertSame(ended, ended.shortenVisibleExpiry(60_000L))
    }
}
