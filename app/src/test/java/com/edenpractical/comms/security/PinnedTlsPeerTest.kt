package com.edenpractical.comms.security

import java.lang.reflect.Proxy
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSession
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PinnedTlsPeerTest {
    // Public, throwaway self-signed test certificate generated solely for tests.
    // No private key is included or required.
    private val der: ByteArray = Base64.getDecoder().decode(
        "MIIBkzCCATmgAwIBAgIUSog4sSc3ZvFb5H5fBYdHCq6YOyQwCgYIKoZIzj0EAwIw" +
            "HzEdMBsGA1UEAwwURWRlbiBDb21tcyBUZXN0IFBlZXIwHhcNMjYxMTA0MDQxNzIw" +
            "WhcNMzYxMTAxMDQxNzIwWjAfMR0wGwYDVQQDDBRFZGVuIENvbW1zIFRlc3QgUGVl" +
            "cjBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABBVcG2W5gifxOzzALwsZmQJ1T/Z0" +
            "XjCnkUJcyag61few16U9YXohhwYOtx79dBwSiofD++aH56sBH3rzWgLUFLOjUz" +
            "BRMB0GA1UdDgQWBBTqK1tcuHN5NietcG//kDjMlheR6DAfBgNVHSMEGDAWgBTq" +
            "K1tcuHN5NietcG//kDjMlheR6DAPBgNVHRMBAf8EBTADAQH/MAoGCCqGSM49BAMC" +
            "A0gAMEUCIQDGL09P9CJp5bwwUrkmltNg0stN7ZCgyf1eWFYJjtkHLQIgIt7QO4" +
            "v2R+QcQB/D2tZK8nzSBkNdtEt8w0y9QnBIrNg="
    )

    private val cert: X509Certificate get() = PinnedTlsPeer.certificateFromDer(der)

    @Test fun publicCertificateDigestMatchesKnownSha256() {
        val expected = "DDA5976EDA6FF06EAB3E4DBBFAAD3188687CC9E19DFE1BEB92C38EB1900A4FD9"
        val full = PinnedTlsPeer.readableFingerprint(PinnedTlsPeer.fingerprint(cert))
            .replace(" ", "")
        assertEquals(expected, full)
        assertEquals(8, PinnedTlsPeer.readableFingerprint(PinnedTlsPeer.fingerprint(cert))
            .split(" ").size)
    }

    @Test fun sameCertWithFullPinAndTls13Passes() {
        val pin = PinnedTlsPeer.fingerprint(cert)
        val verified = PinnedTlsPeer.requirePinnedTls13(fakeSession("TLSv1.3", cert), pin)
        assertArrayEquals(cert.encoded, verified.encoded)
    }

    @Test fun tls12IsRejectedEvenWhenPinMatches() {
        assertThrows(SSLPeerUnverifiedException::class.java) {
            PinnedTlsPeer.requirePinnedTls13(fakeSession("TLSv1.2", cert), PinnedTlsPeer.fingerprint(cert))
        }
    }

    @Test fun unpinnedPeerIsRejected() {
        assertThrows(SSLPeerUnverifiedException::class.java) {
            PinnedTlsPeer.requirePinnedTls13(fakeSession("TLSv1.3", cert), ByteArray(0))
        }
    }

    @Test fun wrongPinIsRejected() {
        val pin = PinnedTlsPeer.fingerprint(cert)
        pin[0] = (pin[0].toInt() xor 0x01).toByte()
        assertThrows(SSLPeerUnverifiedException::class.java) {
            PinnedTlsPeer.requirePinnedTls13(fakeSession("TLSv1.3", cert), pin)
        }
    }

    @Test fun emptyCertChainIsRejected() {
        assertThrows(SSLPeerUnverifiedException::class.java) {
            PinnedTlsPeer.requirePinnedTls13(fakeSession("TLSv1.3", null), PinnedTlsPeer.fingerprint(cert))
        }
    }

    @Test fun malformedCertificateRejected() {
        assertThrows(CertificateException::class.java) {
            PinnedTlsPeer.certificateFromDer(byteArrayOf(1, 2, 3, 4))
        }
    }

    @Test fun hugeCertificateRejectedBeforeParsing() {
        assertThrows(CertificateException::class.java) {
            PinnedTlsPeer.certificateFromDer(ByteArray(8193))
        }
    }

    @Test fun trailingGarbageRejected() {
        val extra = der + byteArrayOf(1, 2, 3)
        assertThrows(CertificateException::class.java) {
            PinnedTlsPeer.certificateFromDer(extra)
        }
    }

    @Test fun readableFingerprintRequiresFullHash() {
        assertThrows(IllegalArgumentException::class.java) {
            PinnedTlsPeer.readableFingerprint(ByteArray(8))
        }
    }

    private fun fakeSession(protocol: String, peer: X509Certificate?): SSLSession {
        return Proxy.newProxyInstance(
            SSLSession::class.java.classLoader,
            arrayOf(SSLSession::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getProtocol" -> protocol
                "getPeerCertificates" -> peer?.let { arrayOf<Certificate>(it) }
                    ?: emptyArray<Certificate>()
                else -> throw UnsupportedOperationException("Not needed in this unit test")
            }
        } as SSLSession
    }
}
