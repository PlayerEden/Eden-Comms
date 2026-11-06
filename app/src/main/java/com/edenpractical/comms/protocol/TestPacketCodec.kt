package com.edenpractical.comms.protocol

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Strict debug-only control frames and bounded development text envelopes
 * inside the already-pinned local TLS test session. These are NOT production
 * messaging formats or an endorsement of sending sensitive information through
 * an unaudited application. Development typed text is never used in release.
 *
 * FrameCodec bounds the outer envelope. Control packets accept only fixed
 * versions, known phrase IDs and session-bound 32-byte approval commitments.
 * Debug TEXT reuses the independently *unreviewed* ECTX candidate parser.
 * ACK is deliberately still REJECTED: no false receipt semantics.
 * Old 3-byte approvals fail closed.
 * Never use on plaintext sockets.
 */
internal object TestPacketCodec {
    private const val VERSION = 1
    private const val READY = 1
    private const val PRESET = 2
    private const val HEARTBEAT = 3
    private const val CLOSE = 4
    // Explicit consent experiment, DEBUG-only, not a reviewed auth protocol.
    private const val APPROVE = 5
    private const val DECLINE = 6
    private const val CONFIRM = 7
    private const val COMMITMENT_BYTES = 32

    data class Phrase(val id: Int, val label: String)
    val phrases: List<Phrase> = listOf(
        Phrase(1, "Hello!"),
        Phrase(2, "Message received."),
        Phrase(3, "Testing the connection."),
        Phrase(4, "Can you read this?")
    )

    enum class Type { READY, PRESET, HEARTBEAT, CLOSE, APPROVE, DECLINE, CONFIRM, DEBUG_TEXT }
    // List makes byte-wise data class equality work in JVM wire tests.
    // Consent commitments are debug-only, never a transport secret.
    data class Packet(
        val type: Type,
        val phraseId: Int = 0,
        val sessionCommitment: List<Byte> = emptyList(),
        val messageId: Long = 0,
        val text: String = ""
    )

    fun phrase(id: Int): String = phrases.firstOrNull { it.id == id }?.label
        ?: throw IllegalArgumentException("Unrecognized test phrase")

    fun ready(): Packet = Packet(Type.READY)
    fun preset(id: Int): Packet {
        phrase(id) // Validate at point of creation, not just on decode.
        return Packet(Type.PRESET, id)
    }
    fun heartbeat(): Packet = Packet(Type.HEARTBEAT)
    fun close(): Packet = Packet(Type.CLOSE)
    fun approve(commitment: ByteArray): Packet = boundControl(Type.APPROVE, commitment)
    fun decline(commitment: ByteArray): Packet = boundControl(Type.DECLINE, commitment)
    fun confirm(commitment: ByteArray): Packet = boundControl(Type.CONFIRM, commitment)

    /**
     * Only LanDemoSession may send this over the debuggable pinned-TLS
     * four-confirmation test path. Parser enforces strict UTF-8 and bounds.
     */
    fun debugText(messageId: Long, content: String): Packet {
        val packet = Packet(Type.DEBUG_TEXT, messageId = messageId, text = content)
        encode(packet) // Validate before queuing; fail closed on bad Unicode/size.
        return packet
    }

    private fun boundControl(type: Type, commitment: ByteArray): Packet {
        require(commitment.size == COMMITMENT_BYTES &&
            commitment.any { it != 0.toByte() }) { "Invalid debug session commitment" }
        return Packet(type, sessionCommitment = commitment.toList())
    }

    fun encode(packet: Packet): ByteArray {
        if (packet.type == Type.DEBUG_TEXT) {
            require(packet.phraseId == 0 && packet.sessionCommitment.isEmpty()) {
                "Debug typed text cannot carry approval fields"
            }
            return ProposedChatWireCodec.encode(
                ProposedChatWireCodec.Packet.Text(packet.messageId, packet.text)
            )
        }
        require(packet.messageId == 0L && packet.text.isEmpty()) {
            "Only debug typed text may carry text or message IDs"
        }
        val wireType = when (packet.type) {
            Type.READY -> READY
            Type.PRESET -> PRESET
            Type.HEARTBEAT -> HEARTBEAT
            Type.CLOSE -> CLOSE
            Type.APPROVE -> APPROVE
            Type.DECLINE -> DECLINE
            Type.CONFIRM -> CONFIRM
            Type.DEBUG_TEXT -> error("Debug text handled before controls")
        }
        if (packet.type == Type.PRESET) phrase(packet.phraseId)
        else require(packet.phraseId == 0) { "Control packets have no text" }
        val consent = packet.type == Type.APPROVE ||
            packet.type == Type.DECLINE || packet.type == Type.CONFIRM
        if (consent) {
            require(packet.sessionCommitment.size == COMMITMENT_BYTES &&
                packet.sessionCommitment.any { it != 0.toByte() }) {
                "Approval must be scoped to a nonzero test session commitment"
            }
        } else {
            require(packet.sessionCommitment.isEmpty()) {
                "Only experimental approval controls may include a commitment"
            }
        }
        val header = byteArrayOf(
            VERSION.toByte(), wireType.toByte(), packet.phraseId.toByte()
        )
        return if (consent) header + packet.sessionCommitment.toByteArray() else header
    }

    fun decode(bytes: ByteArray): Packet {
        // The opaque candidate inner payload is accepted ONLY for a TEXT
        // shape. Peer ACK cannot imply delivered/read, and is rejected here.
        if (bytes.size >= 4 && bytes[0] == 0x45.toByte() &&
            bytes[1] == 0x43.toByte() && bytes[2] == 0x54.toByte() &&
            bytes[3] == 0x58.toByte()
        ) {
            val candidate = ProposedChatWireCodec.decode(bytes)
            if (candidate !is ProposedChatWireCodec.Packet.Text) throw TestPacketException()
            return Packet(
                Type.DEBUG_TEXT, messageId = candidate.messageId,
                text = candidate.content
            )
        }
        if (bytes.size < 3 || (bytes[0].toInt() and 0xFF) != VERSION)
            throw TestPacketException()
        val type = when (bytes[1].toInt() and 0xFF) {
            READY -> Type.READY
            PRESET -> Type.PRESET
            HEARTBEAT -> Type.HEARTBEAT
            CLOSE -> Type.CLOSE
            APPROVE -> Type.APPROVE
            DECLINE -> Type.DECLINE
            CONFIRM -> Type.CONFIRM
            else -> throw TestPacketException()
        }
        val id = bytes[2].toInt() and 0xFF
        val consent = type == Type.APPROVE ||
            type == Type.DECLINE || type == Type.CONFIRM
        if (bytes.size != if (consent) 3 + COMMITMENT_BYTES else 3)
            throw TestPacketException()
        if (type == Type.PRESET) {
            if (phrases.none { it.id == id }) throw TestPacketException()
        } else if (id != 0) throw TestPacketException()
        if (!consent) return Packet(type, id)
        val commitment = bytes.copyOfRange(3, bytes.size)
        try {
            if (commitment.all { it == 0.toByte() }) throw TestPacketException()
            return Packet(type, id, commitment.toList())
        } finally {
            commitment.fill(0)
        }
    }

    fun write(out: OutputStream, packet: Packet) {
        out.write(FrameCodec.encode(encode(packet)))
        out.flush()
    }

    fun read(input: InputStream): Packet = decode(FrameCodec.readFrom(input))
}
internal class TestPacketException : IOException("Invalid development test packet")
