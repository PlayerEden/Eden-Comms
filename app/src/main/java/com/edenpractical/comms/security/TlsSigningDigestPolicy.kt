package com.edenpractical.comms.security

/**
 * Keystore permission check only. Supporting raw ECDSA does not itself
 * authenticate a peer, establish TLS, or prove a device is secure.
 */
internal object TlsSigningDigestPolicy {
    fun authorizesRawSignature(authorizedDigests: Array<String>): Boolean =
        authorizedDigests.any { it == "NONE" }
}
