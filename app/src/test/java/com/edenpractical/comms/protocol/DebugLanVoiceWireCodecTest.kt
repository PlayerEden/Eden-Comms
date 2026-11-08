package com.edenpractical.comms.protocol

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class DebugLanVoiceWireCodecTest {
    private val wire = DebugLanVoiceWireCodec

    @Test fun shortPcmBurstRoundTripsWithoutPersistentAudio() {
        val incoming = DebugLanVoiceAssembler()
        val begin = wire.decode(wire.encode(wire.start(91L)))
        assertNull(incoming.accept(begin))
        for (sequence in 0..7) {
            val pcm = ByteArray(wire.PCM_BYTES) { (sequence + it).toByte() }
            val local = wire.data(91, sequence, pcm)
            pcm.fill(0)
            val encoded = wire.encode(local)
            local.clear()
            val decoded = wire.decode(encoded)
            assertEquals(DebugLanVoiceWireCodec.Kind.DATA, decoded.kind)
            assertEquals(sequence, decoded.sequence)
            assertNull(incoming.accept(decoded))
            decoded.clear()
            encoded.fill(0)
        }
        val result = incoming.accept(wire.decode(wire.encode(wire.end(91, 8))))
        assertNotNull(result)
        assertEquals(wire.PCM_BYTES * 8, result!!.size)
        assertEquals(0, result[0].toInt())
        result.fill(0)
        assertNull(incoming.accept(wire.start(92)))
    }

    @Test fun rejectsWrongWireAndOutOfOrderWithoutOfferingClip() {
        val b = wire.encode(wire.start(1))
        val malformed = b.clone()
        malformed[0] = 0
        assertThrows(IOException::class.java) { wire.decode(malformed) }
        assertThrows(IOException::class.java) { wire.decode(b + byteArrayOf(3)) }
        assertThrows(IllegalArgumentException::class.java) {
            wire.data(1, 0, ByteArray(10))
        }
        val received = DebugLanVoiceAssembler()
        assertNull(received.accept(wire.start(1)))
        val bad = wire.data(1, 1, ByteArray(wire.PCM_BYTES))
        assertThrows(IOException::class.java) { received.accept(bad) }
        assertThrows(IOException::class.java) {
            received.accept(wire.data(1, 0, ByteArray(wire.PCM_BYTES)))
        }
        bad.clear()
    }

    @Test fun finishBeforeAnyPcmOrWrongTurnNeverGeneratesAudio() {
        val received = DebugLanVoiceAssembler()
        received.accept(wire.start(4))
        assertThrows(IOException::class.java) { received.accept(wire.end(4, 1)) }
        val second = DebugLanVoiceAssembler()
        second.accept(wire.start(2))
        assertThrows(IOException::class.java) {
            second.accept(wire.data(3, 0, ByteArray(wire.PCM_BYTES)))
        }
        assertThrows(IllegalArgumentException::class.java) { wire.start(0) }
        assertThrows(IllegalArgumentException::class.java) { wire.end(1, wire.MAX_FRAMES + 1) }
    }

    @Test fun realDebugTestPacketEnvelopeCarriesVoiceOnlyNotConsentOrTypedText() {
        val pcm = ByteArray(wire.PCM_BYTES) { 12 }
        val voicePacket = TestPacketCodec.debugVoice(wire.data(9, 0, pcm))
        val encoded = TestPacketCodec.encode(voicePacket)
        val decoded = TestPacketCodec.decode(encoded)
        assertEquals(TestPacketCodec.Type.DEBUG_VOICE_PCM, decoded.type)
        assertEquals(9L, decoded.voice!!.turn)
        assertEquals(0, decoded.voice!!.sequence)
        assertTrue(decoded.text.isEmpty())
        assertTrue(decoded.sessionCommitment.isEmpty())
        decoded.voice!!.clear()
        voicePacket.voice!!.clear()
        encoded.fill(0)
        pcm.fill(0)
    }

    @Test fun aCompletedTurnCannotBeReplayedOnTheSameTestSession() {
        val incoming = DebugLanVoiceAssembler()
        incoming.accept(wire.start(12))
        incoming.accept(wire.data(12, 0, ByteArray(wire.PCM_BYTES)))
        incoming.accept(wire.end(12, 1))!!.fill(0)
        assertThrows(IOException::class.java) { incoming.accept(wire.start(12)) }
    }

    @Test fun actualEncryptedSessionPacketIOStillRoundTripsVoiceAndControls() {
        val pcm = ByteArray(wire.PCM_BYTES) { (it % 101).toByte() }
        val local = TestPacketCodec.debugVoice(wire.data(37, 0, pcm))
        val sent = java.io.ByteArrayOutputStream()
        TestPacketCodec.write(sent, local)
        val received = TestPacketCodec.read(
            java.io.ByteArrayInputStream(sent.toByteArray())
        )
        assertEquals(TestPacketCodec.Type.DEBUG_VOICE_PCM, received.type)
        assertEquals(37L, received.voice!!.turn)
        assertEquals(0, received.voice!!.sequence)
        assertArrayEquals(pcm, received.voice!!.pcm)
        received.voice!!.clear()
        local.voice!!.clear()
        pcm.fill(0)

        val readyBytes = java.io.ByteArrayOutputStream()
        TestPacketCodec.write(readyBytes, TestPacketCodec.ready())
        assertEquals(TestPacketCodec.ready(), TestPacketCodec.read(
            java.io.ByteArrayInputStream(readyBytes.toByteArray())))
    }

    @Test fun malformedVoiceReadFailsClosedThroughRealPacketIO() {
        val corrupt = wire.encode(wire.start(12))
        corrupt[4] = 99.toByte()
        val framed = FrameCodec.encode(corrupt)
        assertThrows(IOException::class.java) {
            TestPacketCodec.read(java.io.ByteArrayInputStream(framed))
        }
        framed.fill(0)
        corrupt.fill(0)
    }

    @Test fun rejectsUnknownVersionKindAndControlFlags() {
        val b = wire.encode(wire.start(1))
        for ((index, value) in listOf(4 to 2, 5 to 99, 6 to 1, 7 to 1,
            18 to 1, 19 to 1)) {
            val broken = b.copyOf()
            broken[index] = value.toByte()
            assertThrows(IOException::class.java) { wire.decode(broken) }
        }
    }
}
