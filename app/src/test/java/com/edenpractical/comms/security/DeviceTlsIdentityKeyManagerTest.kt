package com.edenpractical.comms.security

import java.net.Socket
import java.security.KeyPairGenerator
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl.SSLEngine
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the JSSE key-manager adapter. Actual AndroidKeyStore identity
 * generation/attestation requires on-device instrumentation and is NOT covered
 * by these tests.
 */
class DeviceTlsIdentityKeyManagerTest {
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

    // Only the adapter's certificate/key routing is tested here, not a
    // successful TLS handshake. This throwaway JVM key does not match cert.
    private val local by lazy {
        LocalTlsIdentity(cert, KeyPairGenerator.getInstance("EC").apply {
            initialize(256)
        }.generateKeyPair().private)
    }

    @Test fun exposesPublicFingerprintWithoutPrivateKeySerialization() {
        val fp = local.publicFingerprint()
        assertEquals(32, fp.size)
        assertEquals(8, local.fingerprintDisplay().split(" ").size)
        assertArrayEquals(PinnedTlsPeer.fingerprint(cert), fp)
    }

    @Test fun allowsEcAliasForBothTlsRoles() {
        val keyManager = local.keyManager()
        assertEquals(1, keyManager.getClientAliases("EC", null)!!.size)
        assertEquals(1, keyManager.getServerAliases("EC", null)!!.size)
        assertNotNull(keyManager.chooseClientAlias(arrayOf("RSA", "EC"), null, null as Socket?))
        assertNotNull(keyManager.chooseServerAlias("EC", null, null as Socket?))
        assertNotNull(keyManager.chooseEngineClientAlias(arrayOf("EC"), null, null as SSLEngine?))
        assertNotNull(keyManager.chooseEngineServerAlias("EC", null, null as SSLEngine?))
    }

    @Test fun rejectsNonEcAliases() {
        val keyManager = local.keyManager()
        assertNull(keyManager.getClientAliases("RSA", null))
        assertNull(keyManager.getServerAliases("RSA", null))
        assertNull(keyManager.chooseClientAlias(arrayOf("RSA"), null, null as Socket?))
        assertNull(keyManager.chooseServerAlias("RSA", null, null as Socket?))
        assertNull(keyManager.chooseEngineClientAlias(arrayOf("RSA"), null, null as SSLEngine?))
        assertNull(keyManager.chooseEngineServerAlias("RSA", null, null as SSLEngine?))
    }

    @Test fun returnsOnlyTheConfiguredCertificateAndPrivateKey() {
        val keyManager = local.keyManager()
        val alias = keyManager.chooseServerAlias("EC", null, null as Socket?)
        assertNotNull(alias)
        assertArrayEquals(cert.encoded, keyManager.getCertificateChain(alias)!!.single().encoded)
        assertTrue(keyManager.getPrivateKey(alias) === local.privateKey)
        assertNull(keyManager.getCertificateChain("unrelated-device"))
        assertNull(keyManager.getPrivateKey("unrelated-device"))
    }

    @Test fun identityFingerprintDoesNotContainUserOrNetworkMetadata() {
        val printable = local.fingerprintDisplay()
        assertFalse(printable.contains("."))
        assertFalse(printable.contains("@"))
        assertEquals(71, printable.length) // 64 hex + seven spaces
    }
}
