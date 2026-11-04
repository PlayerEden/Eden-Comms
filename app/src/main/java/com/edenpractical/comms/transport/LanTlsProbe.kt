package com.edenpractical.comms.transport

import android.content.Context
import com.edenpractical.comms.security.LocalTlsIdentity
import com.edenpractical.comms.security.PinnedTlsPeer
import com.edenpractical.comms.security.Tls13PeerContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

/**
 * One-shot encrypted LAN authentication probe: TLS handshake ONLY.
 *
 * No message bytes are written/read. Success means the local TLS provider
 * negotiated 1.3 with a matching, pre-verified peer certificate fingerprint.
 * The connection is closed after the check (no background service).
 *
 * This is an internal milestone test, not a full cryptographically reviewed
 * pairing or secure messaging feature.
 */
internal class LanTlsProbe(
    private val context: Context,
    private val report: (Event) -> Unit
) : AutoCloseable {
    enum class Phase { LISTENING, CONNECTING, SUCCEEDED, FAILED, CANCELLED }
    data class Event(val phase: Phase, val description: String)

    companion object {
        const val PORT: Int = 42519
        private const val ACCEPT_TIMEOUT_MS = 30_000
        private const val CONNECT_TIMEOUT_MS = 7_000
        private const val TLS_TIMEOUT_MS = 12_000
    }

    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "eden-comms-lan-probe").apply { isDaemon = true }
    }

    @Volatile private var cancelled = false
    @Volatile private var activeSocket: Socket? = null
    @Volatile private var activeServer: SSLServerSocket? = null

    fun host(local: LocalTlsIdentity, verifiedPeerFingerprint: ByteArray) {
        execute {
            val wifi = WifiLanEndpoint.current(context)
                ?: error("No usable Wi-Fi interface")
            val tls = Tls13PeerContext(local.keyManager(), verifiedPeerFingerprint)
            val server = tls.unboundServerSocket()
            activeServer = server
            server.soTimeout = ACCEPT_TIMEOUT_MS
            server.bind(InetSocketAddress(wifi.address, PORT), 1)
            emit(Phase.LISTENING, "Listening on " + wifi.address.hostAddress +
                ":" + PORT + " • waiting for a TLS peer")
            val socket = server.accept() as SSLSocket
            activeSocket = socket
            server.close()
            activeServer = null
            socket.soTimeout = TLS_TIMEOUT_MS
            socket.startHandshake()
            PinnedTlsPeer.requirePinnedTls13(socket.session, verifiedPeerFingerprint)
            emit(Phase.SUCCEEDED, "Mutual TLS 1.3 handshake passed. Test link closed; text is still disabled.")
        }
    }

    fun join(
        local: LocalTlsIdentity,
        verifiedPeerFingerprint: ByteArray,
        hostIpv4: String
    ) {
        execute {
            val wifi = WifiLanEndpoint.current(context)
                ?: error("No usable Wi-Fi interface")
            val peer = LocalIpv4Policy.requireSamePrivateSubnet(
                hostIpv4, wifi.address, wifi.prefixLength
            )
            val tls = Tls13PeerContext(local.keyManager(), verifiedPeerFingerprint)
            val raw = wifi.network.socketFactory.createSocket()
            activeSocket = raw
            emit(Phase.CONNECTING, "Connecting to nearby Wi-Fi host • TLS 1.3 only")
            raw.connect(InetSocketAddress(peer, PORT), CONNECT_TIMEOUT_MS)
            val socket = tls.wrapConnectedClient(raw, peer.hostAddress ?: hostIpv4, PORT)
            activeSocket = socket
            socket.soTimeout = TLS_TIMEOUT_MS
            socket.startHandshake()
            PinnedTlsPeer.requirePinnedTls13(socket.session, verifiedPeerFingerprint)
            emit(Phase.SUCCEEDED, "Mutual TLS 1.3 handshake passed. Test link closed; text is still disabled.")
        }
    }

    private fun execute(action: () -> Unit) {
        executor.execute {
            try {
                if (!cancelled) action()
            } catch (_: Exception) {
                emit(Phase.FAILED, "Secure LAN test failed. Check both phones' Wi-Fi, local IP and verified fingerprints.")
            } finally {
                closeSockets()
            }
        }
    }

    private fun emit(phase: Phase, description: String) {
        if (!cancelled) report(Event(phase, description))
    }

    private fun closeSockets() {
        try { activeSocket?.close() } catch (_: Exception) { }
        try { activeServer?.close() } catch (_: Exception) { }
        activeSocket = null
        activeServer = null
    }

    override fun close() {
        cancelled = true
        closeSockets()
        executor.shutdownNow()
    }
}
