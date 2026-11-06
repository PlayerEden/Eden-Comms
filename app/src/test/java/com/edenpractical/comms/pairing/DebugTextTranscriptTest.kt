package com.edenpractical.comms.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugTextTranscriptTest {
    @Test fun activityOnlyAppearsAfterConfirmedSession() {
        val new = DebugTextTranscript.fresh()
        assertSame(new, new.record(DebugTextTranscript.Direction.RECEIVED_HERE, 1, "test"))
        assertEquals(DebugTextTranscript.State.WAITING, new.state)
        val active = new.ready()
        assertTrue(active.visible.isEmpty())
        assertEquals(DebugTextTranscript.State.ACTIVE, active.state)
        val sent = active.record(DebugTextTranscript.Direction.WRITTEN_TO_TLS, 1, "Hello!")
        val received = sent.record(DebugTextTranscript.Direction.RECEIVED_HERE, 1, "Hi!")
        assertEquals(2, received.visible.size)
        assertEquals(1, received.writtenHere)
        assertEquals(1, received.receivedHere)
        assertEquals("Hi!", received.visible.last().text)
        assertEquals(DebugTextTranscript.Direction.RECEIVED_HERE,
            received.visible.last().direction)
        assertTrue(new.visible.isEmpty())
    }

    @Test fun rejectsInvalidAndDuplicateLocalUiEvents() {
        val state = DebugTextTranscript.fresh().ready()
        for (text in listOf("", "   ", "x".repeat(281), "hello\u0000",
            "hello\u007f")) {
            assertSame(state, state.record(DebugTextTranscript.Direction.RECEIVED_HERE, 1, text))
        }
        assertSame(state, state.record(DebugTextTranscript.Direction.RECEIVED_HERE, 0, "valid"))
        val good = state.record(DebugTextTranscript.Direction.RECEIVED_HERE, 3, "Hello 🙂")
        assertSame(good, good.record(DebugTextTranscript.Direction.RECEIVED_HERE, 3, "duplicate"))
    }

    @Test fun capAndIrreversibleFinishDiscardActualMessageContent() {
        var state = DebugTextTranscript.fresh().ready()
        repeat(60) { i ->
            state = state.record(DebugTextTranscript.Direction.WRITTEN_TO_TLS,
                i.toLong() + 1, "private-test-$i")
        }
        assertEquals(DebugTextTranscript.MAX_VISIBLE, state.visible.size)
        assertEquals(60, state.writtenHere)
        assertEquals("private-test-59", state.visible.last().text)
        val ended = state.finish()
        assertTrue(ended.visible.isEmpty())
        assertEquals(60, ended.writtenHere)
        assertEquals(0, ended.receivedHere)
        assertEquals(DebugTextTranscript.State.FINISHED, ended.state)
        assertSame(ended, ended.record(DebugTextTranscript.Direction.RECEIVED_HERE, 61, "late"))
        assertSame(ended, ended.ready())
        assertSame(ended, ended.finish())
        val fresh = DebugTextTranscript.fresh()
        assertTrue(fresh.visible.isEmpty())
        assertFalse(fresh == state)
    }
    @Test fun clearVisibleDropsTextButKeepsCountersAndRejectsLateCallbacks() {
        var active = DebugTextTranscript.fresh().ready()
        active = active.record(DebugTextTranscript.Direction.WRITTEN_TO_TLS, 1L, "sent")
        active = active.record(DebugTextTranscript.Direction.RECEIVED_HERE, 1L, "received")
        val cleared = active.clearVisible()
        assertTrue(cleared.visible.isEmpty())
        assertEquals(DebugTextTranscript.State.ACTIVE, cleared.state)
        assertEquals(1, cleared.writtenHere)
        assertEquals(1, cleared.receivedHere)
        assertSame(cleared, cleared.clearVisible())
        assertSame(cleared, cleared.record(
            DebugTextTranscript.Direction.WRITTEN_TO_TLS, 1L, "late old send"
        ))
        assertSame(cleared, cleared.record(
            DebugTextTranscript.Direction.RECEIVED_HERE, 1L, "late old receive"
        ))
        val next = cleared.record(
            DebugTextTranscript.Direction.RECEIVED_HERE, 2L, "new incoming"
        )
        assertEquals("new incoming", next.visible.single().text)
        assertEquals(2, next.receivedHere)
        assertEquals(1, next.writtenHere)
        assertTrue(active.visible.isNotEmpty()) // Old immutable state not mutated.
        val ended = next.finish()
        assertTrue(ended.visible.isEmpty())
        assertSame(ended, ended.clearVisible())
    }

    @Test fun oldIdsCannotReappearAfterFallingOffBoundedTail() {
        var active = DebugTextTranscript.fresh().ready()
        repeat(45) { index ->
            active = active.record(
                DebugTextTranscript.Direction.RECEIVED_HERE,
                index.toLong() + 1L, "test $index"
            )
        }
        assertEquals(12, active.visible.size)
        assertEquals(45, active.receivedHere)
        assertSame(active, active.record(
            DebugTextTranscript.Direction.RECEIVED_HERE, 1L, "old again"
        ))
        assertSame(active, active.record(
            DebugTextTranscript.Direction.RECEIVED_HERE, 43L, "old again"
        ))
        val cleared = active.clearVisible()
        assertSame(cleared, cleared.record(
            DebugTextTranscript.Direction.RECEIVED_HERE, 45L, "replay after clear"
        ))
        assertTrue(cleared.visible.isEmpty())
    }

    @Test fun clearBeforeReadyOrAfterFinishNeverReopensOrPersists() {
        val waiting = DebugTextTranscript.fresh()
        assertSame(waiting, waiting.clearVisible())
        assertTrue(waiting.visible.isEmpty())
        val ended = waiting.finish()
        assertSame(ended, ended.clearVisible())
        assertSame(ended, ended.ready())
    }

}
