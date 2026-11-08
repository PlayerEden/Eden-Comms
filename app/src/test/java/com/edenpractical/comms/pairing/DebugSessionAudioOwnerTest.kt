package com.edenpractical.comms.pairing

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DebugSessionAudioOwnerTest {
    @Test fun onlyCurrentConnectionMayStopMicAndSpeaker() {
        val owner = DebugSessionAudioOwner()
        val first = Any()
        val newer = Any()
        val stops = AtomicInteger(0)
        owner.activate(first)
        owner.activate(newer)
        assertFalse(owner.stopIfCurrent(first) { stops.incrementAndGet() })
        assertEquals(0, stops.get())
        assertTrue(owner.stopIfCurrent(newer) { stops.incrementAndGet() })
        assertEquals(1, stops.get())
        assertFalse(owner.stopIfCurrent(newer) { stops.incrementAndGet() })
        assertEquals(1, stops.get())
    }

    @Test fun voluntaryStopRevokesCallbackBeforeSocketCloses() {
        val owner = DebugSessionAudioOwner()
        val session = Any()
        owner.activate(session)
        owner.revoke()
        assertFalse(owner.stopIfCurrent(session) { fail("Old socket close must be inert") })
        val fresh = Any()
        owner.activate(fresh)
        assertTrue(owner.stopIfCurrent(fresh) { })
    }

    @Test fun lateClosedSessionCannotCancelANewlyActivatedRecording() {
        val owner = DebugSessionAudioOwner()
        val old = Any()
        val fresh = Any()
        val stops = AtomicInteger()
        owner.activate(old)
        owner.activate(fresh)
        val pool = Executors.newSingleThreadExecutor()
        try {
            val late = pool.submit<Boolean> {
                owner.stopIfCurrent(old) { stops.incrementAndGet() }
            }
            assertFalse(late.get(2, TimeUnit.SECONDS))
            assertEquals(0, stops.get())
            assertTrue(owner.stopIfCurrent(fresh) { stops.incrementAndGet() })
            assertEquals(1, stops.get())
        } finally {
            pool.shutdownNow()
        }
    }

    @Test fun closeAndNewActivationCannotInterleaveBetweenCheckAndStop() {
        val owner = DebugSessionAudioOwner()
        val old = Any()
        val fresh = Any()
        owner.activate(old)
        val enteredStop = CountDownLatch(1)
        val allowFinish = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val close = pool.submit<Boolean> {
                owner.stopIfCurrent(old) {
                    enteredStop.countDown()
                    assertTrue(allowFinish.await(2, TimeUnit.SECONDS))
                }
            }
            assertTrue(enteredStop.await(2, TimeUnit.SECONDS))
            val activate = pool.submit<Boolean> {
                owner.activate(fresh)
                true
            }
            allowFinish.countDown()
            assertTrue(close.get(2, TimeUnit.SECONDS))
            assertTrue(activate.get(2, TimeUnit.SECONDS))
            assertFalse(owner.stopIfCurrent(old) { fail("Old audio cannot close twice") })
            assertTrue(owner.stopIfCurrent(fresh) { })
        } finally {
            allowFinish.countDown()
            pool.shutdownNow()
        }
    }
}
