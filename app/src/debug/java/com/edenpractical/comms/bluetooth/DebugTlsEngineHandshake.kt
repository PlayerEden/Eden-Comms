package com.edenpractical.comms.bluetooth

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult
import javax.net.ssl.SSLException

/**
 * DEBUG handshake-only TLS 1.3 driver for a length-delimited TLS record
 * stream, such as RFCOMM. Does not expose an application-data read/write API.
 *
 * The owner must close the underlying BluetoothSocket for cancellation and
 * read timeout: InputStream.read() is allowed to block, but the RF controller
 * closes both sockets on background, permission loss, or its timer. No TLS
 * plaintext is accepted here. No fallback if the provider behaves differently.
 *
 * This is a prototype pending independent protocol/Android provider review.
 */
internal object DebugTlsEngineHandshake {
    const val MAX_RECORD_BODY = 18_432 // TLS 1.3 record limit + encryption overhead
    const val MAX_ENGINE_PACKET = 32_768
    const val MAX_STEPS = 256
    private val EMPTY = ByteBuffer.allocate(0)

    /** One complete TLS record only, no unbounded memory or magic-byte scan. */
    fun readRecord(input: InputStream): ByteArray {
        val header = ByteArray(5)
        readExactly(input, header)
        val type = header[0].toInt() and 0xff
        val legacyMajor = header[1].toInt() and 0xff
        val legacyMinor = header[2].toInt() and 0xff
        val length = ((header[3].toInt() and 255) shl 8) or (header[4].toInt() and 255)
        if (type !in 20..23 || legacyMajor != 3 || legacyMinor !in 1..3 ||
            length < 1 || length > MAX_RECORD_BODY
        ) throw SSLException("Malformed or oversized TLS record")
        return ByteArray(header.size + length).also { packet ->
            header.copyInto(packet)
            val body = ByteArray(length)
            readExactly(input, body)
            body.copyInto(packet, header.size)
            body.fill(0)
        }
    }

    private fun readExactly(input: InputStream, target: ByteArray) {
        var offset = 0
        var noProgress = 0
        while (offset < target.size) {
            val count = input.read(target, offset, target.size - offset)
            if (count < 0) throw EOFException("Truncated TLS record")
            if (count == 0) {
                if (++noProgress > 16) throw IOException("TLS record read made no progress")
            } else {
                noProgress = 0
                offset += count
            }
        }
    }

    /**
     * Perform only the initial handshake; NEVER send user text/voice or read
     * decrypted app payloads. The caller must verify TLSv1.3 and exact peer
     * certificate fingerprint after this returns, then close the transport.
     */
    fun negotiate(engine: SSLEngine, input: InputStream, output: OutputStream) {
        val packetCapacity = engine.session.packetBufferSize
        val applicationCapacity = engine.session.applicationBufferSize
        if (packetCapacity !in 1..MAX_ENGINE_PACKET ||
            applicationCapacity !in 1..MAX_ENGINE_PACKET
        ) throw SSLException("TLS provider buffer size exceeds diagnostic bounds")
        val app = ByteBuffer.allocate(applicationCapacity)
        var finished = false
        var packetsSeen = 0
        engine.beginHandshake()
        repeat(MAX_STEPS) {
            val status = engine.handshakeStatus
            if (status == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
                if (finished && packetsSeen > 0) return
                throw SSLException("No completed initial TLS handshake")
            }
            when (status) {
                SSLEngineResult.HandshakeStatus.NEED_TASK -> {
                    var taskCount = 0
                    while (true) {
                        val task = engine.delegatedTask ?: break
                        if (++taskCount > 64) throw SSLException("Too many TLS delegated tasks")
                        task.run()
                    }
                }
                SSLEngineResult.HandshakeStatus.NEED_WRAP -> {
                    val outputRecord = ByteBuffer.allocate(packetCapacity)
                    val result = engine.wrap(EMPTY.duplicate(), outputRecord)
                    if (result.status != SSLEngineResult.Status.OK ||
                        result.bytesConsumed() != 0 ||
                        result.bytesProduced() !in 1..MAX_ENGINE_PACKET
                    ) throw SSLException("Unexpected TLS handshake wrap")
                    outputRecord.flip()
                    val packet = ByteArray(outputRecord.remaining())
                    outputRecord.get(packet)
                    // The engine constructs TLS wire records; never send
                    // application plaintext or downgrade to a raw fallback.
                    output.write(packet)
                    output.flush()
                    packet.fill(0)
                    packetsSeen++
                    if (result.handshakeStatus ==
                        SSLEngineResult.HandshakeStatus.FINISHED
                    ) finished = true
                }
                SSLEngineResult.HandshakeStatus.NEED_UNWRAP -> {
                    val record = readRecord(input)
                    app.clear()
                    val result = engine.unwrap(ByteBuffer.wrap(record), app)
                    val recordLength = record.size
                    record.fill(0)
                    if (result.status != SSLEngineResult.Status.OK ||
                        result.bytesProduced() != 0 ||
                        result.bytesConsumed() != recordLength
                    ) throw SSLException("Unexpected TLS handshake unwrap")
                    packetsSeen++
                    if (result.handshakeStatus ==
                        SSLEngineResult.HandshakeStatus.FINISHED
                    ) finished = true
                }
                SSLEngineResult.HandshakeStatus.FINISHED -> {
                    finished = true
                }
                SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING -> Unit
            }
        }
        throw SSLException("TLS handshake exceeded bounded diagnostic steps")
    }
}
