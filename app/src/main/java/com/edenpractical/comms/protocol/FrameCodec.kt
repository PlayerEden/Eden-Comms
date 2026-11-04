package com.edenpractical.comms.protocol

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Bounded binary framing for a FUTURE cryptographically authenticated channel.
 *
 * This class does not encrypt or authenticate anything. It MUST NOT be used on
 * a plaintext socket. All bytes passed here must travel inside a reviewed,
 * already authenticated, encrypted transport, after BOTH peers have verified.
 * It is not currently connected to any Android networking API.
 */
internal object FrameCodec {
    private const val FORMAT_VERSION = 1
    private const val RESERVED_FLAGS = 0
    private val MAGIC = byteArrayOf(0x45, 0x43, 0x4F, 0x4D) // "ECOM"

    const val HEADER_BYTES: Int = 10
    const val MAX_PAYLOAD_BYTES: Int = 16 * 1024

    /**
     * Encode one opaque payload. Does NOT encrypt. Call only inside an
     * already-authenticated secure channel, never on a raw radio/socket.
     */
    fun encode(payload: ByteArray): ByteArray {
        require(payload.isNotEmpty() && payload.size <= MAX_PAYLOAD_BYTES) {
            "Invalid frame payload length"
        }
        val bytes = ByteArrayOutputStream(HEADER_BYTES + payload.size)
        DataOutputStream(bytes).use { out ->
            out.write(MAGIC)
            out.writeByte(FORMAT_VERSION)
            out.writeByte(RESERVED_FLAGS)
            out.writeInt(payload.size)
            out.write(payload)
        }
        return bytes.toByteArray()
    }

    /**
     * Read exactly one frame from a stream, allowing sequential frames.
     * Validate its length BEFORE allocation, stopping malformed inputs early.
     */
    fun readFrom(input: InputStream): ByteArray {
        val data = DataInputStream(input)
        val signature = ByteArray(MAGIC.size)
        data.readFully(signature)
        if (!signature.contentEquals(MAGIC)) {
            throw FrameFormatException("Invalid protocol marker")
        }
        val version = data.readUnsignedByte()
        if (version != FORMAT_VERSION) {
            throw FrameFormatException("Unsupported frame version")
        }
        val flags = data.readUnsignedByte()
        if (flags != RESERVED_FLAGS) {
            throw FrameFormatException("Unsupported frame flags")
        }
        val length = data.readInt()
        if (length < 1 || length > MAX_PAYLOAD_BYTES) {
            throw FrameFormatException("Frame length outside allowed bounds")
        }
        return ByteArray(length).also { data.readFully(it) }
    }
}

internal class FrameFormatException(message: String) : IOException(message)
