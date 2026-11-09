package com.edenpractical.comms.pairing

import com.edenpractical.comms.protocol.DebugLanVoiceWireCodec
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * F4-O: the final AudioRecord handoff and the user's Stop action compete for
 * one very short critical section. A frame cannot be accepted *after* stop
 * wins; a frame already handed to the encrypted writer cannot be recalled.
 *
 * The Android capture worker invokes this only with the bounded in-memory
 * DebugLanLiveVoiceQueue.offer callback, never with a blocking socket write.
 */
internal class DebugLiveMicFrameGate {
    private var stopped = false

    @Synchronized
    fun offerIfActive(frame: ByteArray, handoff: (ByteArray) -> Boolean): Boolean =
        !stopped && handoff(frame)

    @Synchronized
    fun stop() {
        stopped = true
    }
}

/**
 * F4-O: Android's speaker worker and encrypted LAN reader may race when the
 * conversation is hidden, backgrounded or disconnected. Copy-and-enqueue,
 * finish, and cancel/drain must be atomic with respect to one another.
 *
 * Poll may run independently. A frame already polled by the speaker is
 * outside this queue's control; the Android caller stops AudioTrack and
 * rechecks cancellation before writing that frame.
 *
 * Never retains audio on disk and never opens a connection.
 */
internal class DebugLivePlaybackBuffer {
    companion object {
        const val MAX_PENDING_FRAMES = 12 // 240 ms of 20 ms PCM frames
    }

    private val frames = ArrayBlockingQueue<ByteArray>(MAX_PENDING_FRAMES)
    @Volatile private var ended = false
    @Volatile private var cancelled = false

    @Synchronized
    fun offer(pcm: ByteArray): Boolean {
        if (ended || cancelled ||
            pcm.size != DebugLanVoiceWireCodec.PCM_BYTES) return false
        val copy = pcm.copyOf()
        if (frames.offer(copy)) return true
        copy.fill(0)
        cancel()
        return false
    }

    @Synchronized
    fun finish() {
        ended = true
    }

    @Synchronized
    fun cancel() {
        cancelled = true
        ended = true
        while (true) {
            val sample = frames.poll() ?: break
            sample.fill(0)
        }
    }

    fun poll(timeout: Long, unit: TimeUnit): ByteArray? =
        frames.poll(timeout, unit)

    fun isEmpty(): Boolean = frames.isEmpty()
    fun isEnded(): Boolean = ended
    fun isCancelled(): Boolean = cancelled
}
