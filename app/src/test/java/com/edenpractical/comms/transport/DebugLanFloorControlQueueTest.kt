package com.edenpractical.comms.transport

import com.edenpractical.comms.protocol.DebugLanVoiceFloorCodec
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DebugLanFloorControlQueueTest {
    private val response = DebugLanVoiceFloorCodec.Frame(
        DebugLanVoiceFloorCodec.Kind.DENY, 1L
    )

    @Test fun floorControlCanCompleteWhileMainAudioExecutorIsBusy() {
        val audio = Executors.newSingleThreadExecutor()
        val floor = Executors.newSingleThreadExecutor()
        val audioStarted = CountDownLatch(1)
        val releaseAudio = CountDownLatch(1)
        val controlWritten = CountDownLatch(1)
        val errors = AtomicInteger(0)
        try {
            audio.execute {
                audioStarted.countDown()
                releaseAudio.await(3, TimeUnit.SECONDS)
            }
            assertTrue(audioStarted.await(2, TimeUnit.SECONDS))
            val lane = DebugLanFloorControlQueue(
                executor = floor,
                write = { controlWritten.countDown() },
                onFailure = { errors.incrementAndGet() }
            )
            assertTrue(lane.offer(response))
            // The test's PCM executor has not finished its long activity.
            assertEquals(1L, releaseAudio.count)
            assertTrue("Floor DENY must not wait for the audio executor",
                controlWritten.await(2, TimeUnit.SECONDS))
            assertEquals(0, errors.get())
        } finally {
            releaseAudio.countDown()
            audio.shutdownNow()
            floor.shutdownNow()
        }
    }

    @Test fun boundedControlQueueRejectsOverflowWithoutDroppingSilently() {
        val tasks = mutableListOf<Runnable>()
        val lane = DebugLanFloorControlQueue(
            executor = Executor { task -> tasks.add(task) },
            write = { },
            onFailure = { fail("Unexpected send failure") },
            maxPending = 2
        )
        assertTrue(lane.offer(response))
        assertTrue(lane.offer(response))
        assertEquals(2, lane.pending)
        assertFalse(lane.offer(response))
        assertEquals(2, tasks.size)
        tasks.forEach { it.run() }
        assertEquals(0, lane.pending)
        assertTrue(lane.offer(response))
    }

    @Test fun writerExceptionClosesViaCallbackAndAlwaysReleasesSlot() {
        val failCount = AtomicInteger(0)
        val lane = DebugLanFloorControlQueue(
            executor = Executor { task -> task.run() },
            write = { throw IllegalStateException("simulated blocked TLS write") },
            onFailure = { failCount.incrementAndGet() }
        )
        assertTrue(lane.offer(response))
        assertEquals(1, failCount.get())
        assertEquals(0, lane.pending)
    }

    @Test fun closedExecutorCannotAcceptEssentialControl() {
        val rejected = DebugLanFloorControlQueue(
            executor = Executor { throw RejectedExecutionException() },
            write = { fail("Should never execute") },
            onFailure = { fail("Submission failure is handled by caller") }
        )
        assertFalse(rejected.offer(response))
        assertEquals(0, rejected.pending)
    }
}
