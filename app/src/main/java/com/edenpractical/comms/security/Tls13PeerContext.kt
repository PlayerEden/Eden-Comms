package com.edenpractical.comms.security

import java.security.SecureRandom
import java.net.Socket
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509KeyManager

/**
 * Prepares TLS-only sockets with an exact out-of-band certificate pin.
 *
 * A local X.509 private-key manager is mandatory (both phones authenticate).
 * Used only by the one-shot opt-in LAN handshake probe. It must not be
 * wired to message/audio IO until peer enrollment, certificate lifecycle
 * and two-sided session confirmation have been reviewed and integrated.
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
