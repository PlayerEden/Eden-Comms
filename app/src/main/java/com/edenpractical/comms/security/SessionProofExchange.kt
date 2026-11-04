package com.edenpractical.comms.security

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Additional TEST-ONLY session binding within an already mutually pinned TLS 1.3 socket.
 *
 * This is NOT new cryptography and does NOT authorize chat or voice. The peer
 * certificates MUST have been verified before calling these functions.
 * Both parties prove agreement on the two face-to-face QR identities/nonces
 * and exchange new per-connection challenges over that authenticated channel.
 *
 * No contact data, messages, remote endpoints or private key bytes are encoded.
 */
internal class SessionProofBinding(
    hostCertificateFingerprint: ByteArray,
    joinCertificateFingerprint: ByteArray,
    hostQrNonce: ByteArray,
    joinQrNonce: ByteArray
) {
    internal val hostPin = hostCertificateFingerprint.copyOf()
    internal val joinPin = joinCertificateFingerprint.copyOf()
    internal val hostNonce = hostQrNonce.copyOf()
    internal val joinNonce = joinQrNonce.copyOf()

    init {
        require(hostPin.size == 32 && joinPin.size == 32) { "Full certificate pins required" }
        require(hostNonce.size == 16 && joinNonce.size == 16) { "Fresh QR nonces required" }
        require(!MessageDigest.isEqual(hostPin, joinPin)) { "Cannot pair a phone with itself" }
    }
}

internal class SessionProofException : IOException("QR session binding rejected")

internal object SessionProofExchange {
    private val MAGIC = byteArrayOf(0x45, 0x43, 0x53, 0x50) // ECSP
    private const val VERSION = 1
    private const val HOST_HELLO = 1
    private const val JOIN_HELLO = 2
    private const val HOST_ACK = 3
    private const val JOIN_ACK = 4
    private const val CHALLENGE_BYTES = 16
    private const val RECORD_BYTES = 4 + 1 + 1 + 32 + 32 + 16 + 16 + 16 + 16
    private val ZERO = ByteArray(CHALLENGE_BYTES)

    private data class Record(val challenge: ByteArray, val echo: ByteArray)

    /**
     * Host first, joiner second. A fixed-size four-record exchange avoids
     * untrusted allocations; it is strictly test-only application control data.
     */
    fun runHost(
        input: InputStream,
        output: OutputStream,
        binding: SessionProofBinding,
        random: SecureRandom = SecureRandom()
    ) {
        val hostChallenge = freshChallenge(random)
        write(output, HOST_HELLO, binding, hostChallenge, ZERO)
        val reply = read(input, JOIN_HELLO, binding)
        requireMatch(reply.echo, hostChallenge)
        requireNonzero(reply.challenge)
        write(output, HOST_ACK, binding, ZERO, reply.challenge)
        val last = read(input, JOIN_ACK, binding)
        requireMatch(last.echo, hostChallenge)
        requireMatch(last.challenge, ZERO)
    }

    fun runJoin(
        input: InputStream,
        output: OutputStream,
        binding: SessionProofBinding,
        random: SecureRandom = SecureRandom()
    ) {
        val hello = read(input, HOST_HELLO, binding)
        requireMatch(hello.echo, ZERO)
        requireNonzero(hello.challenge)
        val joinChallenge = freshChallenge(random)
        write(output, JOIN_HELLO, binding, joinChallenge, hello.challenge)
        val ack = read(input, HOST_ACK, binding)
        requireMatch(ack.challenge, ZERO)
        requireMatch(ack.echo, joinChallenge)
        write(output, JOIN_ACK, binding, ZERO, hello.challenge)
    }

    private fun freshChallenge(random: SecureRandom): ByteArray {
        val bytes = ByteArray(CHALLENGE_BYTES)
        do { random.nextBytes(bytes) } while (MessageDigest.isEqual(bytes, ZERO))
        return bytes
    }

    private fun write(
        output: OutputStream,
        type: Int,
        binding: SessionProofBinding,
        challenge: ByteArray,
        echo: ByteArray
    ) {
        val encoded = ByteArrayOutputStream(RECORD_BYTES)
        DataOutputStream(encoded).use { writer ->
            writer.write(MAGIC)
            writer.writeByte(VERSION)
            writer.writeByte(type)
            writer.write(binding.hostPin)
            writer.write(binding.joinPin)
            writer.write(binding.hostNonce)
            writer.write(binding.joinNonce)
            writer.write(challenge)
            writer.write(echo)
        }
        output.write(encoded.toByteArray())
        output.flush()
    }

    private fun read(
        input: InputStream,
        expectedType: Int,
        binding: SessionProofBinding
    ): Record {
        val raw = ByteArray(RECORD_BYTES)
        DataInputStream(input).readFully(raw)
        val data = DataInputStream(ByteArrayInputStream(raw))
        val magic = ByteArray(MAGIC.size).also(data::readFully)
        if (!MessageDigest.isEqual(magic, MAGIC) ||
            data.readUnsignedByte() != VERSION ||
            data.readUnsignedByte() != expectedType
        ) throw SessionProofException()
        requireMatch(ByteArray(32).also(data::readFully), binding.hostPin)
        requireMatch(ByteArray(32).also(data::readFully), binding.joinPin)
        requireMatch(ByteArray(16).also(data::readFully), binding.hostNonce)
        requireMatch(ByteArray(16).also(data::readFully), binding.joinNonce)
        val challenge = ByteArray(CHALLENGE_BYTES).also(data::readFully)
        val echo = ByteArray(CHALLENGE_BYTES).also(data::readFully)
        return Record(challenge, echo)
    }

    private fun requireNonzero(bytes: ByteArray) {
        if (MessageDigest.isEqual(bytes, ZERO)) throw SessionProofException()
    }

    private fun requireMatch(actual: ByteArray, expected: ByteArray) {
        if (!MessageDigest.isEqual(actual, expected)) throw SessionProofException()
    }
}
