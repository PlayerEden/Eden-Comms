package com.edenpractical.comms.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DemoSessionLivenessPolicyTest {
    private val policy = DemoSessionLivenessPolicy

    @Test fun connectionIsLiveAtExactPeerSilenceThreshold() {
        val started = 50_000L
        val last = started + 10_000L
        assertNull(policy.endReason(last + policy.PEER_SILENCE_MS, started, last))
        assertEquals(policy.EndReason.PEER_SILENT,
            policy.endReason(last + policy.PEER_SILENCE_MS + 1, started, last))
    }

    @Test fun heartbeatsPreventPrematureDisconnection() {
        val started = 100_000L
        // Consecutive received heartbeats keep an otherwise long session alive.
        for (step in 1..70) {
            val now = started + step * 8_000L
            assertNull(policy.endReason(now, started, now))
        }
    }

    @Test fun absoluteSessionLimitWinsEvenWithRecentHeartbeat() {
        val started = 1_000L
        val end = started + policy.MAX_SESSION_MS
        assertNull(policy.endReason(end - 1, started, end - 1))
        assertEquals(policy.EndReason.SESSION_LIMIT,
            policy.endReason(end, started, end))
    }

    @Test fun impossibleTimeOrderingCannotExtendTest() {
        assertEquals(policy.EndReason.INVALID_TIMING,
            policy.endReason(999, 1_000, 1_000))
        assertEquals(policy.EndReason.INVALID_TIMING,
            policy.endReason(5_000, 2_000, 1_999))
    }

    @Test fun concurrentFreshReceiveDoesNotCauseFalseTimeout() {
        // Reader may advance lastReceived after watchdog reads elapsed clock.
        assertNull(policy.endReason(40_000, 1_000, 40_001))
    }

    @Test fun timeoutDoesNotDependOnWallClockOrBackgroundStatus() {
        val started = 200_000L
        assertEquals(policy.EndReason.PEER_SILENT,
            policy.endReason(started + 40_001, started, started))
        assertEquals(policy.EndReason.SESSION_LIMIT,
            policy.endReason(started + policy.MAX_SESSION_MS + 50, started,
                started + policy.MAX_SESSION_MS + 49))
    }
}
