package com.edenpractical.comms.bluetooth

import com.edenpractical.comms.security.DebugExporterBindingFrame
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult
import javax.net.ssl.SSLException

/**
 * DEBUG ONLY: precisely bounded TLS-encrypted *control records*, never a
 * general plaintext stream or user-message transport.
 *
 * Each protocol call is one fixed-size frame encrypted into one TLS record.
 * The underlying RFCOMM socket owns cancellation; closing it interrupts read.
 * Unsupported TLS provider fragmentation/renegotiation fails closed rather
 * than silently delivering unauthenticated or partially decrypted bytes.
 */
internal class DebugTlsControlTransport(
    private val engine: SSLEngine,
    private val wireInput: InputStream,
    private val wireOutput: OutputStream
) {
    companion object {
        const val PROOF_BYTES = 4 + 1 + 1 + 32 + 32 + 16 + 16 + 16 + 16
        const val COMMIT_BYTES = 4 + 1 + 1 + 32
        // F2-D: JSSE TLS 1.3 may deliver encrypted session-ticket records
        // after the handshake, before our first protocol control. Skip ONLY
        // fully authenticated records yielding zero application bytes.
        // Extra TLS-only records fail closed; never skip partial user/control.
        const val MAX_NO_PLAINTEXT_RECORDS = 2
    }

    enum class Phase(val bytes: Int) {
        QR_PROOF(PROOF_BYTES),
        EXPORTER(DebugExporterBindingFrame.RECORD_BYTES),
        COMMITMENT(COMMIT_BYTES),
        APPROVAL(DebugBluetoothConsentFrame.RECORD_BYTES),
        CONFIRM(DebugBluetoothConsentFrame.RECORD_BYTES)
    }

    private var phase = Phase.QR_PROOF
    private var closed = false

    val input: InputStream = object : InputStream() {
        override fun read(): Int =
            throw SSLException("Single-byte Bluetooth TLS control reads are prohibited")

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            validateBounds(b.size, off, len)
            requireReady(len)
            // Exactly one application control is required. A TLS 1.3
            // provider can emit one or two encrypted no-plaintext records
            // (e.g. NewSessionTicket) after its initial handshake. They are
            // not control frames and never enter the application buffer.
            for (skipped in 0..MAX_NO_PLAINTEXT_RECORDS) {
                val encoded = DebugTlsEngineHandshake.readRecord(wireInput)
                try {
                    DebugTlsRecordBoundary.requireSingleCiphertext(encoded)
                    val clear = ByteBuffer.allocate(phase.bytes)
                    try {
                        val result = engine.unwrap(ByteBuffer.wrap(encoded), clear)
                        if (result.bytesProduced() == 0) {
                            DebugTlsProviderReadiness.requireExactControlResult(
                                result, consumed = encoded.size, produced = 0
                            )
                            DebugTlsProviderReadiness.requireControlReady(engine)
                            if (skipped == MAX_NO_PLAINTEXT_RECORDS)
                                throw SSLException("Too many TLS-only control-prefix records")
                            continue
                        }
                        DebugTlsProviderReadiness.requireExactControlResult(
                            result, consumed = encoded.size, produced = phase.bytes
                        )
                        DebugTlsProviderReadiness.requireControlReady(engine)
                        clear.flip()
                        clear.get(b, off, len)
                        return len
                    } finally {
                        // Even malformed or partially produced plaintext is cleared.
                        clear.array().fill(0)
                    }
                } finally {
                    encoded.fill(0)
                }
            }
            throw SSLException("Missing Bluetooth TLS control frame")
        }
    }

    val output: OutputStream = object : OutputStream() {
        override fun write(b: Int) {
            throw SSLException("Single-byte Bluetooth TLS control writes are prohibited")
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            validateBounds(b.size, off, len)
            requireReady(len)
            val encrypted = ByteBuffer.allocate(engine.session.packetBufferSize)
            try {
                val result = engine.wrap(ByteBuffer.wrap(b, off, len), encrypted)
                if (result.bytesProduced() !in 1..DebugTlsEngineHandshake.MAX_ENGINE_PACKET)
                    throw SSLException("Unexpected TLS control encryption length")
                DebugTlsProviderReadiness.requireExactControlResult(
                    result, consumed = len, produced = result.bytesProduced()
                )
                DebugTlsProviderReadiness.requireControlReady(engine)
                encrypted.flip()
                val record = ByteArray(encrypted.remaining())
                try {
                    encrypted.get(record)
                    // Never put an unrecognized or multi-record provider result
                    // onto the RFCOMM wire as a supposedly atomic control.
                    DebugTlsRecordBoundary.requireSingleCiphertext(record)
                    wireOutput.write(record)
                    wireOutput.flush()
                } finally {
                    record.fill(0)
                }
            } finally {
                // Also clear buffers when wrap/validation/write throws early.
                encrypted.array().fill(0)
            }
        }

        override fun flush() { wireOutput.flush() }
    }

    /**
     * The only permitted protocol transition:
     * four QR challenge records -> two RFC9266 exporter frames ->
     * two commitment echoes -> separate human APPROVE/DECLINE records ->
     * two bilateral CONFIRM frames. All encrypted in exactly this TLS link.
     */
    fun advance(expected: Phase, next: Phase) {
        val allowed = (expected == Phase.QR_PROOF && next == Phase.EXPORTER) ||
            (expected == Phase.EXPORTER && next == Phase.COMMITMENT) ||
            (expected == Phase.COMMITMENT && next == Phase.APPROVAL) ||
            (expected == Phase.APPROVAL && next == Phase.CONFIRM)
        if (closed || phase != expected || !allowed) {
            throw SSLException("Invalid Bluetooth TLS control phase transition")
        }
        phase = next
    }

    fun clear() { closed = true }

    private fun requireReady(length: Int) {
        if (closed || length != phase.bytes)
            throw SSLException("Bluetooth TLS control unavailable or wrong frame length")
        DebugTlsProviderReadiness.requireControlReady(engine)
    }

    private fun validateBounds(size: Int, offset: Int, length: Int) {
        if (offset < 0 || length < 0 || offset > size || length > size - offset)
            throw IndexOutOfBoundsException("Invalid control frame bounds")
    }
}
