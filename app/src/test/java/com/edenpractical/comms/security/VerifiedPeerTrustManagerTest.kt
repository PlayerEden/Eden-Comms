package com.edenpractical.comms.security

import java.net.Socket
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl.SSLEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class VerifiedPeerTrustManagerTest {
    // Public X.509 fixture only. This test does not contain a private key.
    private val cert: X509Certificate by lazy {
        PinnedTlsPeer.certificateFromDer(Base64.getDecoder().decode(
            "MIIBkzCCATmgAwIBAgIUSog4sSc3ZvFb5H5fBYdHCq6YOyQwCgYIKoZIzj0EAwIw" +
                "HzEdMBsGA1UEAwwURWRlbiBDb21tcyBUZXN0IFBlZXIwHhcNMjYxMTA0MDQxNzIw" +
                "WhcNMzYxMTAxMDQxNzIwWjAfMR0wGwYDVQQDDBRFZGVuIENvbW1zIFRlc3QgUGVl" +
                "cjBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABBVcG2W5gifxOzzALwsZmQJ1T/Z0" +
                "XjCnkUJcyag61few16U9YXohhwYOtx79dBwSiofD++aH56sBH3rzWgLUFLOjUz" +
                "BRMB0GA1UdDgQWBBTqK1tcuHN5NietcG//kDjMlheR6DAfBgNVHSMEGDAWgBTq" +
                "K1tcuHN5NietcG//kDjMlheR6DAPBgNVHRMBAf8EBTADAQH/MAoGCCqGSM49BAMC" +
                "A0gAMEUCIQDGL09P9CJp5bwwUrkmltNg0stN7ZCgyf1eWFYJjtkHLQIgIt7QO4" +
                "v2R+QcQB/D2tZK8nzSBkNdtEt8w0y9QnBIrNg="
        ))
    }

    private fun manager(pin: ByteArray = PinnedTlsPeer.fingerprint(cert)) = VerifiedPeerTrustManager(pin)

    @Test fun samePinnedLeafAcceptedForBothMutualTlsRoles() {
        val tm = manager()
        tm.checkServerTrusted(arrayOf(cert), "EC")
        tm.checkClientTrusted(arrayOf(cert), "EC")
        tm.checkServerTrusted(arrayOf(cert), "EC", null as Socket?)
        tm.checkClientTrusted(arrayOf(cert), "EC", null as Socket?)
        tm.checkServerTrusted(arrayOf(cert), "EC", null as SSLEngine?)
        tm.checkClientTrusted(arrayOf(cert), "EC", null as SSLEngine?)
        assertEquals(0, tm.acceptedIssuers.size)
    }

    @Test fun trustManagerCannotBeInstantiatedWithoutFullPin() {
        assertThrows(IllegalArgumentException::class.java) { VerifiedPeerTrustManager(ByteArray(0)) }
        assertThrows(IllegalArgumentException::class.java) { VerifiedPeerTrustManager(ByteArray(16)) }
    }

    @Test fun wrongFullFingerprintNeverAccepted() {
        val incorrect = PinnedTlsPeer.fingerprint(cert)
        incorrect[0] = (incorrect[0].toInt() xor 0x80).toByte()
        assertThrows(CertificateException::class.java) { manager(incorrect).checkServerTrusted(arrayOf(cert), "EC") }
        assertThrows(CertificateException::class.java) { manager(incorrect).checkClientTrusted(arrayOf(cert), "EC") }
    }

    @Test fun missingOrAmbiguousPeerCertificateIsRejected() {
        val tm = manager()
        assertThrows(CertificateException::class.java) { tm.checkServerTrusted(null, "EC") }
        assertThrows(CertificateException::class.java) { tm.checkServerTrusted(emptyArray(), "EC") }
        assertThrows(CertificateException::class.java) { tm.checkServerTrusted(arrayOf(cert, cert), "EC") }
    }

    @Test fun invalidAuthenticationTypeIsRejected() {
        val tm = manager()
        assertThrows(CertificateException::class.java) { tm.checkServerTrusted(arrayOf(cert), null) }
        assertThrows(CertificateException::class.java) { tm.checkServerTrusted(arrayOf(cert), "") }
    }

    @Test fun pinCopiedSoCallerCannotAlterTrustAfterEnrollment() {
        val mutablePin = PinnedTlsPeer.fingerprint(cert)
        val tm = manager(mutablePin)
        mutablePin.fill(0)
        tm.checkServerTrusted(arrayOf(cert), "EC")
    }
}
