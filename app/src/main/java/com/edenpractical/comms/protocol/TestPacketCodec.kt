package com.edenpractical.comms.protocol

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Fixed, non-user-supplied test phrases and approval control for DEVELOPMENT-ONLY local TLS
 * session. This is NOT a production messaging format or an endorsement of
 * sending sensitive information through an unaudited application.
 *
 * FrameCodec gives a bounded envelope; this layer accepts only a fixed
 * version, packet type, and known phrase IDs. Never use on plaintext sockets.
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

    data class Phrase(val id: Int, val label: String)
    val phrases: List<Phrase> = listOf(
        Phrase(1, "Hello!"),
        Phrase(2, "Message received."),
        Phrase(3, "Testing the connection."),
        Phrase(4, "Can you read this?")
    )

    enum class Type { READY, PRESET, HEARTBEAT, CLOSE, APPROVE, DECLINE }
    data class Packet(val type: Type, val phraseId: Int = 0)

    fun phrase(id: Int): String = phrases.firstOrNull { it.id == id }?.label
        ?: throw IllegalArgumentException("Unrecognized test phrase")

    fun ready(): Packet = Packet(Type.READY)
    fun preset(id: Int): Packet {
        phrase(id) // Validate at point of creation, not just on decode.
        return Packet(Type.PRESET, id)
    }
    fun heartbeat(): Packet = Packet(Type.HEARTBEAT)
    fun close(): Packet = Packet(Type.CLOSE)
    fun approve(): Packet = Packet(Type.APPROVE)
    fun decline(): Packet = Packet(Type.DECLINE)

    fun encode(packet: Packet): ByteArray {
        val wireType = when (packet.type) {
            Type.READY -> READY
            Type.PRESET -> PRESET
            Type.HEARTBEAT -> HEARTBEAT
            Type.CLOSE -> CLOSE
            Type.APPROVE -> APPROVE
            Type.DECLINE -> DECLINE
        }
        if (packet.type == Type.PRESET) phrase(packet.phraseId)
        else require(packet.phraseId == 0) { "Control packets have no text" }
        return byteArrayOf(VERSION.toByte(), wireType.toByte(), packet.phraseId.toByte())
    }

    fun decode(bytes: ByteArray): Packet {
        if (bytes.size != 3 || (bytes[0].toInt() and 0xFF) != VERSION)
            throw TestPacketException()
        val type = when (bytes[1].toInt() and 0xFF) {
            READY -> Type.READY
            PRESET -> Type.PRESET
            HEARTBEAT -> Type.HEARTBEAT
            CLOSE -> Type.CLOSE
            APPROVE -> Type.APPROVE
            DECLINE -> Type.DECLINE
            else -> throw TestPacketException()
        }
        val id = bytes[2].toInt() and 0xFF
        if (type == Type.PRESET) {
            if (phrases.none { it.id == id }) throw TestPacketException()
        } else if (id != 0) throw TestPacketException()
        return Packet(type, id)
    }

    fun write(out: OutputStream, packet: Packet) {
        out.write(FrameCodec.encode(encode(packet)))
        out.flush()
    }

    fun read(input: InputStream): Packet = decode(FrameCodec.readFrom(input))
}
internal class TestPacketException : IOException("Invalid development test packet")
