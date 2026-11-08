package com.edenpractical.comms.transport

import com.edenpractical.comms.protocol.DebugLanVoiceWireCodec
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * An intentionally tiny, bounded bridge from a foreground microphone thread
 * to the single authenticated TLS writer. No disk, replay or extra socket.
 *
 * The encoder assigns sequence numbers only as PCM is consumed, so this
 * bridge never buffers a full 3-second clip before transmission. Network
 * backpressure ends the turn instead of growing an unbounded audio backlog.
 */
internal class DebugLanLiveVoiceQueue {
    companion object {
        const val MAX_PENDING_FRAMES = 12 // max 240 ms held in app
    }

    private val frames = ArrayBlockingQueue<ByteArray>(MAX_PENDING_FRAMES)
    private var accepted = 0
    @Volatile private var finished = false
    @Volatile private var overrun = false

    @Synchronized
    fun offer(pcm: ByteArray): Boolean {
        if (finished || pcm.size != DebugLanVoiceWireCodec.PCM_BYTES ||
            accepted >= DebugLanVoiceWireCodec.MAX_FRAMES) return false
        val copy = pcm.copyOf()
        if (!frames.offer(copy)) {
            copy.fill(0)
            overrun = true
            finished = true
            return false
        }
        accepted++
        if (accepted >= DebugLanVoiceWireCodec.MAX_FRAMES) finished = true
        return true
    }

    @Synchronized
    fun finish() { finished = true }

    /** Cancel discards all *unsent* PCM. Already sent audio cannot be unsent. */
    @Synchronized
    fun cancel() {
        finished = true
        while (true) {
            val next = frames.poll() ?: break
            next.fill(0)
        }
    }

    fun poll(): ByteArray? = frames.poll(30, TimeUnit.MILLISECONDS)
    fun isFinishedAndEmpty(): Boolean = finished && frames.isEmpty()
    fun overran(): Boolean = overrun

    @Synchronized
    fun clear() {
        cancel()
    }
}
