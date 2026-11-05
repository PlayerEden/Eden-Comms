package com.edenpractical.comms.security

import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.IOException
import java.security.MessageDigest

/**
 * Fixed-size DEBUG-ONLY channel-binding comparison record.
 *
 * RFC 9266 permits disclosure of the tls-exporter channel-binding value.
 * These bytes are not an encryption key or user authorization; comparison
 * tests whether the two sides observe the same already-pinned TLS channel.
 * Never log/persist the value or make it a production credential.
 */
internal object DebugExporterBindingFrame {
    private val MAGIC = byteArrayOf(0x45, 0x43, 0x43, 0x42) // "ECCB"
    private const val VERSION = 1
    private const val PRESENTER = 1
    private const val JOINER = 2
    const val EXPORTER_BYTES = 32
    const val RECORD_BYTES = 4 + 1 + 1 + EXPORTER_BYTES

    fun encode(isPresenter: Boolean, value: ByteArray): ByteArray {
        requireValid(value)
        return ByteArray(RECORD_BYTES).apply {
            MAGIC.copyInto(this, 0)
            this[4] = VERSION.toByte()
            this[5] = (if (isPresenter) PRESENTER else JOINER).toByte()
            value.copyInto(this, 6)
        }
    }

    fun readAndVerify(
        input: InputStream,
        expectedPeerIsPresenter: Boolean,
        expectedLocalValue: ByteArray
    ) {
        requireValid(expectedLocalValue)
        val received = ByteArray(RECORD_BYTES)
        try {
            DataInputStream(input).readFully(received)
            val expectedRole = if (expectedPeerIsPresenter) PRESENTER else JOINER
            if (!received.copyOfRange(0, 4).contentEquals(MAGIC) ||
                received[4].toInt() != VERSION ||
                received[5].toInt() != expectedRole ||
                !MessageDigest.isEqual(
                    received.copyOfRange(6, RECORD_BYTES), expectedLocalValue
                )
            ) throw DebugExporterMismatchException()
        } finally {
            received.fill(0)
        }
    }

    fun exchange(
        input: InputStream,
        output: OutputStream,
        isPresenter: Boolean,
        value: ByteArray
    ) {
        requireValid(value)
        // Only the presenter writes first; fixed role order prevents deadlock.
        if (isPresenter) {
            write(output, true, value)
            readAndVerify(input, false, value)
        } else {
            readAndVerify(input, true, value)
            write(output, false, value)
        }
    }

    private fun write(output: OutputStream, isPresenter: Boolean, value: ByteArray) {
        val record = encode(isPresenter, value)
        try {
            output.write(record)
            output.flush()
        } finally {
            record.fill(0)
        }
    }

    private fun requireValid(value: ByteArray) {
        if (value.size != EXPORTER_BYTES || value.all { it == 0.toByte() }) {
            throw DebugExporterMismatchException()
        }
    }
}

internal class DebugExporterMismatchException :
    IOException("Experimental TLS channel binding did not match")

internal class DebugExporterUnavailableException :
    IOException("Experimental TLS channel binding unavailable")
