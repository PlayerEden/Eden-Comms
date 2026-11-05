package com.edenpractical.comms.transport

import com.edenpractical.comms.model.ConnectionMethod
import com.edenpractical.comms.model.ReleaseCapabilities
import com.edenpractical.comms.transport.NearbySessionLifecycle.EndReason
import com.edenpractical.comms.transport.NearbySessionLifecycle.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ScriptedNearbyTransportAdapterTest {
    private fun open(method: ConnectionMethod = ConnectionMethod.LAN):
        Pair<ScriptedNearbyTransportAdapter, NearbySessionLifecycle.Attempt> {
        val adapter = ScriptedNearbyTransportAdapter(method)
        return adapter to requireNotNull(adapter.begin())
    }

    @Test fun allPlannedMethodsUseSameDeterministicLifecycleWithoutRealTransport() {
        for (method in ConnectionMethod.values()) {
            val (sim, token) = open(method)
            assertEquals(Phase.STARTING, sim.lifecycle.snapshot().phase)
            assertEquals(method, sim.lifecycle.snapshot().method)
            assertTrue(sim.post(token, ScriptedNearbyTransportAdapter.Signal.LINK_PRESENT, 10))
            assertTrue(sim.post(token, ScriptedNearbyTransportAdapter.Signal.PREVIEW_READY, 20))
            assertTrue(sim.post(token, ScriptedNearbyTransportAdapter.Signal.PEER_LOST, 30))

            assertTrue(sim.advanceBy(9).isEmpty())
            val link = sim.advanceBy(1)
            assertEquals(1, link.size)
            assertTrue(link.single().accepted)
            assertEquals(10L, link.single().atMs)
            assertEquals(Phase.LINK_PRESENT, sim.lifecycle.snapshot().phase)

            val preview = sim.advanceBy(10)
            assertTrue(preview.single().accepted)
            assertEquals(Phase.PREVIEW_READY, sim.lifecycle.snapshot().phase)
            assertTrue(sim.lifecycle.isPreviewActive(token))
            assertFalse(ReleaseCapabilities.CAN_CONNECT)
            assertFalse(ReleaseCapabilities.CAN_SEND_MESSAGES)
            assertFalse(ReleaseCapabilities.CAN_TRANSMIT_VOICE)

            val ended = sim.advanceBy(10)
            assertTrue(ended.single().accepted)
            assertEquals(Phase.TERMINATED, sim.lifecycle.snapshot().phase)
            assertEquals(EndReason.PEER_LEFT, sim.lifecycle.snapshot().endReason)
            assertFalse(sim.lifecycle.isPreviewActive(token))
            assertEquals(0, sim.pendingCount)
        }
    }

    @Test fun sameTimestampCallbacksPreserveInsertionOrder() {
        val (sim, token) = open()
        assertTrue(sim.post(token, ScriptedNearbyTransportAdapter.Signal.LINK_PRESENT, 25))
        assertTrue(sim.post(token, ScriptedNearbyTransportAdapter.Signal.PREVIEW_READY, 25))
        assertTrue(sim.post(token, ScriptedNearbyTransportAdapter.Signal.PEER_LOST, 25))
        val delivered = sim.advanceBy(25)
        assertEquals(
            listOf(
                ScriptedNearbyTransportAdapter.Signal.LINK_PRESENT,
                ScriptedNearbyTransportAdapter.Signal.PREVIEW_READY,
                ScriptedNearbyTransportAdapter.Signal.PEER_LOST
            ), delivered.map { it.signal }
        )
        assertTrue(delivered.all { it.accepted })
        assertTrue(delivered.all { it.atMs == 25L })
        assertEquals(Phase.TERMINATED, sim.lifecycle.snapshot().phase)
    }

    @Test fun earlyPreviewBeforeLinkFailsClosedForEachMethod() {
        for (method in ConnectionMethod.values()) {
            val (sim, token) = open(method)
            assertTrue(sim.post(token, ScriptedNearbyTransportAdapter.Signal.PREVIEW_READY, 5))
            assertTrue(sim.post(token, ScriptedNearbyTransportAdapter.Signal.LINK_PRESENT, 10))
            val delivered = sim.advanceBy(10)
            assertEquals(listOf(false, false), delivered.map { it.accepted })
            assertEquals(EndReason.INVALID_SEQUENCE, sim.lifecycle.snapshot().endReason)
            assertFalse(sim.lifecycle.isPreviewActive(token))
        }
    }

    @Test fun duplicateLinkAtSameInstantTerminatesBeforeAnyReady() {
        val (sim, token) = open()
        assertTrue(sim.post(token, ScriptedNearbyTransportAdapter.Signal.LINK_PRESENT, 2))
        assertTrue(sim.post(token, ScriptedNearbyTransportAdapter.Signal.LINK_PRESENT, 2))
        assertTrue(sim.post(token, ScriptedNearbyTransportAdapter.Signal.PREVIEW_READY, 3))
        val events = sim.advanceBy(3)
        assertEquals(listOf(true, false, false), events.map { it.accepted })
        assertEquals(EndReason.INVALID_SEQUENCE, sim.lifecycle.snapshot().endReason)
        assertFalse(sim.lifecycle.isPreviewActive(token))
    }

    @Test fun cancelBeforeDelayedCallbacksPreventsAnyTestReady() {
        for (method in ConnectionMethod.values()) {
            val (sim, token) = open(method)
            assertTrue(sim.post(token, ScriptedNearbyTransportAdapter.Signal.LINK_PRESENT, 1))
            assertTrue(sim.post(token, ScriptedNearbyTransportAdapter.Signal.PREVIEW_READY, 50))
            assertTrue(sim.post(token, ScriptedNearbyTransportAdapter.Signal.TIMED_OUT, 75))
            assertTrue(sim.cancel(token))
            assertFalse(sim.cancel(token))
            assertNull(sim.begin())
            val events = sim.advanceBy(100)
            assertEquals(3, events.size)
            assertTrue(events.none { it.accepted })
            assertEquals(Phase.TERMINATED, sim.lifecycle.snapshot().phase)
            assertEquals(EndReason.USER_CANCELLED, sim.lifecycle.snapshot().endReason)
            assertFalse(sim.lifecycle.isPreviewActive(token))
        }
    }

    @Test fun priorGenerationEventsCannotModifyNewConnection() {
        val (sim, old) = open()
        assertTrue(sim.post(old, ScriptedNearbyTransportAdapter.Signal.NETWORK_CHANGED, 60))
        assertTrue(sim.post(old, ScriptedNearbyTransportAdapter.Signal.PREVIEW_READY, 70))
        assertTrue(sim.cancel(old))
        assertTrue(sim.resetAfterTermination())

        val fresh = requireNotNull(sim.begin())
        assertTrue(fresh.generation > old.generation)
        assertTrue(sim.post(fresh, ScriptedNearbyTransportAdapter.Signal.LINK_PRESENT, 5))
        assertTrue(sim.post(fresh, ScriptedNearbyTransportAdapter.Signal.PREVIEW_READY, 10))
        val first = sim.advanceBy(10)
        assertEquals(listOf(true, true), first.map { it.accepted })
        assertTrue(sim.lifecycle.isPreviewActive(fresh))
        val oldCallbacks = sim.advanceBy(65)
        assertEquals(2, oldCallbacks.size)
        assertTrue(oldCallbacks.none { it.accepted })
        assertTrue(sim.lifecycle.isPreviewActive(fresh))
        assertEquals(Phase.PREVIEW_READY, sim.lifecycle.snapshot().phase)
        assertNull(sim.lifecycle.snapshot().endReason)
    }

    @Test fun timeoutWinsAgainstQueuedReadyAtAndAfterDeadline() {
        for (method in ConnectionMethod.values()) {
            val (sim, token) = open(method)
            sim.post(token, ScriptedNearbyTransportAdapter.Signal.LINK_PRESENT, 1)
            sim.post(token, ScriptedNearbyTransportAdapter.Signal.TIMED_OUT, 90_000)
            sim.post(token, ScriptedNearbyTransportAdapter.Signal.PREVIEW_READY, 90_000)
            val events = sim.advanceBy(90_000)
            assertEquals(listOf(true, true, false), events.map { it.accepted })
            assertEquals(EndReason.TIMED_OUT, sim.lifecycle.snapshot().endReason)
            assertFalse(sim.lifecycle.isPreviewActive(token))
        }
    }

    @Test fun verificationFailureAndLinkLossRemainTerminal() {
        for (failure in listOf(
            ScriptedNearbyTransportAdapter.Signal.VERIFICATION_REJECTED,
            ScriptedNearbyTransportAdapter.Signal.NETWORK_CHANGED,
            ScriptedNearbyTransportAdapter.Signal.PEER_LOST
        )) {
            val (sim, token) = open()
            sim.post(token, ScriptedNearbyTransportAdapter.Signal.LINK_PRESENT, 1)
            sim.post(token, failure, 2)
            sim.post(token, ScriptedNearbyTransportAdapter.Signal.PREVIEW_READY, 3)
            val events = sim.advanceBy(3)
            assertEquals(listOf(true, true, false), events.map { it.accepted })
            assertEquals(Phase.TERMINATED, sim.lifecycle.snapshot().phase)
            assertFalse(sim.lifecycle.isPreviewActive(token))
        }
    }

    @Test fun backgroundExitImmediatelyWinsOverQueuedProgress() {
        val (sim, token) = open()
        sim.post(token, ScriptedNearbyTransportAdapter.Signal.LINK_PRESENT, 1)
        sim.post(token, ScriptedNearbyTransportAdapter.Signal.PREVIEW_READY, 2)
        assertTrue(sim.appBackgrounded(token))
        assertFalse(sim.appBackgrounded(token))
        assertTrue(sim.advanceBy(2).none { it.accepted })
        assertEquals(EndReason.APP_BACKGROUNDED, sim.lifecycle.snapshot().endReason)
    }

    @Test fun boundedQueueAndVirtualTimeRejectInvalidInputs() {
        val (sim, token) = open()
        assertFalse(sim.post(token, ScriptedNearbyTransportAdapter.Signal.LINK_PRESENT, -1))
        assertFalse(sim.post(
            token, ScriptedNearbyTransportAdapter.Signal.LINK_PRESENT,
            ScriptedNearbyTransportAdapter.MAX_ADVANCE_MS + 1
        ))
        repeat(ScriptedNearbyTransportAdapter.MAX_PENDING) { i ->
            assertTrue(sim.post(
                token, ScriptedNearbyTransportAdapter.Signal.LINK_PRESENT, i.toLong()
            ))
        }
        assertEquals(ScriptedNearbyTransportAdapter.MAX_PENDING, sim.pendingCount)
        assertFalse(sim.post(token, ScriptedNearbyTransportAdapter.Signal.PREVIEW_READY, 0))
        assertThrows(IllegalArgumentException::class.java) { sim.advanceBy(-1) }
        assertThrows(IllegalArgumentException::class.java) {
            sim.advanceBy(ScriptedNearbyTransportAdapter.MAX_ADVANCE_MS + 1)
        }
        assertEquals(0L, sim.clockMs)
        assertEquals(64, sim.advanceBy(64).size)
        assertEquals(0, sim.pendingCount)
        assertEquals(64L, sim.clockMs)
    }

    @Test fun deterministicRandomizedFaultMatrixNeverResurrectsTerminatedAttempt() {
        val signals = ScriptedNearbyTransportAdapter.Signal.values()
        val random = Random(0xE0C0)
        for (method in ConnectionMethod.values()) {
            repeat(30) {
                val (sim, token) = open(method)
                repeat(50) {
                    sim.post(token, signals[random.nextInt(signals.size)],
                        random.nextLong(0L, 120L))
                }
                // Force physical-equivalent cancellation before callbacks drain.
                assertTrue(sim.cancel(token))
                val deliveries = sim.advanceBy(120)
                assertEquals(50, deliveries.size)
                assertTrue(deliveries.none { it.accepted })
                assertEquals(Phase.TERMINATED, sim.lifecycle.snapshot().phase)
                assertEquals(EndReason.USER_CANCELLED, sim.lifecycle.snapshot().endReason)
                assertFalse(sim.lifecycle.isPreviewActive(token))
            }
        }
    }
}
