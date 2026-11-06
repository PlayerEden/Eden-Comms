package com.edenpractical.comms.bluetooth

import com.edenpractical.comms.bluetooth.DebugRfcommAttemptGate.End
import com.edenpractical.comms.bluetooth.DebugRfcommAttemptGate.Phase
import com.edenpractical.comms.bluetooth.DebugRfcommAttemptGate.Role
import com.edenpractical.comms.model.ReleaseCapabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugRfcommAttemptGateTest {
    @Test fun bothRolesRequireForegroundAndSeparatePermissionEvidence() {
        for (role in Role.entries) {
            val state = DebugRfcommAttemptGate()
            assertNull(state.begin(role, false, true))
            assertNull(state.begin(role, true, false))
            assertEquals(Phase.IDLE, state.snapshot().phase)
            val token = requireNotNull(state.begin(role, true, true))
            assertTrue(state.isConnecting(token))
            assertNull(state.begin(role, true, true))
            assertTrue(state.opened(token, true, true))
            assertEquals(Phase.LINK_OPEN, state.snapshot().phase)
            assertEquals(role, state.snapshot().role)
            assertFalse(state.opened(token, true, true))
            assertFalse(ReleaseCapabilities.CAN_CONNECT)
            assertFalse(ReleaseCapabilities.CAN_SEND_MESSAGES)
            assertFalse(ReleaseCapabilities.CAN_TRANSMIT_VOICE)
            assertTrue(state.stop(token, End.USER))
        }
    }

    @Test fun missingPermissionOrBackgroundAtSocketSuccessFailsClosed() {
        for ((foreground, permitted, reason) in listOf(
            Triple(false, true, End.BACKGROUND),
            Triple(true, false, End.PERMISSION_LOST)
        )) {
            val state = DebugRfcommAttemptGate()
            val token = requireNotNull(state.begin(Role.JOIN, true, true))
            assertFalse(state.opened(token, foreground, permitted))
            assertEquals(Phase.STOPPED, state.snapshot().phase)
            assertEquals(reason, state.snapshot().reason)
            assertFalse(state.isLive(token))
        }
    }

    @Test fun timedOutAndFailedWorkersCannotResurrectClosedAttempt() {
        for (end in End.entries) {
            val state = DebugRfcommAttemptGate()
            val old = requireNotNull(state.begin(Role.HOST, true, true))
            assertTrue(state.stop(old, end))
            assertFalse(state.opened(old, true, true))
            assertFalse(state.stop(old, End.USER))
            assertTrue(state.reset())
            val new = requireNotNull(state.begin(Role.JOIN, true, true))
            assertTrue(new.generation > old.generation)
            assertFalse(state.opened(old, true, true))
            assertFalse(state.stop(old, End.FAILED))
            assertEquals(Phase.CONNECTING, state.snapshot().phase)
            assertTrue(state.opened(new, true, true))
            assertTrue(state.stop(new, End.EXPIRED))
            assertEquals(End.EXPIRED, state.snapshot().reason)
        }
    }

    @Test fun cannotResetActiveLinkOrOpenWrongToken() {
        val state = DebugRfcommAttemptGate()
        assertFalse(state.reset())
        val token = requireNotNull(state.begin(Role.HOST, true, true))
        assertFalse(state.reset())
        val other = DebugRfcommAttemptGate()
        val alienToken = requireNotNull(other.begin(Role.JOIN, true, true))
        assertFalse(state.opened(alienToken, true, true))
        assertFalse(state.stop(alienToken, End.USER))
        assertTrue(state.stop(token, End.BACKGROUND))
        assertTrue(state.reset())
        assertEquals(Phase.IDLE, state.snapshot().phase)
        assertNull(state.snapshot().role)
    }

    @Test fun noPublicPeerIdentityOrTextDeliveryMayBeDeducedFromOpenState() {
        val state = DebugRfcommAttemptGate()
        val token = requireNotNull(state.begin(Role.HOST, true, true))
        assertNotNull(token)
        assertTrue(state.opened(token, true, true))
        assertEquals(Phase.LINK_OPEN, state.snapshot().phase)
        assertFalse(ReleaseCapabilities.CAN_CONNECT)
        assertFalse(ReleaseCapabilities.CAN_SEND_MESSAGES)
        assertFalse(ReleaseCapabilities.CAN_TRANSMIT_VOICE)
        assertTrue(state.stop(token, End.USER))
    }
}
