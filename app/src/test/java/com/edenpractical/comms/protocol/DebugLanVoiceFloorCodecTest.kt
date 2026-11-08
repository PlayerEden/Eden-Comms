package com.edenpractical.comms.protocol

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class DebugLanVoiceFloorCodecTest {
    private val wire = DebugLanVoiceFloorCodec

    @Test fun exactPacketRoundTripOnExistingTestTlsFraming() {
        for (kind in wire.Kind.entries) {
            val frame = wire.Frame(kind, 42L)
            val encoded = wire.encode(frame)
            assertEquals(wire.BYTES, encoded.size)
            assertEquals(frame, wire.decode(encoded))
            val out = ByteArrayOutputStream()
            TestPacketCodec.write(out, TestPacketCodec.debugVoiceFloor(frame))
            val read = TestPacketCodec.read(ByteArrayInputStream(out.toByteArray()))
            assertEquals(TestPacketCodec.Type.DEBUG_VOICE_FLOOR, read.type)
            assertEquals(frame, read.floor)
        }
    }

    @Test fun malformedOrUncorrelatedWireNeverBecomesFloorPermission() {
        val seed = wire.encode(wire.Frame(wire.Kind.GRANT, 1L))
        for (i in listOf(0, 1, 2, 3, 4, 5, 6, 7, 15)) {
            val bad = seed.copyOf()
            bad[i] = if (i == 15) 0.toByte() else 0x7f.toByte()
            if (i == 15) {
                // ID 0 rejected; all 8 ID bytes are zeroed.
                for (p in 8 until 16) bad[p] = 0
            }
            assertThrows(IOException::class.java) { wire.decode(bad) }
        }
        assertThrows(IOException::class.java) { wire.decode(seed + 0.toByte()) }
        assertThrows(IOException::class.java) { wire.decode(seed.dropLast(1).toByteArray()) }
        assertThrows(IllegalArgumentException::class.java) {
            wire.Frame(wire.Kind.GRANT, 0L)
        }
    }

    @Test fun noFloorControlCanSmuggleTextOrAudio() {
        assertThrows(IllegalArgumentException::class.java) {
            TestPacketCodec.encode(TestPacketCodec.Packet(
                TestPacketCodec.Type.DEBUG_VOICE_FLOOR,
                text = "message",
                floor = wire.Frame(wire.Kind.REQUEST, 1)
            ))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TestPacketCodec.encode(TestPacketCodec.Packet(
                TestPacketCodec.Type.DEBUG_VOICE_FLOOR,
                floor = null
            ))
        }
    }
}
