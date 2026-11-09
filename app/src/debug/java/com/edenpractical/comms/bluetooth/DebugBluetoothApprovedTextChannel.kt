package com.edenpractical.comms.bluetooth

import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import com.edenpractical.comms.protocol.DebugLanVoiceWireCodec
import com.edenpractical.comms.protocol.DebugLanVoiceAssembler
import com.edenpractical.comms.protocol.DebugLanVoiceFloorCodec
import com.edenpractical.comms.transport.DebugLanVoiceFloor
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
    private val isHost: Boolean = false,
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
        data class LiveVoiceFrame(
            val kind: DebugLanVoiceWireCodec.Kind,
            /** Live DATA PCM is zeroed after the Activity consumes the callback. */
            val pcm: ByteArray
        ) : Event()
        object FloorGranted : Event()
        object FloorDenied : Event()
    }

    private data class Pending(
        val bytes: ByteArray,
        val writtenText: String? = null,
        val voiceClip: ByteArray? = null,
        val voiceEndFrames: Int = 0
    )
    private val monitor = Any()
    private val ledger = DebugBluetoothTextLedger()
    private val incomingVoice = DebugLanVoiceAssembler()
    private val floor = DebugLanVoiceFloor(isHost)
    private var nextVoiceTurn = 1L
    private var liveTurn = 0L
    private var liveSequence = 0
    private var liveVoiceActive = false
    private var voicePending = false
    private val encrypted = DebugBluetoothFutureTlsTextLab(engine, wireInput, wireOutput)
    private val outbound = ArrayBlockingQueue<Pending>(10)
    private var sender: Thread? = null
    private var leaseWatchdog: Thread? = null
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
        // Retained for F4-U regression only. Even the legacy whole-clip
        // path now requires a negotiated encrypted talk floor.
        if (closed || voicePending || liveVoiceActive || !floor.maySendAudio() ||
            outbound.remainingCapacity() <= 1 ||
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

    private fun nowMs(): Long = System.nanoTime() / 1_000_000L

    /** Always ask inside the CURRENT approved channel. Host arbitrates. */
    fun requestVoiceFloor(): DebugLanVoiceFloor.Ask = synchronized(monitor) {
        if (closed) return@synchronized DebugLanVoiceFloor.Ask.BUSY
        val request = floor.requestLocal(nowMs())
        if (request.packet != null && !enqueueFloor(request.packet)) {
            failClosed()
            return@synchronized DebugLanVoiceFloor.Ask.BUSY
        }
        request.status
    }

    /** A released press must NOT activate after a late GRANT. */
    fun cancelUnstartedVoiceFloor() = synchronized(monitor) {
        if (closed || liveVoiceActive || voicePending) return@synchronized
        val packet = floor.cancelPending() ?: floor.releaseLocal()
        if (packet != null && !enqueueFloor(packet)) failClosed()
    }

    /** Reserve a floor for foreground per-frame microphone capture. */
    fun beginLiveVoice(): Boolean = synchronized(monitor) {
        if (closed || liveVoiceActive || voicePending || !floor.maySendAudio())
            return@synchronized false
        if (nextVoiceTurn == Long.MAX_VALUE) {
            failClosed()
            return@synchronized false
        }
        liveTurn = nextVoiceTurn++
        liveSequence = 0
        liveVoiceActive = true
        true
    }

    /**
     * Actual 20ms PCM from the separately owned mic driver, while HELD.
     * Keep room for END and RELEASE; overload FAILS the current session
     * rather than silently truncating valid spoken audio.
     */
    fun offerLiveVoiceFrame(pcm: ByteArray): Boolean = synchronized(monitor) {
        if (closed || !liveVoiceActive || !floor.maySendAudio() ||
            pcm.size != DebugLanVoiceWireCodec.PCM_BYTES ||
            liveSequence >= DebugLanVoiceWireCodec.MAX_FRAMES
        ) return@synchronized false
        val first = liveSequence == 0
        if (outbound.remainingCapacity() < (if (first) 5 else 4)) {
            failClosed()
            return@synchronized false
        }
        if (first) {
            val start = DebugLanVoiceWireCodec.encode(
                DebugLanVoiceWireCodec.start(liveTurn)
            )
            if (!outbound.offer(Pending(start))) {
                start.fill(0)
                failClosed()
                return@synchronized false
            }
        }
        val body = DebugLanVoiceWireCodec.data(liveTurn, liveSequence, pcm)
        try {
            val bytes = DebugLanVoiceWireCodec.encode(body)
            if (!outbound.offer(Pending(bytes))) {
                bytes.fill(0)
                failClosed()
                return@synchronized false
            }
        } finally { body.clear() }
        liveSequence++
        true
    }

    /** Called by the mic thread AFTER its final accepted PCM callback. */
    fun finishLiveVoice(): Boolean = synchronized(monitor) {
        if (closed || !liveVoiceActive) return@synchronized false
        liveVoiceActive = false
        val count = liveSequence
        if (count > 0) {
            val end = DebugLanVoiceWireCodec.encode(
                DebugLanVoiceWireCodec.end(liveTurn, count)
            )
            if (!outbound.offer(Pending(end, voiceEndFrames = count))) {
                end.fill(0)
                failClosed()
                return@synchronized false
            }
        }
        liveSequence = 0
        liveTurn = 0L
        val release = floor.releaseLocal()
        if (release != null && !enqueueFloor(release)) {
            failClosed()
            return@synchronized false
        }
        true
    }

    private fun enqueueFloor(control: DebugLanVoiceFloorCodec.Frame): Boolean {
        val bytes = DebugLanVoiceFloorCodec.encode(control)
        if (outbound.offer(Pending(bytes))) return true
        bytes.fill(0)
        return false
    }

    private fun failClosed() {
        if (closed) return
        onEvent(Event.Failed)
        close()
    }

    /** The RFCOMM worker stays on this verified session until End / loss. */
    fun runUntilClosed() {
        val started = synchronized(monitor) {
            if (closed || sender != null) false
            else {
                sender = Thread({ writeLoop() }, "EC-debug-bt-encrypted-writer")
                sender?.start()
                leaseWatchdog = Thread({
                    try {
                        while (!closed) {
                            Thread.sleep(100)
                            if (floor.timedOut(nowMs())) {
                                failClosed()
                                break
                            }
                        }
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                }, "EC-debug-nearby-voice-lease").also {
                    it.isDaemon = true
                    it.start()
                }
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
                        DebugNearbyApplicationFrame.Kind.FLOOR -> {
                            val outcome = floor.incoming(
                                DebugLanVoiceFloorCodec.decode(frame), nowMs()
                            )
                            val reply = outcome.outbound
                            if (reply != null && !enqueueFloor(reply))
                                throw IllegalStateException("Encrypted floor response queue full")
                            when (outcome.signal) {
                                DebugLanVoiceFloor.Signal.GRANTED ->
                                    if (!closed) onEvent(Event.FloorGranted)
                                DebugLanVoiceFloor.Signal.DENIED ->
                                    if (!closed) onEvent(Event.FloorDenied)
                                DebugLanVoiceFloor.Signal.NONE -> Unit
                            }
                        }
                        DebugNearbyApplicationFrame.Kind.VOICE -> {
                            val voice = DebugLanVoiceWireCodec.decode(frame)
                            try {
                                if (voice.kind == DebugLanVoiceWireCodec.Kind.START)
                                    floor.remoteAudioStart()
                                val complete = incomingVoice.accept(voice)
                                if (voice.kind == DebugLanVoiceWireCodec.Kind.END)
                                    floor.remoteAudioEnd()
                                if (!closed) onEvent(Event.LiveVoiceFrame(
                                    voice.kind, voice.pcm.copyOf()
                                ))
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
                        if (!closed && pending.voiceEndFrames > 0)
                            onEvent(Event.VoiceWritten(pending.voiceEndFrames))
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
        // Every legacy captured burst must also release the negotiated floor
        // before another person can speak. This happens after END on wire.
        val released = floor.releaseLocal()
        if (released != null) {
            val bytes = DebugLanVoiceFloorCodec.encode(released)
            try { encrypted.transmit(bytes) } finally { bytes.fill(0) }
        }
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
            floor.close()
            liveVoiceActive = false
            encrypted.clear()
            while (true) {
                val item = outbound.poll() ?: break
                item.bytes.fill(0)
                item.voiceClip?.fill(0)
            }
        }
        oldSender?.interrupt()
        leaseWatchdog?.interrupt()
        // The controller closes the actual RFCOMM socket; no blocking join.
    }
}
