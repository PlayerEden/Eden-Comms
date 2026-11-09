package com.edenpractical.comms.bluetooth

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * F2-F: DEBUG-ONLY future encrypted Bluetooth TEXT/ACK plaintext frames.
 *
 * F2-Q: used by the actual post-confirmation debug encrypted Bluetooth
 * channel. This codec ALONE is still never proof of a verified peer.
 * The bytes returned by this codec are NOT encrypted/authenticated; a future
 * separately reviewed TLS adapter must gate actual I/O on a live, mutually
 * approved, identity-bound connection. NEVER send these bytes over bare RFCOMM.
 *
 * Format (big-endian): EC BT magic (4), version (1), kind (1), positive
 * message ID (8), payload length (2), then UTF-8 payload (TEXT only).
 * An ACK has no payload; it means only "peer app accepted the frame", not
 * that a human saw it, and is meaningful only on the verified TLS session.
 */
internal object DebugBluetoothTextFrameCodec {
    const val HEADER_BYTES = 16
    const val MAX_TEXT_BYTES = 512
    const val MAX_FRAME_BYTES = HEADER_BYTES + MAX_TEXT_BYTES

    private const val MAGIC = 0x45434254 // "ECBT"
    private const val VERSION = 1
    private const val TEXT = 1
    private const val ACK = 2

    enum class Kind { TEXT, ACK }
    data class Frame(val kind: Kind, val messageId: Long, val text: String?)

    fun text(messageId: Long, plaintext: String): ByteArray {
        require(messageId > 0) { "Bluetooth message id must be positive" }
        require(plaintext.isNotBlank() && '\u0000' !in plaintext) {
            "Empty, blank or NUL Bluetooth test text rejected"
        }
        val encoded = StandardCharsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .encode(CharBuffer.wrap(plaintext))
        require(encoded.remaining() in 1..MAX_TEXT_BYTES) {
            "Bluetooth test message exceeds bounded UTF-8 size"
        }
        val output = ByteBuffer.allocate(HEADER_BYTES + encoded.remaining())
        header(output, TEXT, messageId, encoded.remaining())
        output.put(encoded)
        return output.array()
    }

    fun acknowledgment(messageId: Long): ByteArray {
        require(messageId > 0) { "Bluetooth ACK id must be positive" }
        val output = ByteBuffer.allocate(HEADER_BYTES)
        header(output, ACK, messageId, 0)
        return output.array()
    }

    /**
     * Strict single-frame decode; caller must supply ONE complete decrypted
     * record from the reviewed transport. No scanning, prefix acceptance or
     * application-level fragmentation/reassembly is performed here.
     */
    fun decode(bytes: ByteArray): Frame {
        require(bytes.size in HEADER_BYTES..MAX_FRAME_BYTES) {
            "Bluetooth frame length outside bounds"
        }
        val input = ByteBuffer.wrap(bytes)
        require(input.int == MAGIC && (input.get().toInt() and 255) == VERSION) {
            "Unknown Bluetooth frame magic/version"
        }
        val type = input.get().toInt() and 255
        val id = input.long
        val bodyLength = input.short.toInt() and 0xffff
        require(id > 0 && bodyLength <= MAX_TEXT_BYTES &&
            bytes.size == HEADER_BYTES + bodyLength
        ) { "Bluetooth frame identifier or exact length invalid" }
        return when (type) {
            TEXT -> {
                require(bodyLength > 0) { "Empty Bluetooth text payload" }
                val text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(input).toString()
                require(text.isNotBlank() && '\u0000' !in text) {
                    "Invalid Bluetooth text payload"
                }
                Frame(Kind.TEXT, id, text)
            }
            ACK -> {
                require(bodyLength == 0) { "Bluetooth ACK cannot carry content" }
                Frame(Kind.ACK, id, null)
            }
            else -> throw IllegalArgumentException("Unrecognized Bluetooth frame kind")
        }
    }

    private fun header(target: ByteBuffer, kind: Int, id: Long, length: Int) {
        target.putInt(MAGIC)
        target.put(VERSION.toByte())
        target.put(kind.toByte())
        target.putLong(id)
        target.putShort(length.toShort())
    }
}

/**
 * F2-F: per-connection bounded, ephemeral TEXT/ACK state only. This is a
 * deterministic protocol rehearsal; constructing a ledger NEVER authorizes
 * a Bluetooth session or confirms peer identity. Does not store message
 * bodies, recognize devices, or deliver receipts to an app UI.
 *
 * All methods are synchronized to prevent closing from racing frame state.
 * A new ledger must be created only by a future, independently reviewed
 * authenticated live-session owner; existing RFCOMM code does not use it.
 */
internal class DebugBluetoothTextLedger {
    companion object {
        const val MAX_MESSAGES_PER_DIRECTION = 128
        const val MAX_PENDING = 8
    }

    data class Received(val messageId: Long, val text: String, val ack: ByteArray)

    private var closed = false
    private var nextOutgoing = 1L
    private var nextIncoming = 1L
    private val pending = LinkedHashSet<Long>()

    @Synchronized
    fun prepareText(text: String): ByteArray {
        require(!closed) { "Bluetooth test ledger closed" }
        require(nextOutgoing <= MAX_MESSAGES_PER_DIRECTION && pending.size < MAX_PENDING) {
            "Bluetooth test text budget reached"
        }
        val id = nextOutgoing
        val bytes = DebugBluetoothTextFrameCodec.text(id, text)
        pending.add(id)
        nextOutgoing++
        return bytes
    }

    @Synchronized
    fun acceptText(bytes: ByteArray): Received {
        require(!closed && nextIncoming <= MAX_MESSAGES_PER_DIRECTION) {
            "Bluetooth test receive budget unavailable"
        }
        val frame = DebugBluetoothTextFrameCodec.decode(bytes)
        require(frame.kind == DebugBluetoothTextFrameCodec.Kind.TEXT &&
            frame.messageId == nextIncoming
        ) { "Bluetooth text duplicate, replay or skipped sequence" }
        val text = checkNotNull(frame.text)
        val ack = DebugBluetoothTextFrameCodec.acknowledgment(frame.messageId)
        nextIncoming++
        return Received(frame.messageId, text, ack)
    }

    @Synchronized
    fun acceptAck(bytes: ByteArray): Long {
        require(!closed) { "Bluetooth test ledger closed" }
        val frame = DebugBluetoothTextFrameCodec.decode(bytes)
        require(frame.kind == DebugBluetoothTextFrameCodec.Kind.ACK &&
            pending.remove(frame.messageId)
        ) { "Bluetooth ACK unknown, duplicate or early" }
        return frame.messageId
    }

    @Synchronized
    fun outstanding(): Int = pending.size

    @Synchronized
    fun close() {
        closed = true
        pending.clear()
    }
}
