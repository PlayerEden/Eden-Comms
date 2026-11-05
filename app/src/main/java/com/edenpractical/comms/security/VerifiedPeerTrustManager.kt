package com.edenpractical.comms.security

import java.net.Socket
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedTrustManager

/**
 * Exact-leaf pin trust policy for a future mutually authenticated TLS channel.
 *
 * This is intentionally NOT a default CA trust manager: our expected peer's
 * complete certificate SHA-256 hash must first come from an out-of-band,
 * physically verified pairing action. Calling this on a hash supplied by the
 * same untrusted network is unsafe and MUST NOT be allowed.
 *
 * This manager is not a complete SSLContext, TLS handshake or pairing flow.
 * Neither network transport nor application messages are enabled by it.
 */
internal class VerifiedPeerTrustManager(expectedCertificateSha256: ByteArray) : X509ExtendedTrustManager() {
    private val pin: ByteArray = expectedCertificateSha256.copyOf().also {
        require(it.size == PinnedTlsPeer.FINGERPRINT_SIZE) { "A full SHA-256 peer pin is required" }
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        verifyExactLeaf(chain, authType)
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        verifyExactLeaf(chain, authType)
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {
        verifyExactLeaf(chain, authType)
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {
        verifyExactLeaf(chain, authType)
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {
        verifyExactLeaf(chain, authType)
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {
        verifyExactLeaf(chain, authType)
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    @Throws(CertificateException::class)
    private fun verifyExactLeaf(chain: Array<out X509Certificate>?, authType: String?) {
        if (authType.isNullOrBlank() || chain == null || chain.size != 1) {
            throw CertificateException("Expected one pinned peer certificate and a valid authentication type")
        }
        val cert = chain[0]
        cert.checkValidity()
        if (!MessageDigest.isEqual(PinnedTlsPeer.fingerprint(cert), pin)) {
            throw CertificateException("Peer certificate fingerprint mismatch")
        }
    }
}
