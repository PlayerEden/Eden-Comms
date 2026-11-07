package com.edenpractical.comms.bluetooth

import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/**
 * Two final, role-ordered equality checks of the DEBUG-only transcript
 * commitment. Frames travel ONLY in the already pinned TLS 1.3 control
 * transport. They are not user approvals, signatures or trust enrollment.
 */
internal object DebugBluetoothCommitmentFrame {
    const val RECORD_BYTES = 4 + 1 + 1 + 32
    private val MAGIC = byteArrayOf(0x45, 0x43, 0x42, 0x43) // ECBC
    private const val VERSION = 1
    private const val HOST = 1
    private const val JOIN = 2

    fun encode(isHost: Boolean, commitment: ByteArray): ByteArray {
        requireValid(commitment)
        return ByteArray(RECORD_BYTES).apply {
            MAGIC.copyInto(this)
            this[4] = VERSION.toByte()
            this[5] = (if (isHost) HOST else JOIN).toByte()
            commitment.copyInto(this, 6)
        }
    }

    fun verify(input: InputStream, expectedPeerIsHost: Boolean, local: ByteArray) {
        requireValid(local)
        val incoming = ByteArray(RECORD_BYTES)
        try {
            DataInputStream(input).readFully(incoming)
            val expectedRole = if (expectedPeerIsHost) HOST else JOIN
            if (!MessageDigest.isEqual(incoming.copyOfRange(0, 4), MAGIC) ||
                incoming[4].toInt() != VERSION ||
                incoming[5].toInt() != expectedRole ||
                !MessageDigest.isEqual(incoming.copyOfRange(6, RECORD_BYTES), local)
            ) throw DebugBluetoothCommitmentMismatch()
        } finally {
            incoming.fill(0)
        }
    }

    fun exchange(input: InputStream, output: OutputStream, isHost: Boolean, commitment: ByteArray) {
        requireValid(commitment)
        if (isHost) {
            write(output, true, commitment)
            verify(input, false, commitment)
        } else {
            verify(input, true, commitment)
            write(output, false, commitment)
        }
    }

    private fun write(output: OutputStream, host: Boolean, digest: ByteArray) {
        val bytes = encode(host, digest)
        try {
            output.write(bytes)
            output.flush()
        } finally {
            bytes.fill(0)
        }
    }

    private fun requireValid(value: ByteArray) {
        if (value.size != 32 || value.all { it == 0.toByte() })
            throw DebugBluetoothCommitmentMismatch()
    }
}

internal class DebugBluetoothCommitmentMismatch :
    java.io.IOException("Bluetooth session transcript agreement rejected")
