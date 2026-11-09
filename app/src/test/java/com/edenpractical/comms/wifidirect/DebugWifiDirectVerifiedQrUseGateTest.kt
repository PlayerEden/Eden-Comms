package com.edenpractical.comms.wifidirect

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugWifiDirectVerifiedQrUseGateTest {
    @Test fun oneQrTranscriptCanBeClaimedOnlyOnceUntilExplicitFreshQrReset() {
        val gate = DebugWifiDirectVerifiedQrUseGate()
        assertTrue(gate.available())
        assertTrue(gate.claim())
        assertFalse(gate.available())
        repeat(100) { assertFalse(gate.claim()) }
        // Socket failure/timeout/decline must never call reset implicitly.
        assertFalse(gate.available())
        gate.resetForNewQrExchange()
        assertTrue(gate.available())
        assertTrue(gate.claim())
        assertFalse(gate.claim())
    }

    @Test fun simultaneousConnectionAttemptsCannotBothClaimOneQrTranscript() {
        val gate = DebugWifiDirectVerifiedQrUseGate()
        val workers = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        val successes = AtomicInteger(0)
        try {
            val results = (1..32).map {
                workers.submit(Callable {
                    if (!start.await(3, TimeUnit.SECONDS)) return@Callable false
                    if (gate.claim()) successes.incrementAndGet()
                    true
                })
            }
            start.countDown()
            results.forEach { assertTrue(it.get(5, TimeUnit.SECONDS)) }
            assertEquals(1, successes.get())
            assertFalse(gate.available())
        } finally {
            start.countDown()
            workers.shutdownNow()
        }
    }

    @Test fun aFreshExchangeGetsOneNewUseWithoutRestoringAnyOtherAuthority() {
        val gate = DebugWifiDirectVerifiedQrUseGate()
        repeat(10) {
            gate.resetForNewQrExchange()
            assertTrue(gate.claim())
            assertFalse(gate.available())
        }
        // This model never says "approved" or "connected"; it is a
        // single-use input claim, not a human approval or trusted device.
        assertFalse(gate.claim())
    }
}
