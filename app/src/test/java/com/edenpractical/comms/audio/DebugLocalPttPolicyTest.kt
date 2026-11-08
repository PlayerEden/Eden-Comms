package com.edenpractical.comms.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugLocalPttPolicyTest {
    private val p = DebugLocalPttPolicy

    @Test fun explicitMicrophonePermissionAndForegroundAreRequired() {
        assertTrue(p.mayRecord(true, true, true, false, false))
        assertFalse(p.mayRecord(false, true, true, false, false))
        assertFalse(p.mayRecord(true, false, true, false, false))
        assertFalse(p.mayRecord(true, true, false, false, false))
        assertFalse(p.mayRecord(true, true, true, true, false))
        assertFalse(p.mayRecord(true, true, true, false, true))
    }

    @Test fun playbackIsLocalExplicitAndBounded() {
        assertEquals(160_000, p.MAX_PCM_BYTES)
        assertEquals(16_000, p.SAMPLE_RATE_HZ)
        assertEquals(5, p.MAX_SECONDS)
        assertTrue(p.mayPlay(true, true, false, false, 32_000))
        for (size in listOf(-1, 0, 1, 1_599, 1_601, 160_001, Int.MAX_VALUE)) {
            assertFalse("Rejected PCM size $size", p.mayPlay(true, true, false, false, size))
        }
        assertFalse(p.mayPlay(false, true, false, false, 32_000))
        assertFalse(p.mayPlay(true, false, false, false, 32_000))
        assertFalse(p.mayPlay(true, true, true, false, 32_000))
        assertFalse(p.mayPlay(true, true, false, true, 32_000))
    }

    @Test fun backgroundOrNewSessionMustDiscardLateAudioWorkerResults() {
        assertTrue(p.acceptWorkerResult(7, 7, true))
        assertFalse(p.acceptWorkerResult(7, 7, false))
        assertFalse(p.acceptWorkerResult(7, 8, true))
        assertFalse(p.acceptWorkerResult(6, 7, true))
    }
}
