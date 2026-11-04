package com.edenpractical.comms.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.security.GeneralSecurityException
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date
import javax.security.auth.x500.X500Principal

/**
 * A minimal, explicitly-created local identity for later mutually authenticated TLS.
 *
 * Android Keystore owns the private key. Neither the private key bytes nor a
 * private-key backup are stored in app files, preferences, or network services.
 *
 * Creating a local identity does NOT establish a connection, verify another
 * person, enable messages or create a saved trusted-peer list.
 */
internal class AndroidKeystoreIdentity {
    companion object {
        private const val PROVIDER = "AndroidKeyStore"
        private const val ALIAS = "eden_comms_local_ec_identity_v1"
        private const val VALIDITY_MS = 2L * 365L * 24L * 60L * 60L * 1000L
        private const val CLOCK_SKEW_MS = 24L * 60L * 60L * 1000L
    }

    /** Load an already chosen identity; does not create one silently. */
    @Synchronized
    fun existing(): LocalTlsIdentity? {
        val keystore = loadKeystore()
        if (!keystore.containsAlias(ALIAS)) return null

        val entry = keystore.getEntry(ALIAS, null) as? KeyStore.PrivateKeyEntry
            ?: throw GeneralSecurityException("Stored identity is not a private-key entry")
        val chain = entry.certificateChain
        if (chain.size != 1) {
            throw GeneralSecurityException("Expected one local identity certificate")
        }
        val cert = chain[0] as? X509Certificate
            ?: throw GeneralSecurityException("Local identity certificate is not X.509")
        if (entry.privateKey.algorithm != KeyProperties.KEY_ALGORITHM_EC) {
            throw GeneralSecurityException("Local identity does not use EC signing")
        }

        cert.checkValidity()
        // Android can emit placeholder signatures for keys without signing
        // authorization. Reject those rather than publishing invalid pins.
        cert.verify(cert.publicKey)
        return LocalTlsIdentity(cert, entry.privateKey)
    }

    /**
     * Only call following an explicit user action. If an entry already exists
     * but is corrupt/expired, refuse instead of silently changing identity.
     */
    @Synchronized
    fun createWithConsent(): LocalTlsIdentity {
        existing()?.let { return it }
        val now = System.currentTimeMillis()
        val serial = BigInteger(128, SecureRandom()).setBit(0)
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(false)
            // No attestation challenge: avoid unnecessary device identity
            // and hardware properties appearing in pairing certificates.
            .setCertificateSubject(X500Principal("CN=Eden Comms Peer"))
            .setCertificateSerialNumber(serial)
            .setCertificateNotBefore(Date(now - CLOCK_SKEW_MS))
            .setCertificateNotAfter(Date(now + VALIDITY_MS))
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, PROVIDER).apply {
            initialize(spec)
            generateKeyPair()
        }
        return existing()
            ?: throw GeneralSecurityException("Android Keystore did not preserve the identity")
    }

    /**
     * Explicitly forgetting the identity invalidates all previous pairings.
     * This does not claim any ability to erase recipient data or old snapshots.
     */
    @Synchronized
    fun forgetWithConsent() {
        val keystore = loadKeystore()
        if (keystore.containsAlias(ALIAS)) keystore.deleteEntry(ALIAS)
    }

    private fun loadKeystore(): KeyStore =
        KeyStore.getInstance(PROVIDER).apply { load(null) }
}

/** Non-exported private-key handle plus the public leaf certificate. */
internal class LocalTlsIdentity(
    val certificate: X509Certificate,
    val privateKey: PrivateKey
) {
    fun publicFingerprint(): ByteArray = PinnedTlsPeer.fingerprint(certificate)
    fun fingerprintDisplay(): String = PinnedTlsPeer.readableFingerprint(publicFingerprint())
    fun keyManager(): DeviceTlsIdentityKeyManager = DeviceTlsIdentityKeyManager(this)
}
