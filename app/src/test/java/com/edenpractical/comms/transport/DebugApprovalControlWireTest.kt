package com.edenpractical.comms.transport

import com.edenpractical.comms.protocol.TestPacketCodec
import com.edenpractical.comms.protocol.TestPacketException
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
    @Test fun bothDevicesMustWriteAndObserveAnApprovalBeforeTestPhrases() {
        val presenter = DebugApprovalGate()
        val joiner = DebugApprovalGate()
        assertFalse(presenter.maySendPreset())
        assertFalse(joiner.maySendPreset())

        // Presenter taps Approve; local write completes, joiner decodes it.
        val fromPresenter = ByteArrayOutputStream()
        TestPacketCodec.write(fromPresenter, TestPacketCodec.approve())
        assertEquals(DebugApprovalGate.State.LOCAL_ONLY, presenter.localWritten())
        assertEquals(TestPacketCodec.approve(),
            TestPacketCodec.read(ByteArrayInputStream(fromPresenter.toByteArray())))
        assertEquals(DebugApprovalGate.State.PEER_ONLY, joiner.peerObserved())
        assertFalse(presenter.maySendPreset())
        assertFalse(joiner.maySendPreset())

        // Joiner taps Approve; its local write completes and presenter decodes.
        val fromJoiner = ByteArrayOutputStream()
        TestPacketCodec.write(fromJoiner, TestPacketCodec.approve())
        assertEquals(DebugApprovalGate.State.BOTH_OBSERVED, joiner.localWritten())
        assertEquals(TestPacketCodec.approve(),
            TestPacketCodec.read(ByteArrayInputStream(fromJoiner.toByteArray())))
        assertEquals(DebugApprovalGate.State.BOTH_OBSERVED, presenter.peerObserved())
        assertTrue(joiner.maySendPreset())
        assertTrue(presenter.maySendPreset())
        assertFalse(com.edenpractical.comms.model.ReleaseCapabilities.CAN_SEND_MESSAGES)
    }

    @Test fun declinedControlIsNontextAndPreventsAnyPresets() {
        val gate = DebugApprovalGate()
        val stream = ByteArrayOutputStream()
        TestPacketCodec.write(stream, TestPacketCodec.decline())
        assertEquals(TestPacketCodec.decline(),
            TestPacketCodec.read(ByteArrayInputStream(stream.toByteArray())))
        gate.decline()
        assertFalse(gate.maySendPreset())
        assertEquals(DebugApprovalGate.State.DECLINED, gate.localWritten())
        assertEquals(DebugApprovalGate.State.DECLINED, gate.peerObserved())
    }

    @Test fun duplicateOrModifiedApprovalControlsFailClosed() {
        val same = ByteArrayOutputStream()
        TestPacketCodec.write(same, TestPacketCodec.approve())
        TestPacketCodec.write(same, TestPacketCodec.approve())
        val input = ByteArrayInputStream(same.toByteArray())
        val gate = DebugApprovalGate()
        assertEquals(TestPacketCodec.approve(), TestPacketCodec.read(input))
        assertEquals(DebugApprovalGate.State.PEER_ONLY, gate.peerObserved())
        assertEquals(TestPacketCodec.approve(), TestPacketCodec.read(input))
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
