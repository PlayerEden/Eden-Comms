package com.edenpractical.comms.transport

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import com.edenpractical.comms.protocol.TestPacketCodec
import com.edenpractical.comms.security.LocalTlsIdentity
import com.edenpractical.comms.security.PinnedTlsPeer
import com.edenpractical.comms.security.SessionProofBinding
import com.edenpractical.comms.security.SessionProofExchange
import com.edenpractical.comms.security.Tls13PeerContext
import com.edenpractical.comms.security.TlsExporterReadiness
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

/**
 * DEVELOPMENT/DEBUG-ONLY, in-memory two-phone test conversation.
 *
 * Sends ONLY four fixed, synthetic phrases, never user-supplied chat text.
 * Every byte is inside strict mutual TLS 1.3, after both exact certificate pins
 * and the existing two-QR session proof pass. A READY exchange follows proof.
 * No advertising of secure messenger readiness, persistent trust, notifications,
 * message history, file transfer, voice, or unencrypted socket fallback.
 *
 * The older LanTlsProbe remains available as a one-shot diagnostic.
 */
internal class LanDemoSession(
    private val context: Context,
    private val report: (Event) -> Unit
) : AutoCloseable {
    enum class Phase { LISTENING, CONNECTING, READY, SENT, RECEIVED, ENDED, FAILED }
    enum class SendResult { QUEUED, BUSY, NOT_CONNECTED }
    data class Event(val phase: Phase, val description: String, val phraseId: Int? = null)

    companion object {
        private const val PORT = LanTlsProbe.PORT
        private const val ACCEPT_TIMEOUT_MS = 90_000
        private const val CONNECT_TIMEOUT_MS = 7_000
        private const val AUTH_TIMEOUT_MS = 15_000
        private const val HEARTBEAT_MS = 8_000L
    }

    init {
        // Deliberately refuse this experimental data path in release builds,
        // regardless of the app's visual navigation or future feature flags.
        require(context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            "The test conversation is not available in release builds"
        }
    }

    private val worker = Executors.newSingleThreadExecutor { action ->
        Thread(action, "eden-demo-connection").apply { isDaemon = true }
    }
    private val writer = Executors.newSingleThreadExecutor { action ->
        Thread(action, "eden-demo-sender").apply { isDaemon = true }
    }
    private val pulses = Executors.newSingleThreadScheduledExecutor { action ->
        Thread(action, "eden-demo-heartbeat").apply { isDaemon = true }
    }
    private val outputLock = Any()
    private val sockets = SessionSocketRegistry()
    private val sendBudget = DemoSendBudget(2)
    private val heartbeatQueued = AtomicBoolean(false)
    private val ending = AtomicBoolean(false)
    private val terminal = AtomicBoolean(false)
    private val stopped = AtomicBoolean(false)

    @Volatile private var ready = false
    @Volatile private var lastReceivedMs = 0L
    @Volatile private var connectedAtMs = 0L
    @Volatile private var currentStep = LanProbeDiagnostics.Step.WIFI
    @Volatile private var wifiLink: WifiLanEndpoint.Link? = null

    fun host(local: LocalTlsIdentity, expectedPin: ByteArray, binding: SessionProofBinding) {
        run {
            currentStep = LanProbeDiagnostics.Step.WIFI
            val wifi = WifiLanEndpoint.current(context)
                ?: error("No usable Wi-Fi")
            wifiLink = wifi
            currentStep = LanProbeDiagnostics.Step.TLS_SETUP
            val tls = Tls13PeerContext(local.keyManager(), expectedPin)
            val server = tls.unboundServerSocket()
            sockets.ownServer(server)
            server.soTimeout = ACCEPT_TIMEOUT_MS
            currentStep = LanProbeDiagnostics.Step.BIND
            server.bind(InetSocketAddress(wifi.address, PORT), 1)
            currentStep = LanProbeDiagnostics.Step.WAIT_FOR_PEER
            emit(Event(Phase.LISTENING, "Waiting for the second phone…"))
            val socket = server.accept() as SSLSocket
            sockets.ownSocket(socket)
            server.close()
            sockets.releaseServer(server)
            authenticateAndServe(socket, expectedPin, binding, isHost = true)
        }
    }

    fun join(
        local: LocalTlsIdentity,
        expectedPin: ByteArray,
        hostIpv4: String,
        binding: SessionProofBinding,
        retryWhileWaiting: Boolean = true
    ) {
        run {
            currentStep = LanProbeDiagnostics.Step.WIFI
            val wifi = WifiLanEndpoint.current(context)
                ?: error("No usable Wi-Fi")
            wifiLink = wifi
            val peer = LocalIpv4Policy.requireSamePrivateSubnet(
                hostIpv4, wifi.address, wifi.prefixLength
            )
            currentStep = LanProbeDiagnostics.Step.TLS_SETUP
            val tls = Tls13PeerContext(local.keyManager(), expectedPin)
            currentStep = LanProbeDiagnostics.Step.TCP_CONNECT
            emit(Event(Phase.CONNECTING, "Connecting when the other phone is ready…"))
            val raw = connectTcp(wifi, peer, retryWhileWaiting)
            val socket = tls.wrapConnectedClient(raw, peer.hostAddress ?: hostIpv4, PORT)
            sockets.ownSocket(socket)
            authenticateAndServe(socket, expectedPin, binding, isHost = false)
        }
    }

    private fun authenticateAndServe(
        socket: SSLSocket,
        expectedPin: ByteArray,
        binding: SessionProofBinding,
        isHost: Boolean
    ) {
        sockets.requireOpen()
        socket.soTimeout = AUTH_TIMEOUT_MS
        currentStep = LanProbeDiagnostics.Step.TLS_HANDSHAKE
        socket.startHandshake()
        currentStep = LanProbeDiagnostics.Step.VERIFY_PIN
        PinnedTlsPeer.requirePinnedTls13(socket.session, expectedPin)
        sockets.requireOpen()

        currentStep = LanProbeDiagnostics.Step.SESSION_BINDING
        if (isHost) {
            SessionProofExchange.runHost(socket.inputStream, socket.outputStream, binding)
        } else {
            SessionProofExchange.runJoin(socket.inputStream, socket.outputStream, binding)
        }

        sockets.requireOpen()

        // DEVELOPMENT DIAGNOSTIC ONLY. This reads a standard exporter on the
        // live verified TLS 1.3 socket but DOES NOT expose its bytes, change
        // the QR proof, send extra packets or authorize user messages.
        val exporterDiagnostic = TlsExporterReadiness.probeAfterVerifiedDemoProof(socket)
        sockets.requireOpen()

        // Both ends have performed QR transcript checks before any preset text.
        // A separate small READY exchange prevents showing the test as connected
        // until the other end has also progressed beyond the QR proof.
        if (isHost) {
            TestPacketCodec.write(socket.outputStream, TestPacketCodec.ready())
            if (TestPacketCodec.read(socket.inputStream) != TestPacketCodec.ready()) {
                throw IOException("Test session not ready")
            }
        } else {
            if (TestPacketCodec.read(socket.inputStream) != TestPacketCodec.ready()) {
                throw IOException("Test session not ready")
            }
            TestPacketCodec.write(socket.outputStream, TestPacketCodec.ready())
        }

        sockets.requireOpen()
        socket.soTimeout = 0
        connectedAtMs = SystemClock.elapsedRealtime()
        lastReceivedMs = connectedAtMs
        ready = true
        emit(Event(
            Phase.READY,
            "Pinned TLS 1.3 + QR test passed. Local channel-binding check: " +
                exporterDiagnostic.safeDescription + "."
        ))
        startHeartbeat()

        // This receive loop processes ONLY a 3-byte versioned preset/control
        // message inside the bounded FrameCodec envelope (no general text).
        while (!stopped.get()) {
            when (val packet = TestPacketCodec.read(socket.inputStream)) {
                is TestPacketCodec.Packet -> {
                    lastReceivedMs = SystemClock.elapsedRealtime()
                    when (packet.type) {
                        TestPacketCodec.Type.PRESET ->
                            emit(Event(Phase.RECEIVED, "Preset received", packet.phraseId))
                        TestPacketCodec.Type.HEARTBEAT -> Unit
                        TestPacketCodec.Type.CLOSE -> {
                            emitTerminal(Phase.ENDED, "Other phone ended the test.")
                            return
                        }
                        TestPacketCodec.Type.READY ->
                            throw IOException("Unexpected extra ready packet")
                    }
                }
            }
        }
    }

    fun sendPreset(id: Int): SendResult {
        if (!ready || stopped.get() || ending.get()) return SendResult.NOT_CONNECTED
        val packet = TestPacketCodec.preset(id)
        if (!sendBudget.tryAcquire()) return SendResult.BUSY
        return try {
            writer.execute {
                try {
                    if (!ready || stopped.get() || ending.get()) return@execute
                    sendPacket(packet)
                    emit(Event(Phase.SENT, "Test phrase written to TLS (not a receipt)", id))
                } catch (_: IOException) {
                    emitTerminal(Phase.ENDED, "The connection was interrupted.")
                    close()
                } finally {
                    sendBudget.release()
                }
            }
            SendResult.QUEUED
        } catch (_: RuntimeException) {
            sendBudget.release()
            SendResult.NOT_CONNECTED
        }
    }

    /** UI ended test; try a bounded CLOSE packet but never block the UI. */
    fun endByUser() {
        if (!ending.compareAndSet(false, true)) return
        if (!ready || stopped.get()) { close(); return }
        try {
            writer.execute {
                try { sendPacket(TestPacketCodec.close()) } catch (_: Exception) {}
                finally { close() }
            }
        } catch (_: RuntimeException) { close() }
    }

    private fun sendPacket(packet: TestPacketCodec.Packet) {
        synchronized(outputLock) {
            if (!ready || stopped.get()) throw SocketException("Test already closed")
            val tls = sockets.currentSocket() as? SSLSocket
                ?: throw SocketException("Test socket closed")
            TestPacketCodec.write(tls.outputStream, packet)
        }
    }

    private fun startHeartbeat() {
        pulses.scheduleAtFixedRate({
            try {
                if (stopped.get() || !ready) return@scheduleAtFixedRate
            val now = SystemClock.elapsedRealtime()
            val link = wifiLink
            val current = WifiLanEndpoint.current(context)
            if (link == null || current == null ||
                link.network != current.network ||
                link.address != current.address ||
                link.prefixLength != current.prefixLength
            ) {
                emitTerminal(Phase.ENDED, "Wi-Fi changed. The test connection was closed.")
                close()
                return@scheduleAtFixedRate
            }
            if (DemoSessionLivenessPolicy.endReason(
                    nowMs = now,
                    startedMs = connectedAtMs,
                    lastReceivedMs = lastReceivedMs
                ) != null
            ) {
                emitTerminal(Phase.ENDED, "The test session ended or the peer stopped responding.")
                close()
                return@scheduleAtFixedRate
            }
            // Keep at most one heartbeat queued when the writer is busy.
            if (!heartbeatQueued.compareAndSet(false, true)) return@scheduleAtFixedRate
            try {
                writer.execute {
                    try {
                        if (ready && !stopped.get() && !ending.get()) {
                            sendPacket(TestPacketCodec.heartbeat())
                        }
                    } catch (_: IOException) {
                        emitTerminal(Phase.ENDED, "The connection was interrupted.")
                        close()
                    } finally {
                        heartbeatQueued.set(false)
                    }
                }
            } catch (_: RuntimeException) {
                heartbeatQueued.set(false)
            }
            } catch (_: Exception) {
                // Android connectivity APIs may fail unexpectedly. Never let
                // ScheduledExecutor silently cancel the only live watchdog.
                emitTerminal(Phase.ENDED, "Connection monitoring stopped unexpectedly.")
                close()
            }
        }, HEARTBEAT_MS, HEARTBEAT_MS, TimeUnit.MILLISECONDS)
    }

    private fun connectTcp(
        wifi: WifiLanEndpoint.Link,
        peer: InetAddress,
        retryWhileWaiting: Boolean
    ): Socket {
        val started = SystemClock.elapsedRealtime()
        while (!stopped.get()) {
            val current = WifiLanEndpoint.current(context) ?: throw SocketException("Wi-Fi lost")
            if (current.network != wifi.network ||
                current.address != wifi.address ||
                current.prefixLength != wifi.prefixLength
            ) throw SocketException("Wi-Fi changed")

            val raw = wifi.network.socketFactory.createSocket()
            sockets.ownSocket(raw)
            try {
                raw.connect(
                    InetSocketAddress(peer, PORT),
                    if (retryWhileWaiting) LanConnectRetryPolicy.CONNECT_TIMEOUT_MS
                    else CONNECT_TIMEOUT_MS
                )
                return raw
            } catch (e: IOException) {
                try { raw.close() } catch (_: Exception) { }
                sockets.releaseSocket(raw)
                val elapsed = SystemClock.elapsedRealtime() - started
                if (!LanConnectRetryPolicy.shouldRetry(e, elapsed, retryWhileWaiting)) throw e
            }
            try { Thread.sleep(LanConnectRetryPolicy.RETRY_DELAY_MS) }
            catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                throw SocketException("Test cancelled")
            }
        }
        throw SocketException("Test cancelled")
    }

    private fun run(action: () -> Unit) {
        worker.execute {
            try { if (!stopped.get()) action() }
            catch (e: Exception) {
                if (!stopped.get()) {
                    if (ready) emitTerminal(Phase.ENDED, "The connection was interrupted.")
                    else emitTerminal(Phase.FAILED,
                        LanProbeDiagnostics.classify(currentStep, e).display())
                }
            } finally { close() }
        }
    }

    private fun emit(event: Event) {
        if (!stopped.get()) report(event)
    }

    private fun emitTerminal(phase: Phase, description: String) {
        if (!stopped.get() && terminal.compareAndSet(false, true)) {
            emit(Event(phase, description))
        }
    }

    override fun close() {
        if (!stopped.compareAndSet(false, true)) return
        ready = false
        ending.set(true)
        sockets.close()
        pulses.shutdownNow()
        writer.shutdownNow()
        worker.shutdownNow()
    }
}
