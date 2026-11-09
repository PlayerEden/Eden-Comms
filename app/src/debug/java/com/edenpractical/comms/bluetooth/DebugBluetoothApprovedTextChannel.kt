package com.edenpractical.comms.bluetooth

import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
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
    }

    private data class Pending(val bytes: ByteArray, val writtenText: String? = null)
    private val monitor = Any()
    private val ledger = DebugBluetoothTextLedger()
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
                    when (DebugBluetoothTextFrameCodec.decode(frame).kind) {
                        DebugBluetoothTextFrameCodec.Kind.TEXT -> {
                            val accepted = ledger.acceptText(frame)
                            synchronized(monitor) {
                                if (closed || !outbound.offer(Pending(accepted.ack))) {
                                    accepted.ack.fill(0)
                                    throw IllegalStateException("Bluetooth ACK queue unavailable")
                                }
                            }
                            if (!closed) onEvent(Event.Received(accepted.messageId, accepted.text))
                        }
                        DebugBluetoothTextFrameCodec.Kind.ACK -> {
                            val id = ledger.acceptAck(frame)
                            if (!closed) onEvent(Event.Acknowledged(id))
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
                    encrypted.transmit(pending.bytes)
                    if (!closed && pending.writtenText != null) {
                        val id = DebugBluetoothTextFrameCodec.decode(pending.bytes).messageId
                        onEvent(Event.Written(id, pending.writtenText))
                    }
                } finally {
                    pending.bytes.fill(0)
                }
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (_: Exception) {
            // The real controller owns the socket; a failed writer must force
            // the receive worker to unblock and stop through its callback.
            onEvent(Event.Failed)
            close()
        }
    }

    override fun close() {
        val oldSender: Thread?
        synchronized(monitor) {
            if (closed) return
            closed = true
            oldSender = sender
            ledger.close()
            encrypted.clear()
            while (true) {
                val item = outbound.poll() ?: break
                item.bytes.fill(0)
            }
        }
        oldSender?.interrupt()
        // The controller closes the actual RFCOMM socket; no blocking join.
    }
}
