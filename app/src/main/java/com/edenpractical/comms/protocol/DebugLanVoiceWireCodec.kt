package com.edenpractical.comms.protocol

import java.io.IOException
import java.nio.ByteBuffer

/**
 * EXPERIMENTAL DEBUG LAN audio bursts on the already-pinned / both-approved
 * test TLS session ONLY. Not a standalone credential, encryption format or
 * production voice feature. 16-kHz mono signed 16-bit little-endian, 20ms.
 *
 * Wire: "ECVP", version(1), kind(1), reserved(2), turn(8),
 * frameSequence(2 unsigned), bodyLength(2), then exactly 640 PCM bytes for DATA.
 * START/END have empty bodies, and START sequence is 0. No compression,
 * background storage or resumption across a new TLS connection.
 */
internal object DebugLanVoiceWireCodec {
    const val PCM_BYTES = 640
    const val MAX_FRAMES = 150 // 3 seconds per hold-to-talk burst
    const val MAX_PCM_BYTES = PCM_BYTES * MAX_FRAMES
    const val HEADER_BYTES = 20
    private const val MAGIC = 0x45435650 // ECVP
    private const val VERSION = 1

    enum class Kind(val code: Int) { START(1), DATA(2), END(3) }
    class Frame(val kind: Kind, val turn: Long, val sequence: Int, val pcm: ByteArray) {
        fun clear() { pcm.fill(0) }
    }

    fun start(turn: Long): Frame = Frame(Kind.START, validateTurn(turn), 0, byteArrayOf())
    fun data(turn: Long, sequence: Int, pcm: ByteArray): Frame {
        require(sequence in 0 until MAX_FRAMES && pcm.size == PCM_BYTES) {
            "Invalid bounded voice PCM frame"
        }
        return Frame(Kind.DATA, validateTurn(turn), sequence, pcm.copyOf())
    }
    fun end(turn: Long, sequence: Int): Frame {
        require(sequence in 1..MAX_FRAMES) { "Invalid voice finish count" }
        return Frame(Kind.END, validateTurn(turn), sequence, byteArrayOf())
    }

    fun encode(frame: Frame): ByteArray {
        require(frame.turn > 0L && frame.sequence in 0..MAX_FRAMES)
        val hasPcm = frame.kind == Kind.DATA
        require(frame.pcm.size == if (hasPcm) PCM_BYTES else 0)
        require(when (frame.kind) {
            Kind.START -> frame.sequence == 0
            Kind.DATA -> frame.sequence < MAX_FRAMES
            Kind.END -> frame.sequence > 0
        })
        return ByteBuffer.allocate(HEADER_BYTES + frame.pcm.size)
            .putInt(MAGIC).put(VERSION.toByte()).put(frame.kind.code.toByte())
            .putShort(0).putLong(frame.turn).putShort(frame.sequence.toShort())
            .putShort(frame.pcm.size.toShort()).put(frame.pcm).array()
    }

    @Throws(IOException::class)
    fun decode(bytes: ByteArray): Frame {
        if (bytes.size !in HEADER_BYTES..(HEADER_BYTES + PCM_BYTES))
            throw TestPacketException()
        val buf = ByteBuffer.wrap(bytes)
        val magic = buf.int
        val version = buf.get().toInt() and 0xff
        val code = buf.get().toInt() and 0xff
        val reserved = buf.short.toInt()
        val turn = buf.long
        val sequence = buf.short.toInt() and 0xffff
        val length = buf.short.toInt() and 0xffff
        val kind = Kind.entries.firstOrNull { it.code == code }
            ?: throw TestPacketException()
        val expectBody = if (kind == Kind.DATA) PCM_BYTES else 0
        if (magic != MAGIC || version != VERSION || reserved != 0 ||
            turn <= 0L || length != expectBody || bytes.size != HEADER_BYTES + length ||
            sequence > MAX_FRAMES ||
            (kind == Kind.START && sequence != 0) ||
            (kind == Kind.DATA && sequence >= MAX_FRAMES) ||
            (kind == Kind.END && sequence == 0)
        ) throw TestPacketException()
        return Frame(kind, turn, sequence, ByteArray(length).also { buf.get(it) })
    }

    private fun validateTurn(turn: Long): Long {
        require(turn > 0) { "Voice turn must be positive" }
        return turn
    }
}

/** One incoming turn, exact sequences, no queue of archived clips or user IDs. */
internal class DebugLanVoiceAssembler {
    private val data = ByteArray(DebugLanVoiceWireCodec.MAX_PCM_BYTES)
    private var activeTurn = 0L
    private var completedTurn = 0L
    private var next = 0
    private var failed = false

    @Synchronized
    @Throws(IOException::class)
    fun accept(frame: DebugLanVoiceWireCodec.Frame): ByteArray? {
        if (failed) throw TestPacketException()
        try {
            when (frame.kind) {
                DebugLanVoiceWireCodec.Kind.START -> {
                    if (activeTurn != 0L || next != 0 || frame.sequence != 0 ||
                        frame.turn <= completedTurn)
                        throw TestPacketException()
                    activeTurn = frame.turn
                    return null
                }
                DebugLanVoiceWireCodec.Kind.DATA -> {
                    if (activeTurn == 0L || activeTurn != frame.turn ||
                        frame.sequence != next || next >= DebugLanVoiceWireCodec.MAX_FRAMES
                    ) throw TestPacketException()
                    frame.pcm.copyInto(data, next * DebugLanVoiceWireCodec.PCM_BYTES)
                    next++
                    return null
                }
                DebugLanVoiceWireCodec.Kind.END -> {
                    if (activeTurn == 0L || activeTurn != frame.turn ||
                        frame.sequence != next || next == 0
                    ) throw TestPacketException()
                    val clip = data.copyOf(next * DebugLanVoiceWireCodec.PCM_BYTES)
                    completedTurn = activeTurn
                    resetCurrent()
                    return clip
                }
            }
        } catch (e: Exception) {
            failed = true
            data.fill(0)
            activeTurn = 0L
            next = 0
            throw e
        }
    }

    private fun resetCurrent() {
        data.fill(0)
        activeTurn = 0L
        next = 0
    }

    @Synchronized
    fun clear() {
        failed = true
        resetCurrent()
    }
}
