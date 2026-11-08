package com.edenpractical.comms.audio

import java.io.Closeable
import java.util.ArrayDeque

/**
 * F4-D2: bounded 20 ms PCM frames for the local, DEBUG-only microphone/speaker test.
 *
 * A frame contains precisely 320 mono 16-bit samples (640 bytes) at 16 kHz.
 * This queue is not a wire protocol, jitter buffer, encrypted session, or sender.
 * It intentionally has no socket, device, or background interfaces.
 */
internal class DebugLocalPttFrames private constructor() : Closeable {
    internal data class Frame(val sequence: Int, val pcm: ByteArray)

    private val pending = ArrayDeque<Frame>()
    private var nextSequence = 0
    private var nextPlayback = 0
    private var closed = false

    val queuedCount: Int get() = pending.size

    /** Copy caller data, refuse replays, gaps, surplus frames or use after close. */
    fun offer(sequence: Int, pcm: ByteArray): Boolean {
        if (closed || sequence != nextSequence ||
            nextSequence >= MAX_FRAMES || pcm.size != FRAME_BYTES
        ) return false
        pending.addLast(Frame(sequence, pcm.copyOf()))
        nextSequence++
        return true
    }

    /** Ownership of returned PCM transfers to the local playback worker, which must wipe it. */
    fun poll(): Frame? {
        if (closed || pending.isEmpty()) return null
        val frame = pending.removeFirst()
        if (frame.sequence != nextPlayback) {
            frame.pcm.fill(0)
            close()
            return null
        }
        nextPlayback++
        return frame
    }

    override fun close() {
        if (closed) return
        closed = true
        while (pending.isNotEmpty()) pending.removeFirst().pcm.fill(0)
    }

    companion object {
        const val FRAME_MILLIS = 20
        const val FRAME_BYTES = DebugLocalPttPolicy.SAMPLE_RATE_HZ *
            FRAME_MILLIS / 1_000 * 2
        const val MAX_FRAMES =
            DebugLocalPttPolicy.MAX_PCM_BYTES / FRAME_BYTES

        /**
         * Rehearse framing on one phone only, AFTER the existing explicit capture ends.
         * A partial final packet is zero-padded; bytes are never persisted or transmitted.
         */
        fun fromClip(clip: ByteArray): DebugLocalPttFrames? {
            if (clip.size !in DebugLocalPttPolicy.MIN_PCM_BYTES..
                DebugLocalPttPolicy.MAX_PCM_BYTES || clip.size % 2 != 0
            ) return null
            val frames = DebugLocalPttFrames()
            var offset = 0
            var sequence = 0
            while (offset < clip.size) {
                val packet = ByteArray(FRAME_BYTES)
                val length = minOf(FRAME_BYTES, clip.size - offset)
                clip.copyInto(packet, endIndex = offset + length,
                    destinationOffset = 0, startIndex = offset)
                val accepted = frames.offer(sequence, packet)
                packet.fill(0)
                if (!accepted) {
                    frames.close()
                    return null
                }
                offset += length
                sequence++
            }
            return frames
        }
    }
}
