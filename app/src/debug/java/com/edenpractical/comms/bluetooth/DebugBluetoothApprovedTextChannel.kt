package com.edenpractical.comms.bluetooth

import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import com.edenpractical.comms.protocol.DebugLanVoiceWireCodec
import com.edenpractical.comms.protocol.DebugLanVoiceAssembler
import java.util.concurrent.ArrayBlockingQueue
import javax.net.ssl.SSLEngine

/**
 * DEBUG ONLY: real encrypted Bluetooth application TEXT + peer-app ACK on the
 * *same* pinned TLS engine and original RFCOMM byte streams used by the QR,
 * exporter, transcript, APPROVE and both CONFIRM records.
 *
 * Constructed only by DebugRfcommTlsProbe's post-confirmation callback. This
 * object does not grant authentication and MUST NOT be constructed from a
 * diagnostic receipt, radio address, simulator result or UI boolean.
 *
 * Exactly one sender thread calls SSLEngine.wrap; the calling receiver thread
 * calls unwrap. JSSE permits one concurrent wrap and unwrap, not overlapping
 * calls of the same kind. The underlying socket is owned by the controller;
 * closing it interrupts an in-flight read. No plaintext fallback or storage.
 */
internal class DebugBluetoothApprovedTextChannel(
    engine: SSLEngine,
    wireInput: InputStream,
    wireOutput: OutputStream,
    private val onEvent: (Event) -> Unit
) : Closeable {
    sealed class Event {
        object Ready : Event()
        object Failed : Event()
        data class Written(val id: Long, val text: String) : Event()
        data class Received(val id: Long, val text: String) : Event()
        data class Acknowledged(val id: Long) : Event()
        data class VoiceWritten(val frames: Int) : Event()
        /** Sensitive PCM: controller MUST wipe after synchronous UI handoff. */
        data class VoiceReceived(val pcm: ByteArray) : Event()
    }

    private data class Pending(
        val bytes: ByteArray,
        val writtenText: String? = null,
        val voiceClip: ByteArray? = null
    )
    private val monitor = Any()
    private val ledger = DebugBluetoothTextLedger()
    private val incomingVoice = DebugLanVoiceAssembler()
    private var nextVoiceTurn = 1L
    private var voicePending = false
    private val encrypted = DebugBluetoothFutureTlsTextLab(engine, wireInput, wireOutput)
    private val outbound = ArrayBlockingQueue<Pending>(10)
    private var sender: Thread? = null
    @Volatile private var closed = false

    /** Never call wire I/O from Compose. This only offers a bounded frame. */
    fun offerText(text: String): Boolean = synchronized(monitor) {
        if (closed || outbound.remainingCapacity() <= 1) return@synchronized false
        val frame = try {
            ledger.prepareText(text)
        } catch (_: IllegalArgumentException) {
            return@synchronized false
        } catch (_: java.nio.charset.CharacterCodingException) {
            // Android IMEs can temporarily produce an unmatched surrogate.
            // Reject the draft rather than throwing on the UI thread.
            return@synchronized false
        } catch (_: IllegalStateException) {
            return@synchronized false
        }
        // Capacity checked while holding the SAME lock used for ACK enqueue.
        if (!outbound.offer(Pending(frame, text))) {
            frame.fill(0)
            // A failed enqueue cannot safely unassign its message id.
            close()
            return@synchronized false
        }
        true
    }

    /**
     * Offer ONE previously captured 3-second-or-shorter microphone burst.
     * Only the existing verified TLS writer sends it; never another socket.
     * This is sequential short-clip PTT, not low-latency floor-managed voice.
     * The queue holds at most one copy, not 150 unsent packets.
     */
    fun offerVoiceClip(pcm: ByteArray): Boolean = synchronized(monitor) {
        if (closed || voicePending || outbound.remainingCapacity() <= 1 ||
            pcm.isEmpty() || pcm.size > DebugLanVoiceWireCodec.MAX_PCM_BYTES ||
            pcm.size % DebugLanVoiceWireCodec.PCM_BYTES != 0
        ) return@synchronized false
        val owned = pcm.copyOf()
        if (!outbound.offer(Pending(byteArrayOf(), voiceClip = owned))) {
            owned.fill(0)
            return@synchronized false
        }
        voicePending = true
        true
    }

    /** The RFCOMM worker stays on this verified session until End / loss. */
    fun runUntilClosed() {
        val started = synchronized(monitor) {
            if (closed || sender != null) false
            else {
                sender = Thread({ writeLoop() }, "EC-debug-bt-encrypted-writer")
                sender?.start()
                true
            }
        }
        if (!started) throw IllegalStateException("Bluetooth text link not ready")
        onEvent(Event.Ready)
        try {
            while (!closed) {
                val frame = encrypted.receive()
                try {
                    when (DebugNearbyApplicationFrame.inspect(frame)) {
                        DebugNearbyApplicationFrame.Kind.TEXT_OR_ACK -> {
                            when (DebugBluetoothTextFrameCodec.decode(frame).kind) {
                                DebugBluetoothTextFrameCodec.Kind.TEXT -> {
                                    val accepted = ledger.acceptText(frame)
                                    synchronized(monitor) {
                                        if (closed || !outbound.offer(Pending(accepted.ack))) {
                                            accepted.ack.fill(0)
                                            throw IllegalStateException("Bluetooth ACK queue unavailable")
                                        }
                                    }
                                    if (!closed)
                                        onEvent(Event.Received(accepted.messageId, accepted.text))
                                }
                                DebugBluetoothTextFrameCodec.Kind.ACK -> {
                                    val id = ledger.acceptAck(frame)
                                    if (!closed) onEvent(Event.Acknowledged(id))
                                }
                            }
                        }
                        DebugNearbyApplicationFrame.Kind.VOICE -> {
                            val voice = DebugLanVoiceWireCodec.decode(frame)
                            try {
                                val complete = incomingVoice.accept(voice)
                                if (complete != null) {
                                    if (!closed) onEvent(Event.VoiceReceived(complete))
                                    else complete.fill(0)
                                }
                            } finally { voice.clear() }
                        }
                    }
                } finally {
                    frame.fill(0)
                }
            }
        } finally {
            close()
        }
    }

    private fun writeLoop() {
        try {
            while (!closed) {
                val pending = outbound.take()
                try {
                    if (closed) break
                    if (pending.voiceClip != null) {
                        val n = writeVoice(pending.voiceClip)
                        if (!closed) onEvent(Event.VoiceWritten(n))
                    } else {
                        encrypted.transmit(pending.bytes)
                        if (!closed && pending.writtenText != null) {
                            val id = DebugBluetoothTextFrameCodec.decode(pending.bytes).messageId
                            onEvent(Event.Written(id, pending.writtenText))
                        }
                    }
                } finally {
                    pending.bytes.fill(0)
                    pending.voiceClip?.fill(0)
                    if (pending.voiceClip != null) synchronized(monitor) {
                        voicePending = false
                    }
                }
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (_: Exception) {
            // Any incomplete voice turn is fatal; never resume a partial
            // sequence or silently send raw PCM on another connection.
            if (!closed) onEvent(Event.Failed)
            close()
        }
    }

    private fun writeVoice(pcm: ByteArray): Int {
        val count = pcm.size / DebugLanVoiceWireCodec.PCM_BYTES
        require(count in 1..DebugLanVoiceWireCodec.MAX_FRAMES)
        val turn = nextVoiceTurn++
        val begin = DebugLanVoiceWireCodec.encode(DebugLanVoiceWireCodec.start(turn))
        try { encrypted.transmit(begin) } finally { begin.fill(0) }
        for (index in 0 until count) {
            if (closed) throw IllegalStateException("Voice session disconnected")
            val start = index * DebugLanVoiceWireCodec.PCM_BYTES
            val chunk = pcm.copyOfRange(start, start + DebugLanVoiceWireCodec.PCM_BYTES)
            val voice = DebugLanVoiceWireCodec.data(turn, index, chunk)
            chunk.fill(0)
            try {
                val wire = DebugLanVoiceWireCodec.encode(voice)
                try { encrypted.transmit(wire) } finally { wire.fill(0) }
            } finally { voice.clear() }
        }
        val finish = DebugLanVoiceWireCodec.encode(DebugLanVoiceWireCodec.end(turn, count))
        try { encrypted.transmit(finish) } finally { finish.fill(0) }
        return count
    }

    override fun close() {
        val oldSender: Thread?
        synchronized(monitor) {
            if (closed) return
            closed = true
            oldSender = sender
            ledger.close()
            incomingVoice.clear()
            encrypted.clear()
            while (true) {
                val item = outbound.poll() ?: break
                item.bytes.fill(0)
                item.voiceClip?.fill(0)
            }
        }
        oldSender?.interrupt()
        // The controller closes the actual RFCOMM socket; no blocking join.
    }
}
