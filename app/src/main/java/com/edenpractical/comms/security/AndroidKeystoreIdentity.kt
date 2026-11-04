package com.edenpractical.comms.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.security.GeneralSecurityException
import java.security.KeyPairGenerator
import java.security.KeyFactory
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
        private const val LEGACY_ALIAS = "eden_comms_local_ec_identity_v1"
        private const val CURRENT_ALIAS = "eden_comms_local_ec_identity_v2"
        private const val VALIDITY_MS = 2L * 365L * 24L * 60L * 60L * 1000L
        private const val CLOCK_SKEW_MS = 24L * 60L * 60L * 1000L
    }

    /**
     * Android v1 Keystore entries authorize only SHA-256. Android's TLS
     * provider may request a raw ECDSA signature (DIGEST_NONE), so v2
     * explicitly allows both. Detect, do not silently replace, old keys.
     */
    enum class TlsSigningStatus {
        NOT_CREATED, RAW_ECDSA_AUTHORIZED, LEGACY_SHA256_ONLY, INSPECTION_UNAVAILABLE
    }

    @Synchronized
    fun tlsSigningStatus(): TlsSigningStatus {
        val identity = existing() ?: return TlsSigningStatus.NOT_CREATED
        return try {
            val keyInfo = KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_EC, PROVIDER)
                .getKeySpec(identity.privateKey, KeyInfo::class.java)
            if (TlsSigningDigestPolicy.authorizesRawSignature(keyInfo.digests))
                TlsSigningStatus.RAW_ECDSA_AUTHORIZED
            else TlsSigningStatus.LEGACY_SHA256_ONLY
        } catch (_: Exception) {
            // Unknown is NOT success. Authentication still has to be proven
            // by the pinned TLS handshake.
            TlsSigningStatus.INSPECTION_UNAVAILABLE
        }
    }

    /** Read an existing identity. Never generate or change keys automatically. */
    @Synchronized
    fun existing(): LocalTlsIdentity? {
        val keystore = loadKeystore()
        val alias = when {
            keystore.containsAlias(CURRENT_ALIAS) -> CURRENT_ALIAS
            keystore.containsAlias(LEGACY_ALIAS) -> LEGACY_ALIAS
            else -> return null
        }
        return readIdentity(keystore, alias)
    }

    private fun readIdentity(keystore: KeyStore, alias: String): LocalTlsIdentity {
        val entry = keystore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
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
        // Reject placeholder certificates that aren't genuinely self-signed.
        cert.verify(cert.publicKey)
        return LocalTlsIdentity(cert, entry.privateKey)
    }

    /** No silent replacement, even when a prior identity is incompatible. */
    @Synchronized
    fun createWithConsent(): LocalTlsIdentity {
        existing()?.let { return it }
        createAt(CURRENT_ALIAS)
        return readIdentity(loadKeystore(), CURRENT_ALIAS)
    }

    private fun createAt(alias: String) {
        val now = System.currentTimeMillis()
        val spec = KeyGenParameterSpec.Builder(
            alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            // SHA256 makes the self-signed certificate valid; NONE is typically
            // needed for TLS signature requests. Neither weakens the TLS pin.
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_NONE)
            .setUserAuthenticationRequired(false)
            .setCertificateSubject(X500Principal("CN=Eden Comms Peer"))
            .setCertificateSerialNumber(BigInteger(128, SecureRandom()).setBit(0))
            .setCertificateNotBefore(Date(now - CLOCK_SKEW_MS))
            .setCertificateNotAfter(Date(now + VALIDITY_MS))
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, PROVIDER).apply {
            initialize(spec)
            generateKeyPair()
        }
    }

    /**
     * Only after an explicit user confirmation. Keep the legacy key while
     * v2 is generated and its certificate validated, then delete legacy.
     * A failed v2 generation leaves the old identity in place.
     *
     * Replacing an identity changes its public fingerprint and invalidates
     * former QR scans / any remote trust pins. Each phone must pair again.
     */
    @Synchronized
    fun upgradeLegacyWithConsent(): LocalTlsIdentity {
        val keystore = loadKeystore()
        if (!keystore.containsAlias(LEGACY_ALIAS)) {
            return existing() ?: throw GeneralSecurityException("No legacy identity to upgrade")
        }
        if (keystore.containsAlias(CURRENT_ALIAS)) {
            // A previous interrupted upgrade might have left both aliases.
            val current = readIdentity(keystore, CURRENT_ALIAS)
            keystore.deleteEntry(LEGACY_ALIAS)
            return current
        }
        try {
            createAt(CURRENT_ALIAS)
            val current = readIdentity(loadKeystore(), CURRENT_ALIAS)
            val info = KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_EC, PROVIDER)
                .getKeySpec(current.privateKey, KeyInfo::class.java)
            if (!TlsSigningDigestPolicy.authorizesRawSignature(info.digests)) {
                throw GeneralSecurityException("Replacement key lacks required TLS authorization")
            }
            keystore.deleteEntry(LEGACY_ALIAS)
            return current
        } catch (error: Exception) {
            // Roll back only the newly created v2 alias. Never delete v1
            // on unsuccessful migration.
            try {
                if (keystore.containsAlias(CURRENT_ALIAS))
                    keystore.deleteEntry(CURRENT_ALIAS)
            } catch (_: Exception) { }
            throw error
        }
    }

    /** Explicit reset invalidates previous pairings; never touches a peer phone. */
    @Synchronized
    fun forgetWithConsent() {
        val keystore = loadKeystore()
        if (keystore.containsAlias(CURRENT_ALIAS)) keystore.deleteEntry(CURRENT_ALIAS)
        if (keystore.containsAlias(LEGACY_ALIAS)) keystore.deleteEntry(LEGACY_ALIAS)
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
