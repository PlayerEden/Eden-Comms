package com.edenpractical.comms.pairing

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private typealias O = DebugVoicePlayoutTurn.Observation

class DebugVoicePlayoutTurnTest {

    @Test fun validTlsStartWithoutAndroidSpeakerIsNotCountedAsAudiblePlayback() {
        val turn = DebugVoicePlayoutTurn()
        // A validated remote START alone causes NO local success observation.
        assertEquals(O.LOCAL_PLAYBACK_FAILED, turn.rejectedFrame())
        assertEquals(O.NONE, turn.rejectedFrame())
        assertEquals(O.NONE, turn.speakerResult(false))
    }

    @Test fun oneRealSpeakerStartIsReportedOncePerTurn() {
        val turn = DebugVoicePlayoutTurn()
        assertEquals(O.SPEAKER_STARTED, turn.speakerResult(true))
        assertEquals(O.NONE, turn.speakerResult(true))
        assertEquals(O.LOCAL_PLAYBACK_FAILED, turn.rejectedFrame())
        assertEquals(O.NONE, turn.rejectedFrame())
        // The driver started, but later audio may have been dropped.
        assertEquals(O.NONE, turn.speakerResult(true))
    }

    @Test fun audioTrackFailureIsRecordedOnceAndNeverConvertedIntoFakeStart() {
        val turn = DebugVoicePlayoutTurn()
        assertEquals(O.LOCAL_PLAYBACK_FAILED, turn.speakerResult(false))
        assertEquals(O.NONE, turn.speakerResult(false))
        assertEquals(O.NONE, turn.rejectedFrame())
    }

    @Test fun newVoiceTurnStartsFreshWithoutReusingPreviousOutcome() {
        val old = DebugVoicePlayoutTurn()
        assertEquals(O.LOCAL_PLAYBACK_FAILED, old.rejectedFrame())
        val fresh = DebugVoicePlayoutTurn()
        assertEquals(O.SPEAKER_STARTED, fresh.speakerResult(true))
        assertEquals(O.NONE, fresh.speakerResult(true))
        assertEquals(O.NONE, old.rejectedFrame())
    }

    @Test fun concurrentRepeatedSpeakerFailuresProduceExactlyOneFailure() {
        val turn = DebugVoicePlayoutTurn()
        val pool = Executors.newFixedThreadPool(4)
        val go = CountDownLatch(1)
        val observed = AtomicInteger(0)
        try {
            val tasks = (0 until 32).map {
                pool.submit {
                    assertTrue(go.await(3, TimeUnit.SECONDS))
                    val result = if (it % 2 == 0) turn.rejectedFrame()
                        else turn.speakerResult(false)
                    if (result == O.LOCAL_PLAYBACK_FAILED) observed.incrementAndGet()
                }
            }
            go.countDown()
            tasks.forEach { it.get(3, TimeUnit.SECONDS) }
            assertEquals(1, observed.get())
            assertEquals(O.NONE, turn.rejectedFrame())
        } finally {
            go.countDown()
            pool.shutdownNow()
        }
    }

    @Test fun concurrentRealStartAndBufferOverflowAreBothReportedAtMostOnce() {
        val turn = DebugVoicePlayoutTurn()
        val pool = Executors.newFixedThreadPool(3)
        val go = CountDownLatch(1)
        val starts = AtomicInteger(0)
        val failures = AtomicInteger(0)
        try {
            val tasks = (0 until 100).map { index ->
                pool.submit {
                    assertTrue(go.await(3, TimeUnit.SECONDS))
                    val result = if (index % 2 == 0) turn.speakerResult(true)
                        else turn.rejectedFrame()
                    if (result == O.SPEAKER_STARTED) starts.incrementAndGet()
                    if (result == O.LOCAL_PLAYBACK_FAILED) failures.incrementAndGet()
                }
            }
            go.countDown()
            tasks.forEach { it.get(3, TimeUnit.SECONDS) }
            assertEquals(1, starts.get())
            assertEquals(1, failures.get())
        } finally {
            go.countDown()
            pool.shutdownNow()
        }
    }
}
