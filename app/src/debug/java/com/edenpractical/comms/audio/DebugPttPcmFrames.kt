package com.edenpractical.comms.audio

import java.io.IOException

/**
 * F4-A: DEBUG-ONLY, IN-MEMORY 20 ms mono PCM framing for the actual one-phone
 * mic -> packetize -> validate/reassemble -> speaker test.
 *
 * This is an experimental audio format, NOT a reviewed remote-PTT protocol.
 * It has no network, TLS, transport authority, microphone permission, file
 * storage, codec compression, jitter buffer, peer identity, or send API.
 * Do not send these frames to another phone before a separately reviewed,
 * mutually approved and physically tested authenticated audio transport.
 */
internal object DebugPttPcmFrames {
    const val SAMPLE_RATE_HZ = 16_000
    const val PCM_BYTES_PER_SAMPLE = 2
    const val FRAME_MS = 20
    const val PCM_BYTES_PER_FRAME =
        SAMPLE_RATE_HZ * FRAME_MS / 1_000 * PCM_BYTES_PER_SAMPLE // 640
    const val MAX_FRAMES = DebugLocalPttPolicy.MAX_SECONDS * 1_000 / FRAME_MS // 250
    const val HEADER_BYTES = 10
    private const val VERSION = 1
    private const val END_FLAG = 1
    private val MAGIC = byteArrayOf(0x45, 0x43, 0x50, 0x54) // ECPT

    /**
     * Segment real captured PCM into ordered 20 ms frames; only the final
     * frame may have a shorter (even-byte) payload. No padding or data loss.
     * The caller MUST wipe its raw PCM once these copies exist.
     */
    fun packetize(pcm: ByteArray, validBytes: Int): List<ByteArray> {
        require(validBytes in DebugLocalPttPolicy.MIN_PCM_BYTES..
            DebugLocalPttPolicy.MAX_PCM_BYTES &&
            validBytes <= pcm.size && validBytes % PCM_BYTES_PER_SAMPLE == 0
        ) { "Invalid bounded PCM capture" }
        val count = (validBytes + PCM_BYTES_PER_FRAME - 1) / PCM_BYTES_PER_FRAME
        require(count in 1..MAX_FRAMES) { "PCM frame count exceeds test limit" }

        val frames = ArrayList<ByteArray>(count)
        try {
            for (i in 0 until count) {
                val offset = i * PCM_BYTES_PER_FRAME
                val n = minOf(PCM_BYTES_PER_FRAME, validBytes - offset)
                val frame = ByteArray(HEADER_BYTES + n)
                MAGIC.copyInto(frame, 0)
                frame[4] = VERSION.toByte()
                val seq = i + 1
                frame[5] = (seq ushr 8).toByte()
                frame[6] = seq.toByte()
                frame[7] = (n ushr 8).toByte()
                frame[8] = n.toByte()
                frame[9] = if (i == count - 1) END_FLAG.toByte() else 0
                pcm.copyInto(frame, HEADER_BYTES, offset, offset + n)
                frames.add(frame)
            }
            return frames
        } catch (failure: RuntimeException) {
            wipe(frames)
            throw failure
        }
    }

    /**
     * Reject missing/duplicate/out-of-order frames, malformed lengths,
     * incorrect final flags and all unrecognized bytes BEFORE playback.
     * Bytes are copied into a single bounded PCM buffer; on any unexpected
     * failure in the copying step, that buffer is cleared before rethrow.
     */
    fun reassemble(frames: List<ByteArray>): ByteArray {
        if (frames.size !in 1..MAX_FRAMES) throw IOException("Invalid PCM frame count")
        var bytes = 0
        for ((i, frame) in frames.withIndex()) {
            if (frame.size < HEADER_BYTES + PCM_BYTES_PER_SAMPLE ||
                frame.size > HEADER_BYTES + PCM_BYTES_PER_FRAME
            ) throw IOException("Malformed PCM frame size")
            for (j in MAGIC.indices) {
                if (frame[j] != MAGIC[j]) throw IOException("Invalid PCM frame magic")
            }
            if ((frame[4].toInt() and 0xff) != VERSION)
                throw IOException("Invalid PCM frame version")
            val seq = ((frame[5].toInt() and 0xff) shl 8) or
                (frame[6].toInt() and 0xff)
            if (seq != i + 1) throw IOException("PCM frame sequence mismatch")
            val n = ((frame[7].toInt() and 0xff) shl 8) or
                (frame[8].toInt() and 0xff)
            if (n != frame.size - HEADER_BYTES || n !in 2..PCM_BYTES_PER_FRAME ||
                n % PCM_BYTES_PER_SAMPLE != 0
            ) throw IOException("Invalid PCM sample count")
            val last = i == frames.lastIndex
            if ((frame[9].toInt() and 0xff) != if (last) END_FLAG else 0)
                throw IOException("PCM final flag invalid")
            if (!last && n != PCM_BYTES_PER_FRAME)
                throw IOException("Truncated nonfinal PCM frame")
            bytes += n
            if (bytes > DebugLocalPttPolicy.MAX_PCM_BYTES)
                throw IOException("PCM audio exceeds five seconds")
        }
        if (bytes < DebugLocalPttPolicy.MIN_PCM_BYTES)
            throw IOException("PCM test clip is too short")
        val pcm = ByteArray(bytes)
        try {
            var offset = 0
            for (frame in frames) {
                val n = frame.size - HEADER_BYTES
                frame.copyInto(pcm, offset, HEADER_BYTES, HEADER_BYTES + n)
                offset += n
            }
            return pcm
        } catch (failure: RuntimeException) {
            pcm.fill(0)
            throw failure
        }
    }

    fun wipe(frames: List<ByteArray>?) {
        frames?.forEach { it.fill(0) }
    }
}
