package com.edenpractical.comms.audio

import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * F4-D4: FUTURE/UNREVIEWED 20 ms voice-frame candidate for isolated JVM tests.
 *
 * This strictly validates a fixed byte layout. IT DOES NOT provide encryption,
 * peer authentication, a secure session, freshness, replay protection across
 * sessions, microphone permission, human approval, or authorization to send.
 * It MUST NOT be wired to LanDemoSession, a socket, a radio or production UI.
 */
internal object DebugFutureLanVoiceFrameCodec {
    private val MAGIC = byteArrayOf(0x45, 0x43, 0x56, 0x46) // "ECVF"
    private const val VERSION = 1
    private const val KIND_PCM = 1
    private const val FLAGS = 0
    private const val RESERVED = 0

    // marker(4) + version(1) + kind(1) + flags(1) + reserved(1)
    // + unsigned-range sequence stored in signed long(8) + size(2).
    const val HEADER_BYTES = 18
    const val PCM_BYTES = DebugLocalPttFrames.FRAME_BYTES
    const val MAX_FRAMES = DebugLocalPttFrames.MAX_FRAMES
    const val PACKET_BYTES = HEADER_BYTES + PCM_BYTES

    internal class Decoded(
        val sequence: Long,
        val pcm: ByteArray
    ) : Closeable {
        override fun close() { pcm.fill(0) }
    }

    /** Returns a new byte array: caller must wipe it after this lab exercise. */
    fun encode(sequence: Long, pcm: ByteArray): ByteArray {
        require(sequence in 0L until MAX_FRAMES.toLong() &&
            pcm.size == PCM_BYTES) { "Invalid future voice frame" }
        return ByteBuffer.allocate(PACKET_BYTES)
            .order(ByteOrder.BIG_ENDIAN)
            .put(MAGIC)
            .put(VERSION.toByte())
            .put(KIND_PCM.toByte())
            .put(FLAGS.toByte())
            .put(RESERVED.toByte())
            .putLong(sequence)
            .putShort(PCM_BYTES.toShort())
            .put(pcm)
            .array()
    }

    /** Reject wrong marker/version/kind/flags/length/sequence before copying PCM. */
    fun decode(bytes: ByteArray): Decoded {
        require(bytes.size == PACKET_BYTES) { "Invalid future voice frame" }
        val input = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val marker = ByteArray(MAGIC.size)
        input.get(marker)
        val version = input.get().toInt() and 0xFF
        val kind = input.get().toInt() and 0xFF
        val flags = input.get().toInt() and 0xFF
        val reserved = input.get().toInt() and 0xFF
        val sequence = input.long
        val length = input.short.toInt() and 0xFFFF
        require(marker.contentEquals(MAGIC) && version == VERSION &&
            kind == KIND_PCM && flags == FLAGS && reserved == RESERVED &&
            sequence >= 0L && sequence < MAX_FRAMES.toLong() &&
            length == PCM_BYTES && input.remaining() == length
        ) { "Invalid future voice frame" }
        return Decoded(sequence, ByteArray(length).also { input.get(it) })
    }
}

/**
 * Copy-free storage-free receiver ordering rehearsal. Only the local test
 * controls the calls. Rejecting a malformed, duplicate or skipped frame ends
 * that synthetic turn. This is NOT a cryptographic replay or session guard.
 */
internal class DebugFutureLanVoiceSequenceRehearsal : Closeable {
    private var nextSequence = 0L
    private var closed = false

    val framesAccepted: Long get() = nextSequence
    val isClosed: Boolean get() = closed

    @Synchronized
    fun accept(packet: ByteArray): DebugFutureLanVoiceFrameCodec.Decoded? {
        if (closed) return null
        val decoded = try {
            DebugFutureLanVoiceFrameCodec.decode(packet)
        } catch (_: IllegalArgumentException) {
            close()
            return null
        }
        if (decoded.sequence != nextSequence) {
            decoded.close()
            close()
            return null
        }
        nextSequence++
        return decoded // test caller owns and wipes PCM; not retained by this object
    }

    @Synchronized
    override fun close() {
        closed = true
    }
}
