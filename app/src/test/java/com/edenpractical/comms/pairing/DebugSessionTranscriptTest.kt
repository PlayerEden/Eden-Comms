package com.edenpractical.comms.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugSessionTranscriptTest {
    private val local = DebugSessionTranscript.Direction.WRITTEN_TO_TLS
    private val remote = DebugSessionTranscript.Direction.RECEIVED_HERE

    @Test fun noPhraseOrCounterBeforeAuthenticatedDebugReady() {
        val waiting = DebugSessionTranscript.fresh()
        assertEquals(DebugSessionTranscript.State.WAITING, waiting.state)
        assertFalse(waiting.reachedReady)
        assertSame(waiting, waiting.record(local, 1))
        assertSame(waiting, waiting.record(remote, 4))
        assertEquals(0, waiting.writtenToTls)
        assertEquals(0, waiting.receivedHere)
        assertTrue(waiting.recent.isEmpty())
    }

    @Test fun onlyKnownPresetIdsAcceptedAfterReady() {
        val active = DebugSessionTranscript.fresh().ready()
        assertEquals(DebugSessionTranscript.State.ACTIVE, active.state)
        assertTrue(active.reachedReady)
        for (invalid in listOf(-1, 0, 5, 255, Int.MAX_VALUE)) {
            assertSame(active, active.record(remote, invalid))
        }
        assertSame(active, active.ready()) // duplicate READY cannot reset the ledger
        val after = active.record(local, 2).record(remote, 3)
        assertEquals(1, after.writtenToTls)
        assertEquals(1, after.receivedHere)
        assertEquals(
            listOf(
                DebugSessionTranscript.Entry(local, 2),
                DebugSessionTranscript.Entry(remote, 3)
            ), after.recent
        )
    }

    @Test fun boundedVisibleTailPreservesDirectionOrderAndEventTotals() {
        var ledger = DebugSessionTranscript.fresh().ready()
        repeat(25) { index ->
            val direction = if (index % 2 == 0) local else remote
            ledger = ledger.record(direction, (index % 4) + 1)
        }
        assertEquals(13, ledger.writtenToTls)
        assertEquals(12, ledger.receivedHere)
        assertEquals(DebugSessionTranscript.MAX_VISIBLE, ledger.recent.size)
        assertEquals(
            (17 until 25).map { index ->
                DebugSessionTranscript.Entry(
                    if (index % 2 == 0) local else remote, (index % 4) + 1
                )
            }, ledger.recent
        )
    }

    @Test fun closingDropsAllPhraseDataButRetainsOnlyLocalCounters() {
        val active = DebugSessionTranscript.fresh().ready()
            .record(local, 1).record(remote, 3).record(local, 2)
        val ended = active.finish()
        assertEquals(DebugSessionTranscript.State.FINISHED, ended.state)
        assertTrue(ended.reachedReady)
        assertEquals(2, ended.writtenToTls)
        assertEquals(1, ended.receivedHere)
        assertTrue(ended.recent.isEmpty())
        assertSame(ended, ended.finish())
        assertSame(ended, ended.ready())
        assertSame(ended, ended.record(local, 4))
        assertSame(ended, ended.record(remote, 1))
    }

    @Test fun failedBeforeReadyCannotBecomeAConnectedTranscript() {
        val failed = DebugSessionTranscript.fresh().finish()
        assertFalse(failed.reachedReady)
        assertEquals(DebugSessionTranscript.State.FINISHED, failed.state)
        assertSame(failed, failed.ready())
        assertSame(failed, failed.record(remote, 1))
        assertEquals(0, failed.receivedHere)
        assertTrue(failed.recent.isEmpty())
    }

    @Test fun aNewAttemptDoesNotReuseOldPhraseOrCounterState() {
        val prior = DebugSessionTranscript.fresh().ready()
            .record(remote, 1).finish()
        val next = DebugSessionTranscript.fresh()
        assertEquals(DebugSessionTranscript.State.WAITING, next.state)
        assertFalse(next.reachedReady)
        assertEquals(0, next.writtenToTls)
        assertEquals(0, next.receivedHere)
        assertTrue(next.recent.isEmpty())
        assertEquals(1, prior.receivedHere)
    }

    @Test fun callbackAdmissionRejectsCancelledAttemptsAndNonDemoStages() {
        val ledger = DebugSessionTranscript.fresh().ready()
        assertTrue(DebugSessionEventAdmission.canRecord(3, 3, "demo", ledger))
        assertFalse(DebugSessionEventAdmission.canRecord(2, 3, "demo", ledger))
        assertFalse(DebugSessionEventAdmission.canRecord(3, 3, "connecting", ledger))
        assertFalse(DebugSessionEventAdmission.canRecord(3, 3, "result", ledger))
        assertFalse(DebugSessionEventAdmission.canRecord(
            3, 3, "demo", ledger.finish()
        ))
        assertFalse(DebugSessionEventAdmission.canRecord(
            3, 3, "demo", DebugSessionTranscript.fresh()
        ))
        assertFalse(DebugSessionEventAdmission.current(5, 6))
        assertTrue(DebugSessionEventAdmission.current(6, 6))
    }

    @Test fun readyAfterTerminalOrFromStaleAttemptCannotResurrectConversation() {
        val waiting = DebugSessionTranscript.fresh()
        assertTrue(DebugSessionEventAdmission.canAcceptReady(
            7, 7, "connecting", waiting
        ))
        assertTrue(DebugSessionEventAdmission.canAcceptReady(
            7, 7, "showReply", waiting
        ))
        assertFalse(DebugSessionEventAdmission.canAcceptReady(
            6, 7, "connecting", waiting
        ))
        assertFalse(DebugSessionEventAdmission.canAcceptReady(
            7, 7, "result", waiting
        ))
        assertFalse(DebugSessionEventAdmission.canAcceptReady(
            7, 7, "demo", waiting
        ))
        assertFalse(DebugSessionEventAdmission.canAcceptReady(
            7, 7, "connecting", waiting.finish()
        ))
        assertFalse(DebugSessionEventAdmission.canAcceptReady(
            7, 7, "showReply", waiting.ready()
        ))
    }

    @Test fun lateEventsAfterTerminalRemainRejectedAcrossMultipleAttempts() {
        var attempt = 11
        var ledger = DebugSessionTranscript.fresh()
        assertTrue(DebugSessionEventAdmission.canAcceptReady(
            11, attempt, "connecting", ledger
        ))
        ledger = ledger.ready().record(local, 1).finish()
        assertFalse(DebugSessionEventAdmission.canRecord(11, attempt, "demo", ledger))
        assertFalse(DebugSessionEventAdmission.canAcceptReady(
            11, attempt, "connecting", ledger
        ))
        attempt++
        val newLedger = DebugSessionTranscript.fresh()
        assertFalse(DebugSessionEventAdmission.canAcceptReady(
            11, attempt, "connecting", newLedger
        ))
        assertTrue(DebugSessionEventAdmission.canAcceptReady(
            12, attempt, "connecting", newLedger
        ))
        assertEquals(0, newLedger.writtenToTls)
    }

    @Test fun immutableUpdatesNeverChangeEarlierSnapshots() {
        val start = DebugSessionTranscript.fresh().ready()
        val after = start.record(local, 1)
        assertEquals(0, start.writtenToTls)
        assertTrue(start.recent.isEmpty())
        assertEquals(1, after.writtenToTls)
        assertEquals(1, after.recent.size)
        assertTrue(after.finish().recent.isEmpty())
        assertEquals(1, after.recent.size)
    }
}
