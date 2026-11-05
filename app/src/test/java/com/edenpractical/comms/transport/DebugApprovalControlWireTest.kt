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
 * Debug controls over actual bounded byte framing in two JVM endpoints.
 * NO Android radio, TLS, real phones, or independent protocol review here.
 */
class DebugApprovalControlWireTest {
    private val commitment = ByteArray(32) { (it + 1).toByte() }

    private fun isCurrent(packet: TestPacketCodec.Packet): Boolean =
        DebugApprovalCommitment.matches(commitment, packet.sessionCommitment.toByteArray())

    private fun throughWire(packet: TestPacketCodec.Packet): TestPacketCodec.Packet {
        val output = ByteArrayOutputStream()
        TestPacketCodec.write(output, packet)
        return TestPacketCodec.read(ByteArrayInputStream(output.toByteArray()))
    }

    @Test fun twoPhonesNeedBothApprovalsAndTwoFinalConfirmsBeforeAnyPresets() {
        val presenter = DebugApprovalGate()
        val joiner = DebugApprovalGate()
        assertFalse(presenter.maySendPreset())
        assertFalse(joiner.maySendPreset())

        // Person A taps; B receives A's session-bound approval.
        val aApprove = throughWire(TestPacketCodec.approve(commitment))
        assertEquals(DebugApprovalGate.State.LOCAL_ONLY, presenter.localWritten())
        assertTrue(isCurrent(aApprove))
        assertEquals(DebugApprovalGate.State.PEER_ONLY, joiner.peerObserved())
        assertFalse(presenter.maySendPreset())
        assertFalse(joiner.maySendPreset())

        // Person B taps; A receives B's approval. BOTH have now observed two.
        val bApprove = throughWire(TestPacketCodec.approve(commitment))
        assertEquals(DebugApprovalGate.State.BOTH_OBSERVED, joiner.localWritten())
        assertTrue(isCurrent(bApprove))
        assertEquals(DebugApprovalGate.State.BOTH_OBSERVED, presenter.peerObserved())
        assertFalse(presenter.maySendPreset())
        assertFalse(joiner.maySendPreset())

        // A confirms this exact session; B receives it, still cannot send.
        assertTrue(presenter.mayWriteConfirmation())
        val aConfirm = throughWire(TestPacketCodec.confirm(commitment))
        assertEquals(DebugApprovalGate.State.LOCAL_CONFIRMED,
            presenter.localConfirmationWritten())
        assertTrue(isCurrent(aConfirm))
        assertEquals(DebugApprovalGate.State.PEER_CONFIRMED,
            joiner.peerConfirmationObserved())
        assertFalse(presenter.maySendPreset())
        assertFalse(joiner.maySendPreset())

        // B confirms. A and B now each observed two approvals and confirmations.
        val bConfirm = throughWire(TestPacketCodec.confirm(commitment))
        assertEquals(DebugApprovalGate.State.CONFIRMED, joiner.localConfirmationWritten())
        assertTrue(isCurrent(bConfirm))
        assertEquals(DebugApprovalGate.State.CONFIRMED, presenter.peerConfirmationObserved())
        assertTrue(presenter.maySendPreset())
        assertTrue(joiner.maySendPreset())
        assertFalse(com.edenpractical.comms.model.ReleaseCapabilities.CAN_SEND_MESSAGES)
    }

    @Test fun declinedControlRevokesAnyFinalConfirmation() {
        val gate = DebugApprovalGate()
        gate.localWritten()
        gate.peerObserved()
        gate.localConfirmationWritten()
        gate.peerConfirmationObserved()
        assertTrue(gate.maySendPreset())
        assertTrue(isCurrent(throughWire(TestPacketCodec.decline(commitment))))
        assertEquals(DebugApprovalGate.State.DECLINED, gate.decline())
        assertFalse(gate.maySendPreset())
        assertEquals(DebugApprovalGate.State.DECLINED, gate.peerConfirmationObserved())
    }

    @Test fun oldOrStaleSessionConfirmationCannotUnlockGate() {
        val stale = ByteArray(32) { (it + 3).toByte() }
        val stalePacket = throughWire(TestPacketCodec.confirm(stale))
        assertFalse(isCurrent(stalePacket))
        val gate = DebugApprovalGate()
        gate.localWritten()
        gate.peerObserved()
        assertEquals(DebugApprovalGate.State.BOTH_OBSERVED, gate.snapshot())
        if (isCurrent(stalePacket)) gate.peerConfirmationObserved()
        assertFalse(gate.maySendPreset())
        assertThrows(TestPacketException::class.java) {
            TestPacketCodec.decode(byteArrayOf(1, 7, 0)) // generic legacy confirm
        }
    }

    @Test fun earlyOrDuplicatePeerConfirmationIsInvalidEvenWithMatchingDigest() {
        val early = DebugApprovalGate()
        val peerConfirm = throughWire(TestPacketCodec.confirm(commitment))
        assertTrue(isCurrent(peerConfirm))
        assertEquals(DebugApprovalGate.State.INVALID, early.peerConfirmationObserved())
        assertFalse(early.maySendPreset())

        val repeated = DebugApprovalGate()
        repeated.localWritten()
        repeated.peerObserved()
        assertTrue(isCurrent(peerConfirm))
        repeated.peerConfirmationObserved()
        assertEquals(DebugApprovalGate.State.INVALID, repeated.peerConfirmationObserved())
        assertFalse(repeated.maySendPreset())
    }

    @Test fun duplicateApprovalStillTerminatesBeforeFinalConfirmation() {
        val stream = ByteArrayOutputStream()
        TestPacketCodec.write(stream, TestPacketCodec.approve(commitment))
        TestPacketCodec.write(stream, TestPacketCodec.approve(commitment))
        val input = ByteArrayInputStream(stream.toByteArray())
        val gate = DebugApprovalGate()
        assertTrue(isCurrent(TestPacketCodec.read(input)))
        assertEquals(DebugApprovalGate.State.PEER_ONLY, gate.peerObserved())
        assertTrue(isCurrent(TestPacketCodec.read(input)))
        assertEquals(DebugApprovalGate.State.INVALID, gate.peerObserved())
        assertFalse(gate.mayWriteConfirmation())
        assertFalse(gate.maySendPreset())
    }
}
