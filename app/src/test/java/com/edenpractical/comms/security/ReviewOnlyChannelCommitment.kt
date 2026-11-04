package com.edenpractical.comms.security

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest

/**
 * UNREVIEWED DESIGN CANDIDATE IN JVM TEST SOURCES ONLY.
 *
 * This is NOT an RFC-defined application protocol, session proof, KDF, or
 * authorization. The reviewer must approve encoding and threat model before
 * it can ever enter main sources. Synthetic arrays in unit tests are NOT TLS
 * exporter evidence. Does not reference any Android socket or live app path.
 */
internal object ReviewOnlyChannelCommitment {
    private val domain = "EdenComms-Approval-v1".toByteArray(Charsets.US_ASCII)
    private const val FORMAT_VERSION = 1

    fun candidateSha256(
        presenterCertificateSha256: ByteArray,
        joinerCertificateSha256: ByteArray,
        presenterQrNonce: ByteArray,
        joinerQrNonce: ByteArray,
        presenterChallenge: ByteArray,
        joinerChallenge: ByteArray,
        tlsExporterRfc9266: ByteArray
    ): ByteArray {
        val arguments = listOf(
            presenterCertificateSha256 to 32,
            joinerCertificateSha256 to 32,
            presenterQrNonce to 16,
            joinerQrNonce to 16,
            presenterChallenge to 16,
            joinerChallenge to 16,
            tlsExporterRfc9266 to 32
        )
        require(arguments.all { (bytes, size) ->
            bytes.size == size && bytes.any { it != 0.toByte() }
        }) { "Missing or malformed proposed channel evidence" }
        require(!MessageDigest.isEqual(presenterCertificateSha256, joinerCertificateSha256)) {
            "Self pairing is not valid"
        }
        require(!MessageDigest.isEqual(presenterQrNonce, joinerQrNonce)) {
            "QR nonces must be different"
        }
        require(!MessageDigest.isEqual(presenterChallenge, joinerChallenge)) {
            "Fresh proof challenges must be different"
        }

        // Explicit field order from PRESENTER to JOINER, independent of which
        // side computes it. Version, roles and each field length are encoded
        // with 1-byte length prefixes; there are no optional inputs.
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeByte(domain.size)
            out.write(domain)
            out.writeByte(FORMAT_VERSION)
            out.writeByte(ReviewOnlyApprovalFrame.Role.PRESENTER.wire)
            out.writeByte(ReviewOnlyApprovalFrame.Role.JOINER.wire)
            arguments.forEach { (value, _) ->
                out.writeByte(value.size)
                out.write(value)
            }
        }
        val canonical = bytes.toByteArray()
        return try {
            MessageDigest.getInstance("SHA-256").digest(canonical)
        } finally {
            canonical.fill(0)
            bytes.reset()
        }
    }
}
