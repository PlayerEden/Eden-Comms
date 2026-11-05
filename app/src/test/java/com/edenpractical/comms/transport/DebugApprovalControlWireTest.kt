package com.edenpractical.comms.transport

import com.edenpractical.comms.protocol.TestPacketCodec
import com.edenpractical.comms.protocol.TestPacketException
import com.edenpractical.comms.security.DebugApprovalCommitment
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Real debug control bytes, but exchanged across in-memory JVM streams.
 * NO actual Android radio, TLS or human approval is exercised by this test.
 */
class DebugApprovalControlWireTest {
    private val commitment = ByteArray(32) { (it + 1).toByte() }
    private fun boundPacket(packet: TestPacketCodec.Packet): Boolean =
        DebugApprovalCommitment.matches(
            commitment, packet.sessionCommitment.toByteArray()
        )
    @Test fun bothDevicesMustWriteAndObserveAnApprovalBeforeTestPhrases() {
        val presenter = DebugApprovalGate()
        val joiner = DebugApprovalGate()
        assertFalse(presenter.maySendPreset())
        assertFalse(joiner.maySendPreset())

        // Presenter taps Approve; local write completes, joiner decodes it.
        val fromPresenter = ByteArrayOutputStream()
        TestPacketCodec.write(fromPresenter, TestPacketCodec.approve(commitment))
        assertEquals(DebugApprovalGate.State.LOCAL_ONLY, presenter.localWritten())
        val peerDecision = TestPacketCodec.read(
            ByteArrayInputStream(fromPresenter.toByteArray()))
        assertTrue(boundPacket(peerDecision))
        assertEquals(DebugApprovalGate.State.PEER_ONLY, joiner.peerObserved())
        assertFalse(presenter.maySendPreset())
        assertFalse(joiner.maySendPreset())

        // Joiner taps Approve; its local write completes and presenter decodes.
        val fromJoiner = ByteArrayOutputStream()
        TestPacketCodec.write(fromJoiner, TestPacketCodec.approve(commitment))
        assertEquals(DebugApprovalGate.State.BOTH_OBSERVED, joiner.localWritten())
        val presenterReceived = TestPacketCodec.read(
            ByteArrayInputStream(fromJoiner.toByteArray()))
        assertTrue(boundPacket(presenterReceived))
        assertEquals(DebugApprovalGate.State.BOTH_OBSERVED, presenter.peerObserved())
        assertTrue(joiner.maySendPreset())
        assertTrue(presenter.maySendPreset())
        assertFalse(com.edenpractical.comms.model.ReleaseCapabilities.CAN_SEND_MESSAGES)
    }

    @Test fun declinedControlIsNontextAndPreventsAnyPresets() {
        val gate = DebugApprovalGate()
        val stream = ByteArrayOutputStream()
        TestPacketCodec.write(stream, TestPacketCodec.decline(commitment))
        assertTrue(boundPacket(TestPacketCodec.read(
            ByteArrayInputStream(stream.toByteArray()))))
        gate.decline()
        assertFalse(gate.maySendPreset())
        assertEquals(DebugApprovalGate.State.DECLINED, gate.localWritten())
        assertEquals(DebugApprovalGate.State.DECLINED, gate.peerObserved())
    }

    @Test fun staleOrForeignSessionControlCannotGrantApproval() {
        val stale = ByteArray(32) { (it + 3).toByte() }
        val staleWire = ByteArrayOutputStream().apply {
            TestPacketCodec.write(this, TestPacketCodec.approve(stale))
        }
        val received = TestPacketCodec.read(
            ByteArrayInputStream(staleWire.toByteArray()))
        assertEquals(TestPacketCodec.Type.APPROVE, received.type)
        assertFalse(boundPacket(received))
        val gate = DebugApprovalGate()
        // Receiver validates the commitment BEFORE calling peerObserved().
        if (boundPacket(received)) gate.peerObserved()
        assertEquals(DebugApprovalGate.State.WAITING, gate.snapshot())
        assertFalse(gate.maySendPreset())
        assertFalse(com.edenpractical.comms.model.ReleaseCapabilities.CAN_SEND_MESSAGES)
    }

    @Test fun duplicateOrModifiedApprovalControlsFailClosed() {
        val same = ByteArrayOutputStream()
        TestPacketCodec.write(same, TestPacketCodec.approve(commitment))
        TestPacketCodec.write(same, TestPacketCodec.approve(commitment))
        val input = ByteArrayInputStream(same.toByteArray())
        val gate = DebugApprovalGate()
        assertEquals(TestPacketCodec.approve(commitment), TestPacketCodec.read(input))
        assertEquals(DebugApprovalGate.State.PEER_ONLY, gate.peerObserved())
        assertEquals(TestPacketCodec.approve(commitment), TestPacketCodec.read(input))
        assertEquals(DebugApprovalGate.State.INVALID, gate.peerObserved())
        assertFalse(gate.maySendPreset())
        assertThrows(TestPacketException::class.java) {
            TestPacketCodec.decode(byteArrayOf(1, 5, 3))
        }
        assertThrows(TestPacketException::class.java) {
            TestPacketCodec.decode(byteArrayOf(1, 6, 3))
        }
    }
}
