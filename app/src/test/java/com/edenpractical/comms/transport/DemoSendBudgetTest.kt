package com.edenpractical.comms.transport

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoSendBudgetTest {
    @Test fun atMostTwoOutstandingSends() {
        val budget = DemoSendBudget(2)
        assertTrue(budget.tryAcquire())
        assertTrue(budget.tryAcquire())
        assertFalse(budget.tryAcquire())
        assertEquals(2, budget.pending)
        budget.release()
        assertTrue(budget.tryAcquire())
        budget.release()
        budget.release()
        budget.release()
        assertEquals(0, budget.pending)
    }

    @Test fun simultaneousCallersCannotExceedLimit() {
        val budget = DemoSendBudget(2)
        val start = CountDownLatch(1)
        val done = CountDownLatch(12)
        val workers = (0 until 12).map {
            thread {
                try {
                    start.await(3, TimeUnit.SECONDS)
                    budget.tryAcquire()
                } finally { done.countDown() }
            }
        }
        start.countDown()
        assertTrue(done.await(5, TimeUnit.SECONDS))
        workers.forEach { it.join(1000) }
        assertEquals(2, budget.pending)
    }
}
