package com.edenpractical.comms.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugConversationUiCopyTest {
    private val ui = DebugConversationUiCopy

    @Test fun outgoingIsOnlyLocalWriteAndNeverAReceipt() {
        val outgoing = DebugTextTranscript.Direction.WRITTEN_TO_TLS
        assertEquals("You", ui.participant(outgoing))
        assertEquals("Written to test link", ui.observation(outgoing))
        assertFalse(ui.observation(outgoing).contains("delivered", ignoreCase = true))
        assertFalse(ui.observation(outgoing).contains("read", ignoreCase = true))
    }

    @Test fun incomingIsOnlyObservedOnThisPhone() {
        val incoming = DebugTextTranscript.Direction.RECEIVED_HERE
        assertEquals("Other phone", ui.participant(incoming))
        assertEquals("Received on this phone", ui.observation(incoming))
        assertFalse(ui.observation(incoming).contains("verified", ignoreCase = true))
    }

    @Test fun nonnegativeLocalCountsDoNotClaimRemoteDelivery() {
        assertEquals("This phone: 0 written • 0 received", ui.counts(0, 0))
        assertEquals("This phone: 4 written • 2 received", ui.counts(4, 2))
        assertFalse(ui.counts(5, 7).contains("delivered", ignoreCase = true))
        assertThrows(IllegalArgumentException::class.java) { ui.counts(-1, 0) }
        assertThrows(IllegalArgumentException::class.java) { ui.counts(0, -1) }
    }

    @Test fun transcriptStillRequiresActiveRealCallbacks() {
        val fresh = DebugTextTranscript.fresh()
        assertTrue(fresh.visible.isEmpty())
        assertTrue(fresh.record(DebugTextTranscript.Direction.RECEIVED_HERE, 1, "foo") === fresh)
        val live = fresh.ready()
        val localWrite = live.record(DebugTextTranscript.Direction.WRITTEN_TO_TLS, 1, "hello")
        assertEquals(1, localWrite.writtenHere)
        assertEquals(0, localWrite.receivedHere)
        assertTrue(localWrite.finish().visible.isEmpty())
    }
}
