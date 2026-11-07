package com.edenpractical.comms.bluetooth

import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/**
 * One fixed 39-byte TLS-ENCRYPTED Bluetooth consent frame.
 * Digest MUST match the fully verified M3n-B2 session commitment. This is
 * neither a contact credential nor a generic plaintext message format.
 */
internal object DebugBluetoothConsentFrame {
    const val RECORD_BYTES = 4 + 1 + 1 + 1 + 32
    private val MAGIC = byteArrayOf(0x45, 0x43, 0x42, 0x41) // ECBA
    private const val VERSION = 1
    private const val HOST = 1
    private const val JOIN = 2
    private const val APPROVE = 1
    private const val DECLINE = 2
    private const val CONFIRM = 3

    enum class Type { APPROVE, DECLINE, CONFIRM }

    fun encode(isHost: Boolean, kind: Type, commitment: ByteArray): ByteArray {
        requireCommitment(commitment)
        return ByteArray(RECORD_BYTES).apply {
            MAGIC.copyInto(this)
            this[4] = VERSION.toByte()
            this[5] = (if (isHost) HOST else JOIN).toByte()
            this[6] = when (kind) {
                Type.APPROVE -> APPROVE
                Type.DECLINE -> DECLINE
                Type.CONFIRM -> CONFIRM
            }.toByte()
            commitment.copyInto(this, 7)
        }
    }

    /** Reject unknown type/role/version/commitment, including stale sessions. */
    fun read(
        input: InputStream,
        expectPeerHost: Boolean,
        localCommitment: ByteArray
    ): Type {
        requireCommitment(localCommitment)
        val bytes = ByteArray(RECORD_BYTES)
        try {
            DataInputStream(input).readFully(bytes)
            if (!MessageDigest.isEqual(bytes.copyOfRange(0, 4), MAGIC) ||
                bytes[4].toInt() != VERSION ||
                bytes[5].toInt() != (if (expectPeerHost) HOST else JOIN) ||
                !MessageDigest.isEqual(
                    bytes.copyOfRange(7, RECORD_BYTES), localCommitment
                )
            ) throw DebugBluetoothConsentFrameException()
            return when (bytes[6].toInt()) {
                APPROVE -> Type.APPROVE
                DECLINE -> Type.DECLINE
                CONFIRM -> Type.CONFIRM
                else -> throw DebugBluetoothConsentFrameException()
            }
        } finally {
            bytes.fill(0)
        }
    }

    fun write(
        output: OutputStream,
        isHost: Boolean,
        kind: Type,
        commitment: ByteArray
    ) {
        val bytes = encode(isHost, kind, commitment)
        try {
            output.write(bytes)
            output.flush()
        } finally {
            bytes.fill(0)
        }
    }

    private fun requireCommitment(digest: ByteArray) {
        if (digest.size != 32 || digest.all { it == 0.toByte() })
            throw DebugBluetoothConsentFrameException()
    }
}

internal class DebugBluetoothConsentFrameException :
    IOException("Bluetooth consent did not match this authenticated session")
