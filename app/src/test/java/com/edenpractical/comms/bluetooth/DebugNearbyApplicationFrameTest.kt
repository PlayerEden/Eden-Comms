package com.edenpractical.comms.bluetooth

import com.edenpractical.comms.protocol.DebugLanVoiceWireCodec
import org.junit.Assert.*
import org.junit.Test

class DebugNearbyApplicationFrameTest {
    @Test fun textAckAnd20msVoiceFramesAreStrictlySeparate() {
        val text = DebugBluetoothTextFrameCodec.text(1, "hello")
        val ack = DebugBluetoothTextFrameCodec.acknowledgment(1)
        assertEquals(DebugNearbyApplicationFrame.Kind.TEXT_OR_ACK,
            DebugNearbyApplicationFrame.inspect(text))
        assertEquals(DebugNearbyApplicationFrame.Kind.TEXT_OR_ACK,
            DebugNearbyApplicationFrame.inspect(ack))
        val sample = ByteArray(DebugLanVoiceWireCodec.PCM_BYTES) { (it % 31).toByte() }
        val frames = listOf(
            DebugLanVoiceWireCodec.start(1L),
            DebugLanVoiceWireCodec.data(1L, 0, sample),
            DebugLanVoiceWireCodec.end(1L, 1)
        )
        frames.forEach { frame ->
            val encoded = DebugLanVoiceWireCodec.encode(frame)
            assertEquals(DebugNearbyApplicationFrame.Kind.VOICE,
                DebugNearbyApplicationFrame.inspect(encoded))
            encoded.fill(0)
            frame.clear()
        }
        sample.fill(0)
    }

    @Test fun malformedVoiceAndTextNeverReachReader() {
        val sample = ByteArray(DebugLanVoiceWireCodec.PCM_BYTES)
        val frame = DebugLanVoiceWireCodec.data(2L, 0, sample)
        val clean = DebugLanVoiceWireCodec.encode(frame)
        val invalidVersion = clean.copyOf().also { it[4] = 127 }
        val truncated = clean.copyOf(clean.size - 1)
        val malformedLength = clean.copyOf().also { it[19] = 0 }
        val unknownType = clean.copyOf().also { it[5] = 99 }
        for (bytes in listOf(
            invalidVersion, truncated, malformedLength, unknownType,
            ByteArray(DebugNearbyApplicationFrame.MAX_BYTES + 1),
            byteArrayOf(1, 2, 3), ByteArray(16)
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                DebugNearbyApplicationFrame.inspect(bytes)
            }
            bytes.fill(0)
        }
        assertEquals(DebugNearbyApplicationFrame.MAX_BYTES,
            DebugLanVoiceWireCodec.HEADER_BYTES + DebugLanVoiceWireCodec.PCM_BYTES)
        clean.fill(0)
        frame.clear()
    }

    @Test fun assemblerRejectsOutOfOrderAndDoesNotReturnIncompleteClip() {
        val asm = com.edenpractical.comms.protocol.DebugLanVoiceAssembler()
        val pcm = ByteArray(DebugLanVoiceWireCodec.PCM_BYTES) { it.toByte() }
        assertNull(asm.accept(DebugLanVoiceWireCodec.start(3L)))
        // Sequence must start at zero; incomplete clips have no playback.
        try {
            asm.accept(DebugLanVoiceWireCodec.data(3L, 1, pcm))
            fail("Skipped voice frame must abort this turn")
        } catch (_: Exception) { }
        assertThrows(Exception::class.java) {
            asm.accept(DebugLanVoiceWireCodec.end(3L, 1))
        }
        asm.clear()
        pcm.fill(0)
    }
}
