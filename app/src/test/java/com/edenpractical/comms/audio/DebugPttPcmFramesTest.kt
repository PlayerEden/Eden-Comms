package com.edenpractical.comms.audio

import java.io.IOException
import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F4-A real byte-path exercises for one-phone microphone packets.
 * These tests DO NOT certify remote PTT, timing, acoustic quality,
 * security, encrypted transport or audio hardware compatibility.
 */
class DebugPttPcmFramesTest {
    private val codec = DebugPttPcmFrames

    private fun pcm(bytes: Int): ByteArray =
        ByteArray(bytes) { index -> (index * 37 + (index ushr 2)).toByte() }

    @Test fun twentyMsFramesPreserveActualSamplesIncludingShortFinalFrame() {
        assertEquals(640, codec.PCM_BYTES_PER_FRAME)
        assertEquals(20, codec.FRAME_MS)
        assertEquals(10, codec.HEADER_BYTES)
        val input = pcm(1_600) // 2 complete 20ms frames + 10ms final
        val frames = codec.packetize(input, input.size)
        try {
            assertEquals(3, frames.size)
            assertEquals(650, frames[0].size)
            assertEquals(650, frames[1].size)
            assertEquals(330, frames[2].size)
            assertEquals(0, frames[0][9].toInt())
            assertEquals(1, frames[2][9].toInt())
            assertArrayEquals(input, codec.reassemble(frames))
        } finally {
            codec.wipe(frames)
            input.fill(0)
        }
    }

    @Test fun exactFiveSecondRecordingRemainsBoundedAndLossless() {
        val input = pcm(DebugLocalPttPolicy.MAX_PCM_BYTES)
        val frames = codec.packetize(input, input.size)
        try {
            assertEquals(250, codec.MAX_FRAMES)
            assertEquals(250, frames.size)
            assertTrue(frames.all { it.size == 650 })
            assertArrayEquals(input, codec.reassemble(frames))
        } finally {
            codec.wipe(frames)
            input.fill(0)
        }
    }

    @Test fun validFrameLengthsAndSequencesRoundtripWithoutPadding() {
        val sizes = listOf(1_600, 1_602, 1_918, 1_920, 2_560, 32_000,
            159_998, 160_000)
        for (n in sizes) {
            val input = pcm(n)
            val frames = codec.packetize(input, n)
            try {
                assertArrayEquals("PCM size $n", input, codec.reassemble(frames))
                for ((i, frame) in frames.withIndex()) {
                    val seq = ((frame[5].toInt() and 255) shl 8) or
                        (frame[6].toInt() and 255)
                    assertEquals(i + 1, seq)
                }
            } finally {
                codec.wipe(frames)
                input.fill(0)
            }
        }
    }

    @Test fun invalidCapturedPcmLengthIsRefusedBeforeAnyPacketAllocation() {
        val input = pcm(160_002)
        for (n in listOf(-1, 0, 1, 1_598, 1_599, 1_601, 160_001, 160_002,
            Int.MAX_VALUE)) {
            assertThrows("invalid PCM bytes $n", IllegalArgumentException::class.java) {
                codec.packetize(input, n)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            codec.packetize(ByteArray(1_600), 1_602)
        }
    }

    @Test fun malformedHeaderWrongVersionOrForbiddenFlagsFailBeforePlayback() {
        val input = pcm(2_560)
        val original = codec.packetize(input, input.size)
        try {
            val mutations: List<(MutableList<ByteArray>) -> Unit> = listOf(
                { it[0][0] = 0 }, // wrong magic
                { it[0][4] = 2 }, // version
                { it[0][5] = 1 }, // sequence high bytes
                { it[0][6] = 4 }, // sequence low bytes
                { it[0][7] = 0 }, // length high byte
                { it[0][8] = 2 }, // length low byte
                { it[0][9] = 1 }, // too-early final frame
                { it.last()[9] = 0 }, // no final marker
                { it.last()[9] = 3 }, // unknown flags
                { it[0] = it[0].copyOf(it[0].size - 1) }, // truncated packet
                { it[0] = it[0] + 0 }, // extra unframed byte
            )
            for (mutate in mutations) {
                val frames = original.map { it.copyOf() }.toMutableList()
                try {
                    mutate(frames)
                    assertThrows(IOException::class.java) {
                        codec.reassemble(frames)
                    }
                } finally { codec.wipe(frames) }
            }
        } finally {
            codec.wipe(original)
            input.fill(0)
        }
    }

    @Test fun missingDuplicateAndOutOfOrderAudioFramesFailClosed() {
        val frames = codec.packetize(pcm(2_560), 2_560)
        try {
            val tests = listOf(
                frames.drop(1),
                listOf(frames[0], frames[0], frames[2], frames[3]),
                listOf(frames[1], frames[0], frames[2], frames[3]),
                frames.dropLast(1),
                frames + frames.last(),
                emptyList()
            )
            for (sequence in tests) {
                assertThrows(IOException::class.java) {
                    codec.reassemble(sequence)
                }
            }
        } finally { codec.wipe(frames) }
    }

    @Test fun clearedPacketBuffersDoNotRemainPlayable() {
        val captured = pcm(1_600)
        val frames = codec.packetize(captured, captured.size)
        assertFalse(frames.all { it.all { byte -> byte == 0.toByte() } })
        codec.wipe(frames)
        assertTrue(frames.all { it.all { byte -> byte == 0.toByte() } })
        assertThrows(IOException::class.java) { codec.reassemble(frames) }
        // Wipe may be called again by background + onDestroy.
        codec.wipe(frames)
        codec.wipe(null)
        captured.fill(0)
    }

    @Test fun randomizedEvenSampleLengthsMaintainExactRoundtripAndBoundedFrames() {
        val rng = Random(4_202_611)
        repeat(40) {
            val n = rng.nextInt(800, 80_001) * 2
            val input = pcm(n)
            val frames = codec.packetize(input, n)
            try {
                assertTrue(frames.size <= codec.MAX_FRAMES)
                assertArrayEquals(input, codec.reassemble(frames))
            } finally {
                codec.wipe(frames)
                input.fill(0)
            }
        }
    }
}
