package com.edenpractical.comms.wifidirect

import com.edenpractical.comms.security.PinnedTlsPeer
import com.edenpractical.comms.security.Tls13PeerContext
import java.net.InetAddress
import java.net.InetSocketAddress
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509KeyManager

/**
 * F3-B1: UNWIRED, DEBUG-ONLY real TCP + mutual TLS 1.3 identity test on
 * JVM LOOPBACK ONLY. This is not an on-device Wi-Fi Direct connection, a
 * usable session or a radio identity proof. It transmits TLS handshake
 * records but NO user/app data and never exposes sockets to caller.
 *
 * Every real Wi-Fi Direct group/link activity continues using the F3-A
 * zero-data TCP probe. Do not connect this lab to them without external
 * protocol review, two-Android-device QA and a separately approved
 * same-connection bilateral-consent/session owner.
 */
internal object DebugWifiDirectFutureTlsSocketLab {
    const val ACCEPT_TIMEOUT_MS = 4_000
    const val CONNECT_TIMEOUT_MS = 2_000
    const val HANDSHAKE_TIMEOUT_MS = 3_000

    private fun requireLoopback(address: InetAddress, port: Int) {
        require(address.isLoopbackAddress && address.address.size == 4) {
            "F3-B1 TLS lab accepts only JVM IPv4 loopback"
        }
        require(port in 1_024..65_535)
    }

    /**
     * Host waits for exactly one TLS connection with mandatory client auth.
     * Uses an exact expected peer certificate from fresh QR metadata.
     * The listener/client TLS streams CLOSE before returning.
     */
    fun hostOnce(
        address: InetAddress,
        port: Int,
        identity: X509KeyManager,
        expectation: DebugWifiDirectFutureQrTlsExpectation,
        onListening: () -> Unit = {}
    ) {
        require(expectation.role == DebugWifiDirectFutureQrTlsExpectation.Role.HOST)
        requireLoopback(address, port)
        val peerPin = expectation.copyPeerPinForTls()
        try {
            Tls13PeerContext(identity, peerPin).unboundServerSocket().use { server ->
                server.soTimeout = ACCEPT_TIMEOUT_MS
                server.bind(InetSocketAddress(address, port), 1)
                onListening()
                (server.accept() as SSLSocket).use { incoming ->
                    incoming.soTimeout = HANDSHAKE_TIMEOUT_MS
                    incoming.startHandshake()
                    PinnedTlsPeer.requirePinnedTls13(incoming.session, peerPin)
                    // Stop HERE. No application data, further records,
                    // session owner, peer enrollment or text/voice capability.
                }
            }
        } finally {
            peerPin.fill(0)
        }
    }

    /**
     * Joiner connects to a loopback test address only, using the exact
     * host-pin learned in three QR frames. No plaintext fallback.
     */
    fun joinOnce(
        address: InetAddress,
        port: Int,
        identity: X509KeyManager,
        expectation: DebugWifiDirectFutureQrTlsExpectation
    ) {
        require(expectation.role == DebugWifiDirectFutureQrTlsExpectation.Role.JOIN)
        requireLoopback(address, port)
        val peerPin = expectation.copyPeerPinForTls()
        try {
            Tls13PeerContext(identity, peerPin).unconnectedClientSocket().use { client ->
                client.soTimeout = HANDSHAKE_TIMEOUT_MS
                client.connect(InetSocketAddress(address, port), CONNECT_TIMEOUT_MS)
                client.startHandshake()
                PinnedTlsPeer.requirePinnedTls13(client.session, peerPin)
                // No user/application data and no retained TLS socket.
            }
        } finally {
            peerPin.fill(0)
        }
    }
}
