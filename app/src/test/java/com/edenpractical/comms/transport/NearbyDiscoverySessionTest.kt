package com.edenpractical.comms.transport

import com.edenpractical.comms.model.ConnectionMethod
import com.edenpractical.comms.model.ReleaseCapabilities
import com.edenpractical.comms.transport.NearbyDiscoverySession.Phase
import com.edenpractical.comms.transport.NearbyDiscoverySession.StopReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class NearbyDiscoverySessionTest {
    private fun approved(
        method: ConnectionMethod = ConnectionMethod.BLUETOOTH,
        start: Long = 1_000L
    ): Pair<NearbyDiscoverySession, NearbyDiscoverySession.Attempt> {
        val state = NearbyDiscoverySession()
        val attempt = requireNotNull(state.begin(method, foreground = true))
        assertTrue(state.personConsented(attempt, foreground = true))
        assertTrue(state.platformPermissionResult(attempt, foreground = true, granted = true))
        assertTrue(state.radioScanStarted(
            attempt, foreground = true, adapterStarted = true, elapsedMs = start
        ))
        return state to attempt
    }

    @Test fun requiresForegroundAndExplicitPersonAndOsPermissionBeforeRadio() {
        for (method in listOf(ConnectionMethod.BLUETOOTH, ConnectionMethod.WIFI_DIRECT)) {
            val state = NearbyDiscoverySession()
            assertNull(state.begin(method, foreground = false))
            assertNull(state.begin(ConnectionMethod.LAN, foreground = true))
            assertEquals(Phase.IDLE, state.snapshot().phase)
            val attempt = requireNotNull(state.begin(method, foreground = true))
            assertEquals(Phase.WAITING_FOR_PERSON_CONSENT, state.snapshot().phase)
            assertNull(state.begin(method, foreground = true))
            assertTrue(state.personConsented(attempt, foreground = true))
            assertEquals(Phase.WAITING_FOR_OS_PERMISSION, state.snapshot().phase)
            assertTrue(state.platformPermissionResult(attempt, foreground = true, granted = true))
            assertEquals(Phase.READY_FOR_RADIO, state.snapshot().phase)
            assertTrue(state.radioScanStarted(attempt, true, true, 5L))
            assertEquals(Phase.SCANNING, state.snapshot().phase)
            assertEquals(method, state.snapshot().method)
            assertFalse(ReleaseCapabilities.CAN_CONNECT)
            assertFalse(ReleaseCapabilities.CAN_SEND_MESSAGES)
            assertFalse(ReleaseCapabilities.CAN_TRANSMIT_VOICE)
        }
    }

    @Test fun earlyRadioCallbackTerminatesInsteadOfBypassingPersonConsent() {
        val state = NearbyDiscoverySession()
        val attempt = requireNotNull(state.begin(ConnectionMethod.BLUETOOTH, true))
        assertFalse(state.radioScanStarted(attempt, true, true, 0))
        assertEquals(Phase.STOPPED, state.snapshot().phase)
        assertEquals(StopReason.INVALID_SEQUENCE, state.snapshot().reason)
        assertFalse(state.personConsented(attempt, true))
        assertFalse(state.platformPermissionResult(attempt, true, true))
        assertFalse(state.candidateObserved(attempt, true, 1L))
        assertEquals(0, state.snapshot().observations)
        assertFalse(state.cancel(attempt))
    }

    @Test fun duplicateConsentAndLateOsGrantCannotAdvanceOrReviveSession() {
        val state = NearbyDiscoverySession()
        val attempt = requireNotNull(state.begin(ConnectionMethod.WIFI_DIRECT, true))
        assertTrue(state.personConsented(attempt, true))
        assertFalse(state.personConsented(attempt, true))
        assertEquals(StopReason.INVALID_SEQUENCE, state.snapshot().reason)
        assertFalse(state.platformPermissionResult(attempt, true, true))
        assertFalse(state.radioScanStarted(attempt, true, true, 10L))
    }

    @Test fun osPermissionDenialAndMissingRadioFailClosed() {
        for (method in listOf(ConnectionMethod.BLUETOOTH, ConnectionMethod.WIFI_DIRECT)) {
            val denied = NearbyDiscoverySession()
            val x = requireNotNull(denied.begin(method, true))
            assertTrue(denied.personConsented(x, true))
            assertFalse(denied.platformPermissionResult(x, true, granted = false))
            assertEquals(StopReason.PERMISSION_DENIED, denied.snapshot().reason)
            assertFalse(denied.radioScanStarted(x, true, true, 0L))

            val missing = NearbyDiscoverySession()
            val y = requireNotNull(missing.begin(method, true))
            assertTrue(missing.personConsented(y, true))
            assertTrue(missing.platformPermissionResult(y, true, true))
            assertFalse(missing.radioScanStarted(y, true, adapterStarted = false, elapsedMs = 0L))
            assertEquals(StopReason.RADIO_UNAVAILABLE, missing.snapshot().reason)
        }
    }

    @Test fun backgroundAndPermissionRevocationTerminateEveryPhase() {
        for (phase in listOf("consent", "permission", "ready", "scan")) {
            val state = NearbyDiscoverySession()
            val attempt = requireNotNull(state.begin(ConnectionMethod.BLUETOOTH, true))
            if (phase != "consent") assertTrue(state.personConsented(attempt, true))
            if (phase == "ready" || phase == "scan") {
                assertTrue(state.platformPermissionResult(attempt, true, true))
            }
            if (phase == "scan") assertTrue(state.radioScanStarted(attempt, true, true, 100L))
            assertTrue(state.onBackground(attempt))
            assertEquals(StopReason.APP_BACKGROUNDED, state.snapshot().reason)
            assertFalse(state.candidateObserved(attempt, true, 101L))
            assertFalse(state.platformPermissionResult(attempt, true, true))
            assertFalse(state.onBackground(attempt))
        }
        val (state, attempt) = approved()
        assertTrue(state.permissionRevoked(attempt))
        assertEquals(StopReason.PERMISSION_REVOKED, state.snapshot().reason)
        assertFalse(state.tick(attempt, 1_001L))
    }

    @Test fun foregroundLossFromAsyncCallbackStopsBeforeRecordingAnyCandidates() {
        val (state, attempt) = approved()
        assertFalse(state.candidateObserved(attempt, foreground = false, elapsedMs = 1_002L))
        assertEquals(Phase.STOPPED, state.snapshot().phase)
        assertEquals(StopReason.APP_BACKGROUNDED, state.snapshot().reason)
        assertEquals(0, state.snapshot().observations)
    }

    @Test fun timeWindowIsMonotonicAndHardCappedWithoutSleeping() {
        val (state, attempt) = approved(start = 500L)
        assertTrue(state.candidateObserved(attempt, true, 500L))
        assertTrue(state.tick(attempt, 500L + NearbyDiscoverySession.MAX_SCAN_MS - 1))
        assertFalse(state.candidateObserved(
            attempt, true, 500L + NearbyDiscoverySession.MAX_SCAN_MS
        ))
        assertEquals(StopReason.TIME_LIMIT, state.snapshot().reason)
        assertEquals(1, state.snapshot().observations)
        assertFalse(state.tick(attempt, 500L + NearbyDiscoverySession.MAX_SCAN_MS + 1))

        val (backward, y) = approved()
        assertFalse(backward.candidateObserved(y, true, 999L))
        assertEquals(StopReason.INVALID_CLOCK, backward.snapshot().reason)

        val future = NearbyDiscoverySession()
        val z = requireNotNull(future.begin(ConnectionMethod.BLUETOOTH, true))
        assertTrue(future.personConsented(z, true))
        assertTrue(future.platformPermissionResult(z, true, true))
        assertFalse(future.radioScanStarted(z, true, true, -1L))
        assertEquals(StopReason.INVALID_CLOCK, future.snapshot().reason)
    }

    @Test fun candidateObservationsAreOnlyBoundedCountsAndCannotGrantPeerTrust() {
        val (state, attempt) = approved()
        repeat(NearbyDiscoverySession.MAX_OBSERVATIONS) { i ->
            assertTrue(state.candidateObserved(attempt, true, 1_000L + i))
        }
        assertEquals(NearbyDiscoverySession.MAX_OBSERVATIONS, state.snapshot().observations)
        assertFalse(state.candidateObserved(attempt, true, 1_200L))
        assertEquals(StopReason.EVENT_LIMIT, state.snapshot().reason)
        assertEquals(NearbyDiscoverySession.MAX_OBSERVATIONS, state.snapshot().observations)
        assertFalse(state.candidateObserved(attempt, true, 1_201L))
        // No peers, IPs, MACs or identities can enter Snapshot: only a count.
        assertFalse(ReleaseCapabilities.CAN_CONNECT)
    }

    @Test fun oldAttemptsNeverPoisonNewAttemptOrGetAutomaticResume() {
        val state = NearbyDiscoverySession()
        val old = requireNotNull(state.begin(ConnectionMethod.BLUETOOTH, true))
        assertTrue(state.cancel(old))
        assertEquals(StopReason.USER_CANCELLED, state.snapshot().reason)
        assertFalse(state.cancel(old))
        assertNull(state.begin(ConnectionMethod.WIFI_DIRECT, true))
        assertTrue(state.resetAfterStop())
        assertEquals(0, state.snapshot().observations)
        assertFalse(state.resetAfterStop())
        val new = requireNotNull(state.begin(ConnectionMethod.WIFI_DIRECT, true))
        assertTrue(new.generation > old.generation)
        assertFalse(state.personConsented(old, true))
        assertFalse(state.platformPermissionResult(old, true, true))
        assertFalse(state.radioScanStarted(old, true, true, 0L))
        assertFalse(state.candidateObserved(old, true, 0L))
        assertEquals(Phase.WAITING_FOR_PERSON_CONSENT, state.snapshot().phase)
        assertTrue(state.personConsented(new, true))
        assertTrue(state.platformPermissionResult(new, true, true))
        assertTrue(state.radioScanStarted(new, true, true, 10L))
        assertTrue(state.candidateObserved(new, true, 11L))
        assertEquals(1, state.snapshot().observations)
    }

    @Test fun deterministicDelayedEventsNeverResurrectCancelledDiscoveries() {
        val random = Random(0x3D15)
        repeat(150) {
            val (state, attempt) = approved(
                if (it % 2 == 0) ConnectionMethod.BLUETOOTH else ConnectionMethod.WIFI_DIRECT
            )
            repeat(random.nextInt(0, 20)) { n ->
                assertTrue(state.candidateObserved(attempt, true, 1_000L + n))
            }
            assertTrue(state.cancel(attempt))
            repeat(20) {
                assertFalse(state.candidateObserved(attempt, true, random.nextLong(1_000, 20_000)))
                assertFalse(state.platformPermissionResult(attempt, true, true))
                assertFalse(state.radioScanStarted(attempt, true, true, 5_000L))
            }
            assertEquals(StopReason.USER_CANCELLED, state.snapshot().reason)
            assertEquals(Phase.STOPPED, state.snapshot().phase)
        }
    }
}
