package com.edenpractical.comms.transport

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import com.edenpractical.comms.protocol.TestPacketCodec
import com.edenpractical.comms.protocol.TestPacketException
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
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
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
    enum class Phase {
        LISTENING, CONNECTING, PROGRESS, READY, APPROVAL_UPDATE, APPROVAL_COMPLETE,
        SENT, RECEIVED, ENDED, FAILED
    }
    enum class SendResult { QUEUED, BUSY, NOT_CONNECTED, AWAITING_APPROVAL }
    enum class ApprovalResult { QUEUED, ALREADY_PENDING, NOT_CONNECTED }
    data class Event(
        val phase: Phase,
        val description: String,
        val phraseId: Int? = null,
        val progress: LanDemoStatus.Progress? = null,
        val endCode: LanDemoStatus.EndCode? = null,
        val failureCode: LanProbeDiagnostics.Code? = null,
        val approvalState: DebugApprovalGate.State? = null
    )

    companion object {
        private const val PORT = LanTlsProbe.PORT
        private const val ACCEPT_TIMEOUT_MS = 90_000
        private const val CONNECT_TIMEOUT_MS = 7_000
        private const val AUTH_TIMEOUT_MS = 15_000
        private const val HEARTBEAT_MS = 8_000L
        private const val PRE_READY_WIFI_CHECK_MS = 2_000L
        private const val APPROVAL_TIMEOUT_MS = 90_000L
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
    // Separate from the heartbeat loop: connectivity calls on its thread may
    // block and must not delay a user's requested hard socket close.
    private val cancellationTimer = Executors.newSingleThreadScheduledExecutor { action ->
        Thread(action, "eden-demo-cancel-timer").apply { isDaemon = true }
    }
    private val outputLock = Any()
    private val sockets = SessionSocketRegistry()
    private val sendBudget = DemoSendBudget(2)
    private val heartbeatQueued = AtomicBoolean(false)
    private val approvalQueued = AtomicBoolean(false)
    private val approvalGate = DebugApprovalGate()
    private val ending = AtomicBoolean(false)
    private val terminal = AtomicBoolean(false)
    private val stopped = AtomicBoolean(false)

    @Volatile private var ready = false
    @Volatile private var lastReceivedMs = 0L
    @Volatile private var connectedAtMs = 0L
    @Volatile private var currentStep = LanProbeDiagnostics.Step.WIFI
    @Volatile private var wifiLink: WifiLanEndpoint.Link? = null
    @Volatile private var preReadyWifiMonitor: ScheduledFuture<*>? = null

    fun host(local: LocalTlsIdentity, expectedPin: ByteArray, binding: SessionProofBinding) {
        run {
            currentStep = LanProbeDiagnostics.Step.WIFI
            val wifi = WifiLanEndpoint.current(context)
                ?: error("No usable Wi-Fi")
            wifiLink = wifi
            startPreReadyWifiMonitor()
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
            startPreReadyWifiMonitor()
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
        requireStableWifi()
        currentStep = LanProbeDiagnostics.Step.TLS_HANDSHAKE
        emitProgress(LanDemoStatus.Progress.TLS_NEGOTIATING)
        socket.startHandshake()
        currentStep = LanProbeDiagnostics.Step.VERIFY_PIN
        emitProgress(LanDemoStatus.Progress.PEER_IDENTITY)
        PinnedTlsPeer.requirePinnedTls13(socket.session, expectedPin)
        sockets.requireOpen()
        requireStableWifi()

        currentStep = LanProbeDiagnostics.Step.SESSION_BINDING
        emitProgress(LanDemoStatus.Progress.QR_PROOF)
        if (isHost) {
            SessionProofExchange.runHost(socket.inputStream, socket.outputStream, binding)
        } else {
            SessionProofExchange.runJoin(socket.inputStream, socket.outputStream, binding)
        }

        sockets.requireOpen()
        requireStableWifi()

        // DEVELOPMENT DIAGNOSTIC ONLY. This reads a standard exporter on the
        // live verified TLS 1.3 socket but DOES NOT expose its bytes, change
        // the QR proof, send extra packets or authorize user messages.
        val exporterDiagnostic = TlsExporterReadiness.probeAfterVerifiedDemoProof(socket)
        sockets.requireOpen()
        requireStableWifi()

        // Both ends have performed QR transcript checks before any preset text.
        // A separate small READY exchange prevents showing the test as connected
        // until the other end has also progressed beyond the QR proof.
        currentStep = LanProbeDiagnostics.Step.READY_EXCHANGE
        emitProgress(LanDemoStatus.Progress.BOTH_READY)
        if (isHost) {
            TestPacketCodec.write(socket.outputStream, TestPacketCodec.ready())
            if (TestPacketCodec.read(socket.inputStream) != TestPacketCodec.ready()) {
                throw TestPacketException()
            }
        } else {
            if (TestPacketCodec.read(socket.inputStream) != TestPacketCodec.ready()) {
                throw TestPacketException()
            }
            TestPacketCodec.write(socket.outputStream, TestPacketCodec.ready())
        }

        sockets.requireOpen()
        requireStableWifi()
        socket.soTimeout = 0
        connectedAtMs = SystemClock.elapsedRealtime()
        lastReceivedMs = connectedAtMs
        ready = true
        preReadyWifiMonitor?.cancel(false)
        emit(Event(
            Phase.READY,
            "Pinned TLS 1.3 + QR test passed. Await two explicit experimental " +
                "approvals before exchanging preset phrases. Local exporter " +
                "compatibility: " + exporterDiagnostic.safeDescription + "."
        ))
        startHeartbeat()

        // This receive loop processes ONLY a 3-byte versioned preset/control
        // message inside the bounded FrameCodec envelope (no general text).
        while (!stopped.get()) {
            when (val packet = TestPacketCodec.read(socket.inputStream)) {
                is TestPacketCodec.Packet -> {
                    lastReceivedMs = SystemClock.elapsedRealtime()
                    when (packet.type) {
                        TestPacketCodec.Type.PRESET -> {
                            if (!approvalGate.maySendPreset()) throw TestPacketException()
                            emit(Event(Phase.RECEIVED, "Preset received", packet.phraseId))
                        }
                        TestPacketCodec.Type.APPROVE -> {
                            val state = approvalGate.peerObserved()
                            if (state == DebugApprovalGate.State.INVALID ||
                                state == DebugApprovalGate.State.CLOSED ||
                                state == DebugApprovalGate.State.DECLINED
                            ) throw TestPacketException()
                            emitApprovalState(state)
                        }
                        TestPacketCodec.Type.DECLINE -> {
                            approvalGate.decline()
                            emitTerminal(Phase.ENDED, LanDemoStatus.EndCode.PEER_DECLINED)
                            return
                        }
                        TestPacketCodec.Type.HEARTBEAT -> Unit
                        TestPacketCodec.Type.CLOSE -> {
                            emitTerminal(Phase.ENDED, LanDemoStatus.EndCode.PEER_ENDED)
                            return
                        }
                        TestPacketCodec.Type.READY ->
                            throw TestPacketException()
                    }
                }
            }
        }
    }

    fun sendPreset(id: Int): SendResult {
        if (!ready || stopped.get() || ending.get()) return SendResult.NOT_CONNECTED
        if (!approvalGate.maySendPreset()) return SendResult.AWAITING_APPROVAL
        val packet = TestPacketCodec.preset(id)
        if (!sendBudget.tryAcquire()) return SendResult.BUSY
        return try {
            writer.execute {
                try {
                    if (!ready || stopped.get() || ending.get() ||
                        !approvalGate.maySendPreset()) return@execute
                    sendPacket(packet)
                    emit(Event(Phase.SENT, "Test phrase written to TLS (not a receipt)", id))
                } catch (_: IOException) {
                    emitTerminal(Phase.ENDED, LanDemoStatus.EndCode.LINK_INTERRUPTED)
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

    /**
     * Explicit local tap sends a debug-only control over the already pinned,
     * QR-proved and READY test socket. Only a completed TLS write counts as a
     * local test approval. This is NOT production mutual human consent.
     */
    fun approveForTest(): ApprovalResult {
        if (!ready || stopped.get() || ending.get()) return ApprovalResult.NOT_CONNECTED
        if (approvalGate.snapshot() !in setOf(
                DebugApprovalGate.State.WAITING, DebugApprovalGate.State.PEER_ONLY
            )) return ApprovalResult.ALREADY_PENDING
        if (!approvalQueued.compareAndSet(false, true)) return ApprovalResult.ALREADY_PENDING
        return try {
            writer.execute {
                try {
                    if (!ready || stopped.get() || ending.get()) return@execute
                    sendPacket(TestPacketCodec.approve())
                    val state = approvalGate.localWritten()
                    if (state == DebugApprovalGate.State.INVALID ||
                        state == DebugApprovalGate.State.CLOSED ||
                        state == DebugApprovalGate.State.DECLINED
                    ) {
                        throw TestPacketException()
                    }
                    emitApprovalState(state)
                } catch (_: IOException) {
                    emitTerminal(Phase.ENDED, LanDemoStatus.EndCode.LINK_INTERRUPTED)
                    close()
                } finally {
                    approvalQueued.set(false)
                }
            }
            ApprovalResult.QUEUED
        } catch (_: RuntimeException) {
            approvalQueued.set(false)
            ApprovalResult.NOT_CONNECTED
        }
    }

    /** Explicit DECLINE: attempt a fixed TLS control, then close fail-closed. */
    fun declineForTest() {
        if (!ending.compareAndSet(false, true)) return
        approvalGate.decline()
        if (!ready || stopped.get()) { close(); return }
        stopWithControl(TestPacketCodec.decline())
    }

    private fun emitApprovalState(state: DebugApprovalGate.State) {
        when (state) {
            DebugApprovalGate.State.BOTH_OBSERVED ->
                emit(Event(Phase.APPROVAL_COMPLETE,
                    "Both debug approvals observed by this phone; preset test only.",
                    approvalState = state))
            DebugApprovalGate.State.LOCAL_ONLY, DebugApprovalGate.State.PEER_ONLY ->
                emit(Event(Phase.APPROVAL_UPDATE,
                    "Approval observation updated; waiting for both phones.",
                    approvalState = state))
            else -> Unit
        }
    }

    /**
     * UI cancellation is immediate and irreversible. A best-effort CLOSE goes
     * through the writer, but a *separate* scheduled task forces socket close
     * after 1.2 seconds if that writer is blocked behind pending output.
     */
    fun endByUser() {
        if (!ending.compareAndSet(false, true)) return
        approvalGate.close()
        if (!ready || stopped.get()) { close(); return }
        stopWithControl(TestPacketCodec.close())
    }

    private fun stopWithControl(control: TestPacketCodec.Packet) {
        LanGracefulStop.begin(
            scheduleHardClose = { delay, action ->
                cancellationTimer.schedule({ action() }, delay, TimeUnit.MILLISECONDS)
            },
            enqueueClose = {
                writer.execute {
                    try { sendPacket(control) }
                    catch (_: Exception) { /* Best effort only. */ }
                    finally { close() }
                }
            },
            hardClose = { close() }
        )
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
            if (link == null || !WifiLinkContinuity.unchanged(
                    link.network, link.address, link.prefixLength,
                    current?.network, current?.address, current?.prefixLength
                )) {
                emitTerminal(Phase.ENDED, LanDemoStatus.EndCode.WIFI_CHANGED)
                close()
                return@scheduleAtFixedRate
            }
            if (!approvalGate.maySendPreset() &&
                now - connectedAtMs >= APPROVAL_TIMEOUT_MS
            ) {
                emitTerminal(Phase.ENDED, LanDemoStatus.EndCode.APPROVAL_TIMED_OUT)
                close()
                return@scheduleAtFixedRate
            }
            val livenessReason = DemoSessionLivenessPolicy.endReason(
                nowMs = now,
                startedMs = connectedAtMs,
                lastReceivedMs = lastReceivedMs
            )
            if (livenessReason != null) {
                emitTerminal(Phase.ENDED, LanDemoStatus.livenessEnd(livenessReason))
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
                        emitTerminal(Phase.ENDED, LanDemoStatus.EndCode.LINK_INTERRUPTED)
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
                emitTerminal(Phase.ENDED, LanDemoStatus.EndCode.MONITOR_FAILED)
                close()
            }
        }, HEARTBEAT_MS, HEARTBEAT_MS, TimeUnit.MILLISECONDS)
    }

    /**
     * A presenter may be waiting inside accept(), or a joiner retrying TCP,
     * when the handset switches Wi-Fi. Check the original foreground link
     * every ~2 seconds during setup, without scanning or requiring WAN.
     */
    private fun startPreReadyWifiMonitor() {
        preReadyWifiMonitor = pulses.scheduleAtFixedRate({
            if (stopped.get() || ready || ending.get()) return@scheduleAtFixedRate
            try {
                requireStableWifi()
            } catch (e: Exception) {
                // The worker may have transitioned to READY while this
                // diagnostic poll was resolving Android connectivity state.
                // The post-READY heartbeat owns the connection from then on.
                if (stopped.get() || ready || ending.get()) return@scheduleAtFixedRate
                val failure = LanProbeDiagnostics.classify(LanProbeDiagnostics.Step.WIFI, e)
                emitTerminal(Phase.FAILED, failure)
                close()
            }
        }, PRE_READY_WIFI_CHECK_MS, PRE_READY_WIFI_CHECK_MS, TimeUnit.MILLISECONDS)
    }

    /**
     * Revalidate the initial Wi-Fi network after each expensive authentication
     * stage. No roaming/fallback is permitted within a reciprocal QR session.
     */
    private fun requireStableWifi() {
        val initial = wifiLink ?: throw LanWifiChangedException()
        val current = WifiLanEndpoint.current(context)
        if (!WifiLinkContinuity.unchanged(
                initial.network, initial.address, initial.prefixLength,
                current?.network, current?.address, current?.prefixLength
            )) throw LanWifiChangedException()
    }

    private fun connectTcp(
        wifi: WifiLanEndpoint.Link,
        peer: InetAddress,
        retryWhileWaiting: Boolean
    ): Socket {
        val started = SystemClock.elapsedRealtime()
        while (!stopped.get()) {
            if (retryWhileWaiting &&
                SystemClock.elapsedRealtime() - started >= LanConnectRetryPolicy.MAX_WAIT_MS
            ) throw SocketTimeoutException("Local test connection retry window ended")
            val current = WifiLanEndpoint.current(context)
            if (!WifiLinkContinuity.unchanged(
                    wifi.network, wifi.address, wifi.prefixLength,
                    current?.network, current?.address, current?.prefixLength
                )) throw LanWifiChangedException()

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
            val delay = LanConnectRetryPolicy.delayBeforeRetryMs(
                SystemClock.elapsedRealtime() - started
            )
            if (delay <= 0L) throw SocketTimeoutException("Local test connection retry window ended")
            try { Thread.sleep(delay) }
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
                    if (ready) {
                        emitTerminal(Phase.ENDED, LanDemoStatus.activeFailure(e))
                    } else {
                        val failure = LanProbeDiagnostics.classify(currentStep, e)
                        emitTerminal(Phase.FAILED, failure)
                    }
                }
            } finally { close() }
        }
    }

    private fun emitProgress(progress: LanDemoStatus.Progress) {
        emit(Event(Phase.PROGRESS, progress.display, progress = progress))
    }

    private fun emit(event: Event) {
        if (!stopped.get() && !terminal.get()) report(event)
    }

    private fun emitTerminal(phase: Phase, endCode: LanDemoStatus.EndCode) {
        if (!stopped.get() && terminal.compareAndSet(false, true)) {
            report(Event(phase, endCode.display, endCode = endCode))
        }
    }

    private fun emitTerminal(phase: Phase, failure: LanProbeDiagnostics.Failure) {
        if (!stopped.get() && terminal.compareAndSet(false, true)) {
            report(Event(phase, failure.display(), failureCode = failure.code))
        }
    }

    override fun close() {
        if (!stopped.compareAndSet(false, true)) return
        ready = false
        ending.set(true)
        approvalGate.close()
        sockets.close()
        preReadyWifiMonitor?.cancel(false)
        pulses.shutdownNow()
        cancellationTimer.shutdownNow()
        writer.shutdownNow()
        worker.shutdownNow()
    }
}
