package com.edenpractical.comms.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TlsSigningDigestPolicyTest {
    @Test fun legacySha256OnlyCannotAuthorizeRawTlsSigning() {
        assertFalse(TlsSigningDigestPolicy.authorizesRawSignature(arrayOf("SHA-256")))
    }

    @Test fun newlyCreatedDualDigestKeyCanAuthorizeRawTlsSigning() {
        assertTrue(TlsSigningDigestPolicy.authorizesRawSignature(arrayOf("SHA-256", "NONE")))
        assertTrue(TlsSigningDigestPolicy.authorizesRawSignature(arrayOf("NONE", "SHA-256")))
    }

    @Test fun unknownOrEmptyDigestListsAreNotTreatedAsAuthorized() {
        assertFalse(TlsSigningDigestPolicy.authorizesRawSignature(emptyArray()))
        assertFalse(TlsSigningDigestPolicy.authorizesRawSignature(arrayOf("SHA-512")))
        assertFalse(TlsSigningDigestPolicy.authorizesRawSignature(arrayOf("none")))
    }
}
