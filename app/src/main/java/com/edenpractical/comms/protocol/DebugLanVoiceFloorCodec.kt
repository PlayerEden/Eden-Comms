package com.edenpractical.comms.protocol

import java.nio.ByteBuffer

/**
 * F4-F experimental encrypted-session-only PTT floor control.
 *
 * Fixed 16 bytes: ASCII ECFL, version=1, kind, zero reserved, positive
 * 64-bit request ID. No peer address, key, identity, content or plaintext
 * network path. These bytes are meaningful ONLY inside the live approved TLS
 * session, and must never be interpreted as a standalone permission.
 */
internal object DebugLanVoiceFloorCodec {
    private const val MAGIC = 0x4543464c // ECFL
    private const val VERSION = 1
    const val BYTES = 16

    enum class Kind(val value: Int) {
        REQUEST(1), GRANT(2), DENY(3), CANCEL(4), RELEASE(5)
    }

    data class Frame(val kind: Kind, val requestId: Long) {
        init { require(requestId > 0L) }
    }

    fun encode(frame: Frame): ByteArray =
        ByteBuffer.allocate(BYTES)
            .putInt(MAGIC)
            .put(VERSION.toByte())
            .put(frame.kind.value.toByte())
            .putShort(0)
            .putLong(frame.requestId)
            .array()

    fun decode(bytes: ByteArray): Frame {
        if (bytes.size != BYTES) throw TestPacketException()
        val buf = ByteBuffer.wrap(bytes)
        if (buf.int != MAGIC || (buf.get().toInt() and 0xff) != VERSION)
            throw TestPacketException()
        val type = buf.get().toInt() and 0xff
        if (buf.short.toInt() != 0) throw TestPacketException()
        val id = buf.long
        if (id <= 0L) throw TestPacketException()
        return Frame(Kind.entries.firstOrNull { it.value == type }
            ?: throw TestPacketException(), id)
    }
}
