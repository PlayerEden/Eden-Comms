package com.edenpractical.comms.wifidirect

import com.edenpractical.comms.security.PinnedTlsPeer
import com.edenpractical.comms.security.SessionProofBinding
import com.edenpractical.comms.security.SessionProofExchange
import com.edenpractical.comms.security.Tls13PeerContext
import java.net.InetAddress
import java.net.InetSocketAddress
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509KeyManager

/**
 * F3-B2: isolated JVM IPv4 loopback, real TCP/TLS 1.3 + encrypted fresh QR
 * challenge proofs and whole-transcript agreement on that SAME socket.
 *
 * Both real TLS endpoints close IMMEDIATELY after the fixed control exchange.
 * A successful return NEVER authorizes user text, voice, contact trust,
 * connection retention, real Android Wi-Fi Direct routing or human consent.
 * This object is NOT referenced by the live P2P controllers or Compose UI.
 */
internal object DebugWifiDirectFutureQrProofSocketLab {
    const val ACCEPT_TIMEOUT_MS = 4_000
    const val CONNECT_TIMEOUT_MS = 2_000
    const val IO_TIMEOUT_MS = 4_000

    private fun requireJVMOnly(address: InetAddress, port: Int) {
        require(address.isLoopbackAddress && address.address.size == 4) {
            "Future Wi-Fi Direct QR proof test accepts ONLY JVM IPv4 loopback"
        }
        require(port in 1_024..65_535)
    }

    fun hostOnce(
        address: InetAddress,
        port: Int,
        identity: X509KeyManager,
        expectation: DebugWifiDirectFutureQrTlsExpectation,
        onListening: () -> Unit = {}
    ) {
        require(expectation.role == DebugWifiDirectFutureQrTlsExpectation.Role.HOST)
        requireJVMOnly(address, port)
        val pin = expectation.copyPeerPinForTls()
        try {
            Tls13PeerContext(identity, pin).unboundServerSocket().use { listener ->
                listener.soTimeout = ACCEPT_TIMEOUT_MS
                listener.bind(InetSocketAddress(address, port), 1)
                onListening()
                (listener.accept() as SSLSocket).use { socket ->
                    socket.soTimeout = IO_TIMEOUT_MS
                    socket.startHandshake()
                    PinnedTlsPeer.requirePinnedTls13(socket.session, pin)
                    runProofOnSameSocket(socket, expectation, isHost = true)
                    // Deliberately stop before any chat/voice/approval.
                }
            }
        } finally {
            pin.fill(0)
        }
    }

    fun joinOnce(
        address: InetAddress,
        port: Int,
        identity: X509KeyManager,
        expectation: DebugWifiDirectFutureQrTlsExpectation
    ) {
        require(expectation.role == DebugWifiDirectFutureQrTlsExpectation.Role.JOIN)
        requireJVMOnly(address, port)
        val pin = expectation.copyPeerPinForTls()
        try {
            Tls13PeerContext(identity, pin).unconnectedClientSocket().use { socket ->
                socket.soTimeout = IO_TIMEOUT_MS
                socket.connect(InetSocketAddress(address, port), CONNECT_TIMEOUT_MS)
                socket.startHandshake()
                PinnedTlsPeer.requirePinnedTls13(socket.session, pin)
                runProofOnSameSocket(socket, expectation, isHost = false)
                // Deliberately stop before any chat/voice/approval.
            }
        } finally {
            pin.fill(0)
        }
    }

    private fun runProofOnSameSocket(
        socket: SSLSocket,
        expectation: DebugWifiDirectFutureQrTlsExpectation,
        isHost: Boolean
    ) {
        val binding = expectation.copyQrProofBindingForLab()
        val transcript = expectation.copyTranscriptForReview()
        try {
            val challenges = if (isHost) {
                SessionProofExchange.runHost(
                    socket.inputStream, socket.outputStream, binding
                )
            } else {
                SessionProofExchange.runJoin(
                    socket.inputStream, socket.outputStream, binding
                )
            }
            try {
                // Public digest, NOT an encryption key or TLS exporter.
                val digest = DebugWifiDirectFutureQrCommitment.derive(
                    binding, challenges, transcript
                )
                try {
                    DebugWifiDirectFutureQrCommitment.exchange(
                        socket.inputStream, socket.outputStream, isHost, digest
                    )
                } finally {
                    digest.fill(0)
                }
            } finally {
                challenges.clear()
            }
        } finally {
            transcript.fill(0)
            binding.hostPin.fill(0)
            binding.joinPin.fill(0)
            binding.hostNonce.fill(0)
            binding.joinNonce.fill(0)
        }
    }
}
