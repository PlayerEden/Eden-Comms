package com.edenpractical.comms.protocol

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * UNREVIEWED candidate for a future text/ACK INNER payload. M3e reuses
 * its TEXT-only shape in a FLAG_DEBUGGABLE-gated, QR-pinned TLS test path.
 * ACK remains forbidden in that runtime. Never use for production chat.
 *
 * This is only a bounded, versioned serializer/parser. It provides ZERO
 * encryption, TLS authentication, peer identity, replay defense, bilateral
 * approval, delivery evidence, persistence, or permission to transmit.
 *
 * DO NOT dispatch these bytes to a socket or display ACK as received until a
 * separate independently reviewed session/consent binding is implemented.
 * The live LAN demo must remain restricted to four fixed test phrases.
 */
internal object ProposedChatWireCodec {
    private val MAGIC = byteArrayOf(0x45, 0x43, 0x54, 0x58) // "ECTX"
    private const val VERSION = 1
    private const val TYPE_TEXT = 1
    private const val TYPE_ACK = 2
    private const val FLAGS = 0

    const val HEADER_BYTES = 17 // magic(4), version(1), type(1), flags(1), ID(8), size(2)
    const val MAX_CODE_POINTS = 280
    const val MAX_UTF8_BYTES = MAX_CODE_POINTS * 4

    sealed class Packet {
        abstract val messageId: Long

        data class Text(
            override val messageId: Long,
            val content: String
        ) : Packet()

        /** Just an unauthenticated wire SHAPE, not evidence of receipt. */
        data class Ack(override val messageId: Long) : Packet()
    }

    fun encode(packet: Packet): ByteArray {
        if (packet.messageId <= 0L) throw ProposedChatWireException()
        val kind: Int
        val payload: ByteArray
        when (packet) {
            is Packet.Text -> {
                kind = TYPE_TEXT
                validateText(packet.content)
                payload = strictUtf8Encode(packet.content)
                if (payload.size !in 1..MAX_UTF8_BYTES) throw ProposedChatWireException()
            }
            is Packet.Ack -> {
                kind = TYPE_ACK
                payload = byteArrayOf()
            }
        }
        return ByteBuffer.allocate(HEADER_BYTES + payload.size)
            .order(ByteOrder.BIG_ENDIAN)
            .put(MAGIC)
            .put(VERSION.toByte())
            .put(kind.toByte())
            .put(FLAGS.toByte())
            .putLong(packet.messageId)
            .putShort(payload.size.toShort())
            .put(payload)
            .array()
    }

    fun decode(bytes: ByteArray): Packet {
        if (bytes.size !in HEADER_BYTES..(HEADER_BYTES + MAX_UTF8_BYTES))
            throw ProposedChatWireException()

        val input = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val magic = ByteArray(MAGIC.size)
        input.get(magic)
        if (!magic.contentEquals(MAGIC) ||
            input.get().toInt() != VERSION) throw ProposedChatWireException()

        val kind = input.get().toInt() and 0xFF
        if (input.get().toInt() != FLAGS) throw ProposedChatWireException()
        val messageId = input.long
        if (messageId <= 0L) throw ProposedChatWireException()

        val payloadSize = input.short.toInt() and 0xFFFF
        if (payloadSize != input.remaining()) throw ProposedChatWireException()

        return when (kind) {
            TYPE_ACK -> {
                if (payloadSize != 0) throw ProposedChatWireException()
                Packet.Ack(messageId)
            }
            TYPE_TEXT -> {
                if (payloadSize !in 1..MAX_UTF8_BYTES) throw ProposedChatWireException()
                val raw = ByteArray(payloadSize)
                input.get(raw)
                val text = strictUtf8Decode(raw)
                validateText(text)
                Packet.Text(messageId, text)
            }
            else -> throw ProposedChatWireException()
        }
    }

    private fun validateText(text: String) {
        if (text.isBlank() || text.codePointCount(0, text.length) > MAX_CODE_POINTS)
            throw ProposedChatWireException()
        // Forbid invisible control payloads other than intentionally typed tab
        // and newline, including DEL and C1 control characters. Surrogates are
        // separately rejected by strict UTF-8 encoding.
        if (text.codePoints().anyMatch { cp ->
                (cp < 0x20 && cp != 0x09 && cp != 0x0A) ||
                    cp in 0x7F..0x9F
            }) throw ProposedChatWireException()
    }

    private fun strictUtf8Encode(text: String): ByteArray {
        try {
            val output = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(java.nio.CharBuffer.wrap(text))
            return ByteArray(output.remaining()).also { output.get(it) }
        } catch (_: CharacterCodingException) {
            throw ProposedChatWireException()
        }
    }

    private fun strictUtf8Decode(bytes: ByteArray): String {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            throw ProposedChatWireException()
        }
    }
}

internal class ProposedChatWireException : IOException("Invalid review-only chat wire candidate")
