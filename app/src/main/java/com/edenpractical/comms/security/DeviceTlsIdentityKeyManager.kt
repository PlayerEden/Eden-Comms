package com.edenpractical.comms.security

import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedKeyManager

/**
 * Bridges an Android Keystore-backed local identity to JSSE TLS client AND
 * server authentication without exporting the private key.
 *
 * Exact remote certificate trust is handled separately by the pinned trust
 * manager; presenting an identity does not authenticate a remote device.
 */
internal class DeviceTlsIdentityKeyManager(private val identity: LocalTlsIdentity) :
    X509ExtendedKeyManager() {

    private companion object {
        const val LOCAL_ALIAS = "eden-comms-local-identity"
        fun supportsEc(keyType: String?): Boolean =
            keyType == "EC" || keyType == "ECDSA" || keyType?.startsWith("EC_") == true
    }

    override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? =
        if (supportsEc(keyType)) arrayOf(LOCAL_ALIAS) else null

    override fun chooseClientAlias(
        keyType: Array<out String>?,
        issuers: Array<out Principal>?,
        socket: Socket?
    ): String? = if (keyType?.any(::supportsEc) == true) LOCAL_ALIAS else null

    override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? =
        getClientAliases(keyType, issuers)

    override fun chooseServerAlias(
        keyType: String?,
        issuers: Array<out Principal>?,
        socket: Socket?
    ): String? = if (supportsEc(keyType)) LOCAL_ALIAS else null

    override fun chooseEngineClientAlias(
        keyType: Array<out String>?,
        issuers: Array<out Principal>?,
        engine: SSLEngine?
    ): String? = if (keyType?.any(::supportsEc) == true) LOCAL_ALIAS else null

    override fun chooseEngineServerAlias(
        keyType: String?,
        issuers: Array<out Principal>?,
        engine: SSLEngine?
    ): String? = if (supportsEc(keyType)) LOCAL_ALIAS else null

    override fun getCertificateChain(alias: String?): Array<X509Certificate>? =
        if (alias == LOCAL_ALIAS) arrayOf(identity.certificate) else null

    override fun getPrivateKey(alias: String?): PrivateKey? =
        if (alias == LOCAL_ALIAS) identity.privateKey else null
}
