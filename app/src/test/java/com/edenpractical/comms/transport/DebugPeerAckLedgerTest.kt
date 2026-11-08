package com.edenpractical.comms.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class DebugPeerAckLedgerTest {
    @Test fun onlyKnownSentAndConfirmedMessagesCanProduceReceipt() {
        val ledger = DebugPeerAckLedger()
        assertEquals(DebugPeerAckLedger.Result.REJECT, ledger.acknowledge(1, true))
        assertTrue(ledger.queue(1))
        assertFalse(ledger.queue(1))
        assertEquals(DebugPeerAckLedger.Result.REJECT, ledger.acknowledge(1, false))
        assertEquals(DebugPeerAckLedger.Result.WAITING_FOR_WRITE, ledger.acknowledge(1, true))
        assertEquals(DebugPeerAckLedger.Result.REJECT, ledger.acknowledge(1, true))
        assertTrue(ledger.written(1))
        assertEquals(DebugPeerAckLedger.Result.REJECT, ledger.acknowledge(1, true))
        assertEquals(0, ledger.outstanding)

        assertTrue(ledger.queue(2))
        assertFalse(ledger.written(2))
        assertEquals(DebugPeerAckLedger.Result.ACKNOWLEDGED, ledger.acknowledge(2, true))
        assertEquals(DebugPeerAckLedger.Result.REJECT, ledger.acknowledge(2, true))
        assertFalse(ledger.queue(1))
    }

    @Test fun boundedOutstandingRejectsFloodAndCloseClearsState() {
        val ledger = DebugPeerAckLedger(maxOutstanding = 2)
        assertFalse(ledger.queue(0))
        assertFalse(ledger.queue(-1))
        assertTrue(ledger.queue(1))
        assertTrue(ledger.queue(2))
        assertFalse(ledger.queue(3))
        ledger.cancel(1)
        assertTrue(ledger.queue(3))
        assertEquals(2, ledger.outstanding)
        assertEquals(DebugPeerAckLedger.Result.REJECT, ledger.acknowledge(999, true))
        assertEquals(DebugPeerAckLedger.Result.REJECT, ledger.acknowledge(0, true))
        ledger.close()
        assertEquals(0, ledger.outstanding)
        assertFalse(ledger.queue(4))
        assertFalse(ledger.written(2))
        assertEquals(DebugPeerAckLedger.Result.REJECT, ledger.acknowledge(2, true))
    }

    @Test fun earlyNetworkReceiptRacingLocalWriterIsDeferredUntilWrite() {
        val ledger = DebugPeerAckLedger()
        assertTrue(ledger.queue(12))
        val accepted = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val task = thread(name = "ack-race-reader") {
            assertEquals(DebugPeerAckLedger.Result.WAITING_FOR_WRITE,
                ledger.acknowledge(12, true))
            accepted.countDown()
            finish.await(2, TimeUnit.SECONDS)
        }
        try {
            assertTrue(accepted.await(2, TimeUnit.SECONDS))
            assertTrue(ledger.written(12))
            assertEquals(0, ledger.outstanding)
        } finally {
            finish.countDown()
            task.join(2_000)
        }
        assertFalse(task.isAlive)
    }

    @Test fun unknownFutureIdsInvalidApprovalAndCancelledWorkFailClosed() {
        val ledger = DebugPeerAckLedger()
        assertTrue(ledger.queue(1))
        assertFalse(ledger.written(5))
        assertEquals(DebugPeerAckLedger.Result.REJECT, ledger.acknowledge(2, true))
        ledger.cancel(1)
        assertEquals(DebugPeerAckLedger.Result.REJECT, ledger.acknowledge(1, true))
        assertFalse(ledger.queue(1))
        assertTrue(ledger.queue(2))
        assertEquals(DebugPeerAckLedger.Result.REJECT, ledger.acknowledge(2, false))
    }
}
