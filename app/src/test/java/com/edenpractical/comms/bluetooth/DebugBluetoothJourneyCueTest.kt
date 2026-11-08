package com.edenpractical.comms.bluetooth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugBluetoothJourneyCueTest {
    private val c = DebugBluetoothJourneyCue
    private val idle = DebugRfcommAttemptGate.Phase.IDLE

    @Test fun qrFirstWithoutAutomaticAuthentication() {
        val initial = c.current(DebugBluetoothQrCoordinator.Stage.CHOOSE, idle, false, false, false)
        assertTrue(initial.phase.contains("STEP 1"))
        assertTrue(initial.instruction.contains("shows a code"))
        for (s in listOf(DebugBluetoothQrCoordinator.Stage.HOST_SHOW, DebugBluetoothQrCoordinator.Stage.JOIN_SCAN_HOST, DebugBluetoothQrCoordinator.Stage.HOST_SCAN_REPLY,
            DebugBluetoothQrCoordinator.Stage.JOIN_SHOW_REPLY)) {
            assertTrue(c.current(s, idle, false, false, false).phase.contains("STEP 1"))
        }
    }

    @Test fun hostAndJoinHaveClearDifferentNextSteps() {
        val host = c.current(DebugBluetoothQrCoordinator.Stage.HOST_PREPARED, idle, false, false, false)
        assertTrue(host.phase.contains("STEP 2"))
        assertTrue(host.instruction.contains("Host"))
        val find = c.current(DebugBluetoothQrCoordinator.Stage.JOIN_PREPARED, idle, false, false, false)
        assertTrue(find.next.contains("search", ignoreCase = true))
        val searching = c.current(DebugBluetoothQrCoordinator.Stage.JOIN_PREPARED, idle, false, true, false)
        assertTrue(searching.instruction.contains("Looking"))
        val found = c.current(DebugBluetoothQrCoordinator.Stage.JOIN_PREPARED, idle, true, false, false)
        assertTrue(found.instruction.contains("Join"))
        assertFalse(found.next.contains("already verified", ignoreCase = true))
    }

    @Test fun linkAndConsentOverrideEvenStaleQrOrUnverifiedSignal() {
        val connecting = c.current(DebugBluetoothQrCoordinator.Stage.CHOOSE, DebugRfcommAttemptGate.Phase.CONNECTING,
            true, false, false)
        assertTrue(connecting.phase.contains("STEP 3"))
        val tls = c.current(DebugBluetoothQrCoordinator.Stage.HOST_PREPARED, DebugRfcommAttemptGate.Phase.LINK_OPEN,
            true, false, false)
        assertTrue(tls.next.contains("closes after the checks"))
        val consent = c.current(DebugBluetoothQrCoordinator.Stage.JOIN_PREPARED, DebugRfcommAttemptGate.Phase.LINK_OPEN,
            true, false, true)
        assertTrue(consent.phase.contains("BOTH PEOPLE APPROVE"))
        val end = c.current(DebugBluetoothQrCoordinator.Stage.JOIN_PREPARED, DebugRfcommAttemptGate.Phase.STOPPED,
            true, false, true)
        assertTrue(end.phase.contains("TEST ENDED"))
        assertTrue(end.next.contains("fresh codes"))
        val restart = c.current(DebugBluetoothQrCoordinator.Stage.HOST_SHOW,
            DebugRfcommAttemptGate.Phase.STOPPED, false, false, false)
        assertTrue(restart.phase.contains("STEP 1"))
        val retryJoin = c.current(DebugBluetoothQrCoordinator.Stage.JOIN_PREPARED,
            DebugRfcommAttemptGate.Phase.STOPPED, false, false, false)
        assertTrue(retryJoin.phase.contains("STEP 2"))
    }
}
