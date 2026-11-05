package com.edenpractical.comms.security

import java.security.MessageDigest
import java.nio.charset.StandardCharsets

/**
 * UNREVIEWED DEBUG-ONLY transcript identifier for the fixed-phrase test.
 *
 * SHA-256 binds the canonical presenter/joiner certificate pins and QR nonces,
 * both fresh per-connection proof challenges and the RFC 9266 exporter from
 * the already-pinned TLS 1.3 socket. The value is public, NOT a MAC,
 * encryption key, production authorization, or standalone human identity.
 * The independently reviewed production approval protocol is still missing.
 */
internal object DebugApprovalCommitment {
    const val BYTES = 32
    private val DOMAIN = "EdenComms-Debug-Consent-v1".toByteArray(StandardCharsets.US_ASCII)
    private val ROLES = byteArrayOf(1, 2)

    fun derive(
        binding: SessionProofBinding,
        challenges: SessionProofChallenges,
        tlsExporter: ByteArray
    ): ByteArray {
        val fields = listOf(
            binding.hostPin to 32,
            binding.joinPin to 32,
            binding.hostNonce to 16,
            binding.joinNonce to 16,
            challenges.hostChallenge to 16,
            challenges.joinChallenge to 16,
            tlsExporter to 32
        )
        if (fields.any { (value, length) ->
                value.size != length || value.all { it == 0.toByte() }
            } ||
            MessageDigest.isEqual(binding.hostPin, binding.joinPin) ||
            MessageDigest.isEqual(binding.hostNonce, binding.joinNonce) ||
            MessageDigest.isEqual(challenges.hostChallenge, challenges.joinChallenge)
        ) throw DebugApprovalCommitmentException()

        val sha = MessageDigest.getInstance("SHA-256")
        sha.update(DOMAIN)
        sha.update(ROLES)
        // Every field is fixed-width and ordered presenter-first on both phones.
        fields.forEach { (value, _) -> sha.update(value) }
        return sha.digest()
    }

    /** The constant-time comparison is a DEBUG session check, not a signature. */
    fun matches(local: ByteArray, peer: ByteArray): Boolean =
        local.size == BYTES && peer.size == BYTES &&
            local.any { it != 0.toByte() } && MessageDigest.isEqual(local, peer)
}

internal class DebugApprovalCommitmentException :
    java.io.IOException("Debug approval session commitment rejected")
