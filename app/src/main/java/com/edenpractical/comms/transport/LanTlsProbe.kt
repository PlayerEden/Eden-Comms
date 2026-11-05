package com.edenpractical.comms.transport

import android.content.Context
import android.os.SystemClock
import com.edenpractical.comms.security.LocalTlsIdentity
import com.edenpractical.comms.security.PinnedTlsPeer
import com.edenpractical.comms.security.SessionProofBinding
import com.edenpractical.comms.security.SessionProofExchange
import com.edenpractical.comms.security.Tls13PeerContext
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException
import java.net.Socket
import java.util.concurrent.Executors
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

/**
 * One-shot encrypted LAN authentication probe: TLS handshake ONLY.
 *
 * No message/voice bytes are transmitted. After mutually pinned TLS 1.3, a
 * bounded QR transcript/challenge proof is exchanged INSIDE TLS (test control
 * bytes only). The connection then closes; no background service or chat.
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
        private const val ACCEPT_TIMEOUT_MS = 90_000
        private const val CONNECT_TIMEOUT_MS = 7_000
        private const val TLS_TIMEOUT_MS = 12_000
    }

    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "eden-comms-lan-probe").apply { isDaemon = true }
    }

    @Volatile private var cancelled = false
    @Volatile private var activeSocket: Socket? = null
    @Volatile private var activeServer: SSLServerSocket? = null

    /** Updated on this probe's single worker thread; no sensitive values. */
    @Volatile private var diagnosticStep = LanProbeDiagnostics.Step.WIFI

    fun host(local: LocalTlsIdentity, verifiedPeerFingerprint: ByteArray, binding: SessionProofBinding? = null) {
        execute {
            diagnosticStep = LanProbeDiagnostics.Step.WIFI
            val wifi = WifiLanEndpoint.current(context)
                ?: error("No usable Wi-Fi interface")
            diagnosticStep = LanProbeDiagnostics.Step.TLS_SETUP
            val tls = Tls13PeerContext(local.keyManager(), verifiedPeerFingerprint)
            val server = tls.unboundServerSocket()
            activeServer = server
            server.soTimeout = ACCEPT_TIMEOUT_MS
            diagnosticStep = LanProbeDiagnostics.Step.BIND
            server.bind(InetSocketAddress(wifi.address, PORT), 1)
            diagnosticStep = LanProbeDiagnostics.Step.WAIT_FOR_PEER
            emit(Phase.LISTENING, "Waiting for the other phone…")
            val socket = server.accept() as SSLSocket
            activeSocket = socket
            server.close()
            activeServer = null
            socket.soTimeout = TLS_TIMEOUT_MS
            diagnosticStep = LanProbeDiagnostics.Step.TLS_HANDSHAKE
            socket.startHandshake()
            diagnosticStep = LanProbeDiagnostics.Step.VERIFY_PIN
            PinnedTlsPeer.requirePinnedTls13(socket.session, verifiedPeerFingerprint)
            if (binding != null) {
                diagnosticStep = LanProbeDiagnostics.Step.SESSION_BINDING
                SessionProofExchange.runHost(socket.inputStream, socket.outputStream, binding)
                emit(Phase.SUCCEEDED, "Mutual TLS 1.3 + QR session check passed. Test link closed; text remains disabled.")
            } else {
                // Legacy Developer Tools handshake-only diagnostic. NEVER a
                // real verified chat; release capabilities stay disabled.
                emit(Phase.SUCCEEDED, "Mutual TLS 1.3 handshake passed (manual test only). QR session was not checked; text is disabled.")
            }
        }
    }

    fun join(
        local: LocalTlsIdentity,
        verifiedPeerFingerprint: ByteArray,
        hostIpv4: String,
        binding: SessionProofBinding? = null,
        retryWhileWaiting: Boolean = false
    ) {
        execute {
            diagnosticStep = LanProbeDiagnostics.Step.WIFI
            val wifi = WifiLanEndpoint.current(context)
                ?: error("No usable Wi-Fi interface")
            val peer = LocalIpv4Policy.requireSamePrivateSubnet(
                hostIpv4, wifi.address, wifi.prefixLength
            )
            diagnosticStep = LanProbeDiagnostics.Step.TLS_SETUP
            val tls = Tls13PeerContext(local.keyManager(), verifiedPeerFingerprint)
            diagnosticStep = LanProbeDiagnostics.Step.TCP_CONNECT
            emit(Phase.CONNECTING, "Waiting for the other phone…")
            val raw = connectTcp(wifi, peer, retryWhileWaiting)
            val socket = tls.wrapConnectedClient(raw, peer.hostAddress ?: hostIpv4, PORT)
            activeSocket = socket
            socket.soTimeout = TLS_TIMEOUT_MS
            diagnosticStep = LanProbeDiagnostics.Step.TLS_HANDSHAKE
            socket.startHandshake()
            diagnosticStep = LanProbeDiagnostics.Step.VERIFY_PIN
            PinnedTlsPeer.requirePinnedTls13(socket.session, verifiedPeerFingerprint)
            if (binding != null) {
                diagnosticStep = LanProbeDiagnostics.Step.SESSION_BINDING
                SessionProofExchange.runJoin(socket.inputStream, socket.outputStream, binding)
                emit(Phase.SUCCEEDED, "Mutual TLS 1.3 + QR session check passed. Test link closed; text remains disabled.")
            } else {
                // Legacy Developer Tools handshake-only diagnostic.
                emit(Phase.SUCCEEDED, "Mutual TLS 1.3 handshake passed (manual test only). QR session was not checked; text is disabled.")
            }
        }
    }

    /**
     * The joining phone may present its reply QR for a short time before the
     * other phone has scanned it and opened its listener. Retry only *TCP*
     * reachability failures, never TLS/authentication failures.
     */
    private fun connectTcp(
        wifi: WifiLanEndpoint.Link,
        peer: InetAddress,
        retryWhileWaiting: Boolean
    ): Socket {
        val startedMs = SystemClock.elapsedRealtime()
        while (!cancelled) {
            val current = WifiLanEndpoint.current(context)
                ?: throw SocketException("Wi-Fi unavailable")
            if (current.network != wifi.network ||
                current.address != wifi.address ||
                current.prefixLength != wifi.prefixLength
            ) throw SocketException("Wi-Fi changed")

            val raw = wifi.network.socketFactory.createSocket()
            activeSocket = raw
            try {
                raw.connect(
                    InetSocketAddress(peer, PORT),
                    if (retryWhileWaiting) LanConnectRetryPolicy.CONNECT_TIMEOUT_MS
                    else CONNECT_TIMEOUT_MS
                )
                return raw
            } catch (error: IOException) {
                try { raw.close() } catch (_: Exception) { }
                activeSocket = null
                val elapsedMs = SystemClock.elapsedRealtime() - startedMs
                if (!LanConnectRetryPolicy.shouldRetry(error, elapsedMs, retryWhileWaiting)) {
                    throw error
                }
            }
            try {
                Thread.sleep(LanConnectRetryPolicy.RETRY_DELAY_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                throw SocketException("Connection cancelled")
            }
        }
        throw SocketException("Connection cancelled")
    }

    private fun execute(action: () -> Unit) {
        executor.execute {
            try {
                if (!cancelled) action()
            } catch (error: Exception) {
                // Never surface raw exception messages, stack traces, QR data,
                // IP addresses, certificates or keys to users or logs.
                val safe = LanProbeDiagnostics.classify(diagnosticStep, error)
                emit(Phase.FAILED, safe.display())
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
