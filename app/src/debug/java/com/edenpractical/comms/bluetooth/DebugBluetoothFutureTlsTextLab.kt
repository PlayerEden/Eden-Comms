package com.edenpractical.comms.bluetooth

import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLException

/**
 * F2-G: UNWIRED, DEBUG-ONLY encrypted TEXT/ACK integration rehearsal.
 *
 * Exercises one F2-F frame inside exactly one SSLEngine-produced TLS 1.3
 * encrypted record and recovers the same exact authenticated plaintext on
 * the opposite engine. Does NOT establish any trust, session permission,
 * phone pairing, approval, UI or owned Bluetooth connection. Supplying an
 * SSLEngine to this object is not authorization to transmit.
 *
 * The real DebugRfcommTlsProbe and DebugRfcommLinkController MUST NOT reference
 * this lab. A future reviewed live-session owner needs independently checked
 * QR identity, exporter/transcript, both human confirmations, a bounded radio
 * socket lifetime and actual Android/Conscrypt provider acceptance.
 *
 * Calls are serialized by the future caller: a concurrent reader/writer or
 * multiplexed control/data TLS stream is not defined or approved here.
 * Blocking read cancellation is the caller's responsibility (close the pipes
 * or, in a future independently reviewed integration, the owned socket).
 */
internal class DebugBluetoothFutureTlsTextLab(
    private val engine: SSLEngine,
    private val wireInput: InputStream,
    private val wireOutput: OutputStream
) {
    @Volatile private var closed = false

    fun transmit(frame: ByteArray) {
        try {
            ready()
            // Never encrypt an unsupported/partial TEXT or ACK frame.
            DebugBluetoothTextFrameCodec.decode(frame)
            val encrypted = ByteBuffer.allocate(engine.session.packetBufferSize)
            try {
                val result = engine.wrap(ByteBuffer.wrap(frame), encrypted)
                if (result.bytesProduced() !in 1..DebugTlsEngineHandshake.MAX_ENGINE_PACKET)
                    throw SSLException("Unexpected encrypted Bluetooth lab output size")
                DebugTlsProviderReadiness.requireExactControlResult(
                    result, consumed = frame.size, produced = result.bytesProduced()
                )
                DebugTlsProviderReadiness.requireControlReady(engine)
                encrypted.flip()
                val record = ByteArray(encrypted.remaining())
                try {
                    encrypted.get(record)
                    DebugTlsRecordBoundary.requireSingleCiphertext(record)
                    wireOutput.write(record)
                    wireOutput.flush()
                } finally {
                    record.fill(0)
                }
            } finally {
                encrypted.array().fill(0)
            }
        } catch (failure: Exception) {
            clear()
            throw failure
        }
    }

    fun receive(): ByteArray {
        try {
            ready()
            // A JVM/Android TLS 1.3 provider may surface encrypted post-
            // handshake session tickets before the actual application frame.
            // Accept at most two complete authenticated zero-plaintext records.
            for (skipped in 0..DebugTlsControlTransport.MAX_NO_PLAINTEXT_RECORDS) {
                val record = DebugTlsEngineHandshake.readRecord(wireInput)
                try {
                    DebugTlsRecordBoundary.requireSingleCiphertext(record)
                    // Small expected TEXT frames still need the whole provider
                    // application-buffer capacity to avoid BUFFER_OVERFLOW.
                    val plaintext = ByteBuffer.allocate(engine.session.applicationBufferSize)
                    try {
                        val result = engine.unwrap(ByteBuffer.wrap(record), plaintext)
                        if (result.bytesProduced() == 0) {
                            DebugTlsProviderReadiness.requireEmptyTlsOnlyResult(
                                result, consumed = record.size
                            )
                            DebugTlsProviderReadiness.requireControlReady(engine)
                            if (skipped == DebugTlsControlTransport.MAX_NO_PLAINTEXT_RECORDS)
                                throw SSLException("Too many encrypted TLS-only lab records")
                            continue
                        }
                        if (result.bytesProduced() !in
                            DebugBluetoothTextFrameCodec.HEADER_BYTES..
                                DebugBluetoothTextFrameCodec.MAX_FRAME_BYTES
                        ) throw SSLException("Invalid encrypted Bluetooth application frame size")
                        DebugTlsProviderReadiness.requireExactControlResult(
                            result, consumed = record.size, produced = result.bytesProduced()
                        )
                        DebugTlsProviderReadiness.requireControlReady(engine)
                        plaintext.flip()
                        val frame = ByteArray(plaintext.remaining())
                        try {
                            plaintext.get(frame)
                            // Parse before returning: no invalid or partially
                            // authenticated plaintext is delivered to the ledger.
                            DebugBluetoothTextFrameCodec.decode(frame)
                            return frame
                        } catch (failure: Exception) {
                            frame.fill(0)
                            throw failure
                        }
                    } finally {
                        plaintext.array().fill(0)
                    }
                } finally {
                    record.fill(0)
                }
            }
            throw SSLException("Missing encrypted Bluetooth application frame")
        } catch (failure: Exception) {
            clear()
            throw failure
        }
    }

    fun clear() { closed = true }

    private fun ready() {
        if (closed) throw SSLException("Bluetooth encrypted frame lab already cleared")
        DebugTlsProviderReadiness.requireControlReady(engine)
    }
}
