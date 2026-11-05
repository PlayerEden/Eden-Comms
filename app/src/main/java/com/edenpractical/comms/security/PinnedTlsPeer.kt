package com.edenpractical.comms.security

import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSession

/**
 * One verification primitive for a *future* TLS 1.3 authenticated channel.
 *
 * NO network path is connected to this code. It neither establishes TLS nor
 * enrolls a trusted peer. A SHA-256 certificate fingerprint becomes a trust
 * anchor ONLY when its full value was independently authenticated out of band
 * (for example via a properly validated QR pairing flow).
 *
 * Do not use this helper as a substitute for a correctly configured
 * X509TrustManager / TLS context, certificate-chain validation, or a complete
 * mutual-authentication and session-binding protocol. Do not create a
 * trust-all SSLContext. Application frames must remain disabled until all
 * security gates pass.
 */
internal object PinnedTlsPeer {
    const val FINGERPRINT_SIZE: Int = 32
    private const val MAX_CERTIFICATE_BYTES: Int = 8192

    /**
     * Parse an X.509 DER certificate using the platform provider.
     * Size-limited before parsing. No private key enters this function.
     */
    fun certificateFromDer(der: ByteArray): X509Certificate {
        if (der.isEmpty() || der.size > MAX_CERTIFICATE_BYTES) {
            throw CertificateException("Certificate length outside allowed bounds")
        }
        val source = ByteArrayInputStream(der)
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(source) as? X509Certificate
            ?: throw CertificateException("Expected X.509 certificate")
        if (source.available() != 0) {
            throw CertificateException("Trailing bytes after certificate")
        }
        return certificate
    }

    /**
     * Full SHA-256 of the DER-encoded peer leaf certificate.
     * A digest is not a verification code until the user checks it against
     * an authenticated, separate channel (not the network under attack).
     */
    fun fingerprint(certificate: X509Certificate): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(certificate.encoded)

    /**
     * Reject weak protocol versions, absent/invalid certificates and pin
     * mismatches. Caller must stop the connection BEFORE sending application
     * data, and close the socket on failure.
     */
    @Throws(SSLPeerUnverifiedException::class)
    fun requirePinnedTls13(session: SSLSession, expectedFingerprint: ByteArray): X509Certificate {
        if (session.protocol != "TLSv1.3") {
            throw SSLPeerUnverifiedException("TLS 1.3 required; no downgrade allowed")
        }
        if (expectedFingerprint.size != FINGERPRINT_SIZE) {
            throw SSLPeerUnverifiedException("A 32-byte verified peer pin is required")
        }
        val leaf = session.peerCertificates.firstOrNull() as? X509Certificate
            ?: throw SSLPeerUnverifiedException("Missing X.509 peer certificate")
        try {
            leaf.checkValidity()
            if (!MessageDigest.isEqual(fingerprint(leaf), expectedFingerprint)) {
                throw SSLPeerUnverifiedException("Peer certificate does not match verified pin")
            }
        } catch (error: CertificateException) {
            val rejection = SSLPeerUnverifiedException("Peer certificate is invalid")
            rejection.initCause(error)
            throw rejection
        }
        return leaf
    }

    /**
     * Full 256-bit fingerprint, grouped for a future advanced verification
     * screen. A shorter human-readable SAS requires separate protocol review.
     */
    fun readableFingerprint(fingerprint: ByteArray): String {
        require(fingerprint.size == FINGERPRINT_SIZE) { "Expected full SHA-256 fingerprint" }
        return fingerprint.joinToString("") { "%02X".format(it.toInt() and 0xff) }
            .chunked(8)
            .joinToString(" ")
    }
}
