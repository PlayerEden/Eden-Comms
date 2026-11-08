package com.edenpractical.comms.wifidirect

import com.edenpractical.comms.security.SessionProofBinding
import com.edenpractical.comms.security.SessionProofChallenges
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * F3-B2: TWO strict fixed-size encrypted test-control frames on the already
 * mutually pinned TLS 1.3 socket, AFTER SessionProofExchange succeeds.
 *
 * The public SHA-256 digest correlates the *whole* QR metadata transcript to
 * two fresh, ordered in-TLS challenges. This is not an HMAC, TLS exporter,
 * proof of direct proximity, human approval, peer enrollment or permission
 * to send any messages. No credentials, voice or app payload ever travel.
 *
 * Do not use on live Wi-Fi Direct radios without independent protocol review
 * and physical OEM TLS + user-approval QA.
 */
internal object DebugWifiDirectFutureQrCommitment {
    const val BYTES = 4 + 1 + 1 + 32
    private val MAGIC = byteArrayOf(0x45, 0x43, 0x57, 0x43) // ECWC
    private const val VERSION = 1
    private const val HOST = 1
    private const val JOIN = 2
    private val DOMAIN =
        "eden-comms/wifi-direct/encrypted-qr-challenge-proof/v1".toByteArray(
            StandardCharsets.US_ASCII
        )

    fun derive(
        binding: SessionProofBinding,
        challenges: SessionProofChallenges,
        fullQrTranscript: ByteArray
    ): ByteArray {
        require(fullQrTranscript.size == 32 &&
            fullQrTranscript.any { it != 0.toByte() })
        val fields = listOf(
            binding.hostPin to 32,
            binding.joinPin to 32,
            binding.hostNonce to 16,
            binding.joinNonce to 16,
            challenges.hostChallenge to 16,
            challenges.joinChallenge to 16
        )
        require(fields.all { (value, width) ->
            value.size == width && value.any { it != 0.toByte() }
        })
        require(!MessageDigest.isEqual(binding.hostPin, binding.joinPin) &&
            !MessageDigest.isEqual(binding.hostNonce, binding.joinNonce) &&
            !MessageDigest.isEqual(
                challenges.hostChallenge, challenges.joinChallenge
            ))
        val sha = MessageDigest.getInstance("SHA-256")
        sha.update(DOMAIN)
        sha.update(fullQrTranscript)
        fields.forEach { (bytes, _) -> sha.update(bytes) }
        return sha.digest()
    }

    /** Host sends first, joiner reads first: no role reflection/deadlock. */
    fun exchange(
        input: InputStream,
        output: OutputStream,
        isHost: Boolean,
        digest: ByteArray
    ) {
        valid(digest)
        if (isHost) {
            write(output, HOST, digest)
            verify(input, JOIN, digest)
        } else {
            verify(input, HOST, digest)
            write(output, JOIN, digest)
        }
    }

    private fun verify(input: InputStream, expectedRole: Int, digest: ByteArray) {
        val frame = ByteArray(BYTES)
        try {
            DataInputStream(input).readFully(frame)
            if (!MessageDigest.isEqual(frame.copyOfRange(0, 4), MAGIC) ||
                (frame[4].toInt() and 255) != VERSION ||
                (frame[5].toInt() and 255) != expectedRole ||
                !MessageDigest.isEqual(frame.copyOfRange(6, BYTES), digest)
            ) throw IOException("Wi-Fi Direct encrypted QR transcript agreement rejected")
        } finally {
            frame.fill(0)
        }
    }

    private fun write(output: OutputStream, role: Int, digest: ByteArray) {
        val frame = ByteArray(BYTES)
        try {
            MAGIC.copyInto(frame)
            frame[4] = VERSION.toByte()
            frame[5] = role.toByte()
            digest.copyInto(frame, 6)
            output.write(frame)
            output.flush()
        } finally {
            frame.fill(0)
        }
    }

    private fun valid(bytes: ByteArray) {
        require(bytes.size == 32 && bytes.any { it != 0.toByte() })
    }
}
