package com.edenpractical.comms.security

import java.security.SecureRandom
import java.net.Socket
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509KeyManager

/**
 * Prepares TLS-only sockets with an exact out-of-band certificate pin.
 *
 * A local X.509 private-key manager is mandatory (both phones authenticate).
 * Used by existing LAN test sockets and by M3n-B1 Bluetooth DEBUG TLS 1.3
 * handshake diagnostics. This must not authorize message/audio IO until
 * peer enrollment, QR session binding, channel agreement and both human
 * approvals have been reviewed and integrated.
 *
 * IMPORTANT: SSLContext.getInstance("TLSv1.3") may support older protocols
 * with certain providers, so explicitly constrain every returned socket.
 */
internal class Tls13PeerContext(localIdentity: X509KeyManager, expectedPeerSha256: ByteArray) {
    private val context: SSLContext = SSLContext.getInstance("TLSv1.3").apply {
        init(
            arrayOf<KeyManager>(localIdentity),
            arrayOf(VerifiedPeerTrustManager(expectedPeerSha256)),
            SecureRandom()
        )
        require(supportedSSLParameters.protocols.contains("TLSv1.3")) {
            "This device/provider cannot negotiate TLS 1.3; refuse to connect"
        }
    }

    /**
     * M3n-B1 DEBUG handshake-only SSLEngine for an arbitrary byte stream
     * (Bluetooth RFCOMM). Uses the SAME exact-pin trust manager and Keystore
     * X509ExtendedKeyManager as the LAN TLS socket.
     *
     * Creating an engine does not authorize sending/receiving application
     * data. The debug RFCOMM probe must separately verify the completed TLS
     * session protocol, exact peer fingerprint and exporter availability.
     */
    fun unconnectedEngine(client: Boolean): SSLEngine =
        context.createSSLEngine().apply {
            useClientMode = client
            require(supportedProtocols.contains("TLSv1.3")) {
                "TLS 1.3 engine unavailable; refuse Bluetooth probe"
            }
            enabledProtocols = arrayOf("TLSv1.3")
            enableSessionCreation = true
            if (!client) needClientAuth = true
        }

    fun unconnectedClientSocket(): SSLSocket =
        (context.socketFactory.createSocket() as SSLSocket).apply {
            useClientMode = true
            require(supportedProtocols.contains("TLSv1.3")) { "TLS 1.3 unavailable" }
            enabledProtocols = arrayOf("TLSv1.3")
        }

    fun wrapConnectedClient(rawSocket: Socket, peerAddress: String, port: Int): SSLSocket =
        (context.socketFactory.createSocket(rawSocket, peerAddress, port, true) as SSLSocket).apply {
            useClientMode = true
            require(supportedProtocols.contains("TLSv1.3")) { "TLS 1.3 unavailable" }
            enabledProtocols = arrayOf("TLSv1.3")
        }

    fun unboundServerSocket(): SSLServerSocket =
        (context.serverSocketFactory.createServerSocket() as SSLServerSocket).apply {
            require(supportedProtocols.contains("TLSv1.3")) { "TLS 1.3 unavailable" }
            enabledProtocols = arrayOf("TLSv1.3")
            needClientAuth = true
        }
}
