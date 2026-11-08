package com.edenpractical.comms.transport

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import com.edenpractical.comms.protocol.TestPacketCodec
import com.edenpractical.comms.protocol.DebugLanVoiceWireCodec
import com.edenpractical.comms.protocol.DebugLanVoiceFloorCodec
import com.edenpractical.comms.protocol.DebugLanVoiceAssembler
import com.edenpractical.comms.protocol.TestPacketException
import com.edenpractical.comms.security.LocalTlsIdentity
import com.edenpractical.comms.security.PinnedTlsPeer
import com.edenpractical.comms.security.SessionProofBinding
import com.edenpractical.comms.security.SessionProofExchange
import com.edenpractical.comms.security.Tls13PeerContext
import com.edenpractical.comms.security.DebugTlsChannelAgreement
import com.edenpractical.comms.security.DebugApprovalCommitment
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
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

/**
 * DEVELOPMENT/DEBUG-ONLY, in-memory two-phone test conversation.
 *
 * Sends four fixed synthetic phrases AND bounded user-typed development
 * text for functional QA ONLY after the existing session-bound, two-party
 * debug approvals and final confirmations. This is an UNREVIEWED preview,
 * never a safe destination for sensitive content or a production messenger.
 * Every byte is inside pinned mutual TLS 1.3 after the two-QR session proof.
 * No saved history, delivery claims, voice or plaintext socket fallback.
 *
 * The older LanTlsProbe remains available as a one-shot diagnostic.
 */
internal class LanDemoSession(
    private val context: Context,
    private val report: (Event) -> Unit
) : AutoCloseable {
    enum class Phase {
        LISTENING, CONNECTING, PROGRESS, READY, APPROVAL_UPDATE, APPROVAL_COMPLETE,
        SENT, RECEIVED, TEXT_WRITTEN, TEXT_RECEIVED, TEXT_PEER_ACK,
        VOICE_WRITTEN, VOICE_STARTED, VOICE_FINISHED, VOICE_RECEIVED,
        VOICE_FLOOR_GRANTED, VOICE_FLOOR_DENIED, ENDED, FAILED
    }
    enum class SendResult { QUEUED, BUSY, NOT_CONNECTED, AWAITING_APPROVAL }
    enum class ApprovalResult { QUEUED, ALREADY_PENDING, NOT_CONNECTED }
    enum class TextSendResult { QUEUED, INVALID_TEXT, BUSY, NOT_CONNECTED, AWAITING_APPROVAL }
    enum class VoiceSendResult { QUEUED, INVALID_AUDIO, BUSY, NOT_CONNECTED, AWAITING_APPROVAL }
    enum class FloorRequestResult { GRANTED, PENDING, BUSY, NOT_CONNECTED, AWAITING_APPROVAL }
    data class Event(
        val phase: Phase,
        val description: String,
        val phraseId: Int? = null,
        val progress: LanDemoStatus.Progress? = null,
        val endCode: LanDemoStatus.EndCode? = null,
        val failureCode: LanProbeDiagnostics.Code? = null,
        val approvalState: DebugApprovalGate.State? = null,
        val text: String? = null,
        val messageId: Long? = null
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
    // Audio can hold the ordinary writer for a full 3-second turn.
    // Floor REQUEST/GRANT/DENY/CANCEL cannot safely queue behind it.
    private val floorWriter = Executors.newSingleThreadExecutor { action ->
        Thread(action, "eden-demo-floor").apply { isDaemon = true }
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
    // These frames still use sendPacket() -> the same outputLock and mutual
    // TLS output stream. Only the scheduling lane is independent of PCM.
    private val floorControls = DebugLanFloorControlQueue(
        executor = floorWriter,
        write = { frame ->
            if (!approvalGate.maySendPreset())
                throw SocketException("Voice floor approval is no longer active")
            sendPacket(TestPacketCodec.debugVoiceFloor(frame))
        },
        onFailure = {
            emitTerminal(Phase.ENDED, LanDemoStatus.EndCode.LINK_INTERRUPTED)
            close()
        }
    )
    private val sendBudget = DemoSendBudget(2)
    private val incomingAckBudget = DemoSendBudget(2)
    private val peerAckLedger = DebugPeerAckLedger(maxOutstanding = 16)
    private val nextDebugTextId = AtomicLong(1L)
    // Reader-thread-only replay/sequence gate, reset per connected socket.
    private val debugTextReceive = DebugTextReceiveGuard()
    private val incomingVoice = DebugLanVoiceAssembler()
    private val voiceQueued = AtomicBoolean(false)
    private val nextVoiceTurn = AtomicLong(1L)
    private val peerVoiceActive = AtomicBoolean(false)
    @Volatile private var voiceFloor: DebugLanVoiceFloor? = null
    @Volatile private var liveVoice: LiveVoiceHandle? = null
    // In-memory playback consumer only. No file, history or export.
    @Volatile private var voicePlayback: ((ByteArray) -> Unit)? = null
    // The receiver calls this only after strict current-turn/sequence checks.
    // Android audio copies PCM into its own small, bounded playout queue.
    @Volatile private var liveVoicePlayback:
        ((DebugLanVoiceWireCodec.Kind, ByteArray) -> Unit)? = null
    private val heartbeatQueued = AtomicBoolean(false)
    private val approvalQueued = AtomicBoolean(false)
    private val confirmationQueued = AtomicBoolean(false)
    private val approvalGate = DebugApprovalGate()
    @Volatile private var debugConsentCommitment: ByteArray? = null
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
        val proofChallenges = if (isHost) {
            SessionProofExchange.runHost(socket.inputStream, socket.outputStream, binding)
        } else {
            SessionProofExchange.runJoin(socket.inputStream, socket.outputStream, binding)
        }
        try {
            sockets.requireOpen()
            requireStableWifi()

            // DEBUG-ONLY equality on pinned TLS plus a canonical digest of
            // QR roles/pins/nonces, per-connection proof challenges and exporter.
            // This is NOT reviewed production consent or text authorization.
            currentStep = LanProbeDiagnostics.Step.CHANNEL_BINDING
            emitProgress(LanDemoStatus.Progress.CHANNEL_MATCH)
            debugConsentCommitment = DebugTlsChannelAgreement.verify(
                socket, isPresenter = isHost, binding = binding,
                challenges = proofChallenges
            )
            sockets.requireOpen()
            requireStableWifi()
        } finally {
            proofChallenges.clear()
        }

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
        // Each authenticated LAN session gets fresh presenter arbitration.
        // No grant or previous request survives a new QR/TLS connection.
        voiceFloor = DebugLanVoiceFloor(isHost = isHost)
        ready = true
        preReadyWifiMonitor?.cancel(false)
        emit(Event(
            Phase.READY,
            "Pinned TLS 1.3, QR proof and matching RFC 9266 debug channel " +
                "binding passed on this phone. Both experimental approvals must " +
                "match this QR/challenge/TLS session before development-only " +
                "text or preset phrases; not independently reviewed authorization."
        ))
        startHeartbeat()
        // If a grant is delayed or an owner never releases, close the WHOLE
        // session. Never silently hand the floor to another microphone.
        cancellationTimer.scheduleAtFixedRate({
            if (ready && !stopped.get() &&
                voiceFloor?.timedOut(SystemClock.elapsedRealtime()) == true
            ) {
                emitTerminal(Phase.ENDED, LanDemoStatus.EndCode.LINK_INTERRUPTED)
                close()
            }
        }, 250, 250, TimeUnit.MILLISECONDS)

        // Fixed control types and a strict, bounded debug TEXT inner payload
        // inside FrameCodec. Never treat this as reviewed production messaging.
        // A debug ACK is only accepted after mutual approval and for a
        // specifically tracked outgoing text ID; it does not imply read.
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
                            requireCurrentConsentCommitment(packet)
                            val state = approvalGate.peerObserved()
                            if (state == DebugApprovalGate.State.INVALID ||
                                state == DebugApprovalGate.State.CLOSED ||
                                state == DebugApprovalGate.State.DECLINED
                            ) throw TestPacketException()
                            emitApprovalState(state)
                        }
                        TestPacketCodec.Type.DECLINE -> {
                            requireCurrentConsentCommitment(packet)
                            approvalGate.decline()
                            emitTerminal(Phase.ENDED, LanDemoStatus.EndCode.PEER_DECLINED)
                            return
                        }
                        TestPacketCodec.Type.CONFIRM -> {
                            requireCurrentConsentCommitment(packet)
                            val state = approvalGate.peerConfirmationObserved()
                            if (state == DebugApprovalGate.State.INVALID ||
                                state == DebugApprovalGate.State.CLOSED ||
                                state == DebugApprovalGate.State.DECLINED
                            ) throw TestPacketException()
                            emitApprovalState(state)
                        }
                        TestPacketCodec.Type.DEBUG_TEXT -> {
                            if (packet.text.isEmpty() ||
                                !debugTextReceive.accept(
                                    packet.messageId, approvalGate.maySendPreset()
                                )
                            ) throw TestPacketException()
                            // The peer app accepted a new validated test packet.
                            // The ACK is queued only after this event, not
                            // as proof of UI display or a human reading it.
                            emit(Event(
                                Phase.TEXT_RECEIVED, "Development text received in this app",
                                messageId = packet.messageId, text = packet.text
                            ))
                            queuePeerAppAck(packet.messageId)
                        }
                        TestPacketCodec.Type.DEBUG_ACK -> {
                            val result = peerAckLedger.acknowledge(
                                packet.messageId, approvalGate.maySendPreset()
                            )
                            when (result) {
                                DebugPeerAckLedger.Result.ACKNOWLEDGED ->
                                    emit(Event(
                                        Phase.TEXT_PEER_ACK,
                                        "Other app returned a test ACK (not a read receipt)",
                                        messageId = packet.messageId
                                    ))
                                DebugPeerAckLedger.Result.WAITING_FOR_WRITE -> Unit
                                DebugPeerAckLedger.Result.REJECT -> throw TestPacketException()
                            }
                        }
                        TestPacketCodec.Type.DEBUG_VOICE_FLOOR -> {
                            if (!approvalGate.maySendPreset()) throw TestPacketException()
                            val frame = packet.floor ?: throw TestPacketException()
                            val decision = (voiceFloor ?: throw TestPacketException())
                                .incoming(frame, SystemClock.elapsedRealtime())
                            decision.outbound?.let { response ->
                                // The response must not wait for a 3s PCM
                                // writer loop; keep it inside the same TLS
                                // socket's synchronized outputLock.
                                queueFloorControl(response)
                            }
                            when (decision.signal) {
                                DebugLanVoiceFloor.Signal.GRANTED ->
                                    emit(Event(Phase.VOICE_FLOOR_GRANTED,
                                        "Approved peer granted this microphone the floor"))
                                DebugLanVoiceFloor.Signal.DENIED ->
                                    emit(Event(Phase.VOICE_FLOOR_DENIED,
                                        "Other phone currently owns the radio channel"))
                                DebugLanVoiceFloor.Signal.NONE -> Unit
                            }
                        }
                        TestPacketCodec.Type.DEBUG_VOICE_START,
                        TestPacketCodec.Type.DEBUG_VOICE_PCM,
                        TestPacketCodec.Type.DEBUG_VOICE_END -> {
                            if (!approvalGate.maySendPreset()) throw TestPacketException()
                            val voiceFrame = packet.voice ?: throw TestPacketException()
                            try {
                                if (voiceFrame.kind == DebugLanVoiceWireCodec.Kind.START)
                                    (voiceFloor ?: throw TestPacketException()).remoteAudioStart()
                                val completed = incomingVoice.accept(voiceFrame)
                                // The existing assembler still owns strict turn
                                // ordering and replay rejection. With the F4-D
                                // listener installed, dispatch each validated
                                // frame BEFORE END (never double-play the clip).
                                if (voiceFrame.kind == DebugLanVoiceWireCodec.Kind.START) {
                                    peerVoiceActive.set(true)
                                    emit(Event(
                                        Phase.VOICE_STARTED,
                                        "Other phone started a validated live voice turn"
                                    ))
                                }
                                try {
                                    val live = liveVoicePlayback
                                    if (live != null) live(voiceFrame.kind, voiceFrame.pcm)
                                    else if (completed != null) voicePlayback?.invoke(completed)
                                    if (completed != null) emit(Event(
                                        Phase.VOICE_RECEIVED,
                                        "Validated encrypted voice turn finished in this app"
                                    ))
                                } finally {
                                    completed?.fill(0)
                                    if (voiceFrame.kind == DebugLanVoiceWireCodec.Kind.END) {
                                        (voiceFloor ?: throw TestPacketException()).remoteAudioEnd()
                                        peerVoiceActive.set(false)
                                        emit(Event(
                                            Phase.VOICE_FINISHED,
                                            "Other phone finished a validated voice turn"
                                        ))
                                    }
                                }
                            } finally { voiceFrame.clear() }
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

    /**
     * At most two peer-app acknowledgements queued on the existing TLS writer.
     * Receipt is optional; inability to queue never invents successful
     * delivery or sends data over any unverified transport.
     */
    private fun queuePeerAppAck(id: Long) {
        if (!ready || stopped.get() || ending.get() ||
            !approvalGate.maySendPreset() || !incomingAckBudget.tryAcquire()) return
        try {
            writer.execute {
                try {
                    if (!ready || stopped.get() || ending.get() ||
                        !approvalGate.maySendPreset()) return@execute
                    sendPacket(TestPacketCodec.debugAck(id))
                } catch (_: IOException) {
                    emitTerminal(Phase.ENDED, LanDemoStatus.EndCode.LINK_INTERRUPTED)
                    close()
                } finally {
                    incomingAckBudget.release()
                }
            }
        } catch (_: RuntimeException) {
            incomingAckBudget.release()
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
     * Development-build ONLY. The caller cannot enable this from a non-debug
     * application (constructor checks FLAG_DEBUGGABLE). Explicitly confirmed
     * current pinned TLS session only. This is NOT trusted personal messaging.
     *
     * A QUEUED result only means queued for the writer; the later TEXT_WRITTEN
     * callback means this phone wrote it to TLS, not peer delivery/read.
     */
    fun sendDebugText(rawText: String): TextSendResult {
        if (!ready || stopped.get() || ending.get()) return TextSendResult.NOT_CONNECTED
        if (!approvalGate.maySendPreset()) return TextSendResult.AWAITING_APPROVAL
        val clean = rawText.trim()
        if (clean.isEmpty()) return TextSendResult.INVALID_TEXT
        // Validate Unicode, control characters and byte/code-point bounds
        // before allocating a slot in the bounded outgoing queue.
        try {
            TestPacketCodec.debugText(1, clean)
        } catch (_: IOException) {
            return TextSendResult.INVALID_TEXT
        } catch (_: IllegalArgumentException) {
            return TextSendResult.INVALID_TEXT
        }
        if (!sendBudget.tryAcquire()) return TextSendResult.BUSY
        val id = nextDebugTextId.getAndIncrement()
        if (id <= 0L) {
            sendBudget.release()
            return TextSendResult.INVALID_TEXT
        }
        if (!peerAckLedger.queue(id)) {
            sendBudget.release()
            return TextSendResult.BUSY
        }
        val packet = TestPacketCodec.debugText(id, clean)
        return try {
            writer.execute {
                var didWrite = false
                try {
                    if (!ready || stopped.get() || ending.get() ||
                        !approvalGate.maySendPreset()) return@execute
                    sendPacket(packet)
                    didWrite = true
                    val arrivedEarly = peerAckLedger.written(id)
                    emit(Event(
                        Phase.TEXT_WRITTEN, "Development text written to TLS (not delivery)",
                        messageId = id, text = clean
                    ))
                    if (arrivedEarly) emit(Event(
                        Phase.TEXT_PEER_ACK,
                        "Other app returned a test ACK (not a read receipt)",
                        messageId = id
                    ))
                } catch (_: IOException) {
                    emitTerminal(Phase.ENDED, LanDemoStatus.EndCode.LINK_INTERRUPTED)
                    close()
                } finally {
                    if (!didWrite) peerAckLedger.cancel(id)
                    sendBudget.release()
                }
            }
            TextSendResult.QUEUED
        } catch (_: RuntimeException) {
            peerAckLedger.cancel(id)
            sendBudget.release()
            TextSendResult.NOT_CONNECTED
        }
    }

    /**
     * Queue an essential radio-floor control on its OWN bounded scheduler.
     * All actual wire writes still use sendPacket's one approved TLS socket
     * and outputLock, including the audio and writer-ordered RELEASE.
     *
     * Failing to enqueue a control closes the entire session; neither peer
     * may silently assume a missing request/cancel/deny was delivered.
     */
    private fun queueFloorControl(frame: DebugLanVoiceFloorCodec.Frame): Boolean {
        if (!ready || stopped.get() || ending.get() ||
            !approvalGate.maySendPreset()
        ) return false
        if (floorControls.offer(frame)) return true
        emitTerminal(Phase.ENDED, LanDemoStatus.EndCode.LINK_INTERRUPTED)
        close()
        return false
    }

    /**
     * Request the sole presenter's per-session voice floor, BEFORE microphone
     * startup. Host reserves locally; joiner waits for a correlated GRANT.
     * An unapproved/closed socket never sends REQUEST.
     */
    fun requestVoiceFloor(): FloorRequestResult {
        if (!ready || stopped.get() || ending.get()) return FloorRequestResult.NOT_CONNECTED
        if (!approvalGate.maySendPreset()) return FloorRequestResult.AWAITING_APPROVAL
        val floor = voiceFloor ?: return FloorRequestResult.NOT_CONNECTED
        return when (val requested = floor.requestLocal(SystemClock.elapsedRealtime())) {
            is DebugLanVoiceFloor.Request -> when (requested.status) {
                DebugLanVoiceFloor.Ask.BUSY -> FloorRequestResult.BUSY
                DebugLanVoiceFloor.Ask.GRANTED -> FloorRequestResult.GRANTED
                DebugLanVoiceFloor.Ask.PENDING -> {
                    val request = requested.packet ?: throw IllegalStateException()
                    try {
                        if (queueFloorControl(request)) FloorRequestResult.PENDING
                        else FloorRequestResult.NOT_CONNECTED
                    } catch (_: RuntimeException) {
                        floor.cancelPending()
                        FloorRequestResult.NOT_CONNECTED
                    }
                }
            }
        }
    }

    /** Cancel an unstarted hold/tap. A late GRANT is ignored by the joiner. */
    fun cancelUnstartedVoiceFloor() {
        val floor = voiceFloor ?: return
        val control = floor.cancelPending() ?: floor.releaseLocal()
        if (control != null && ready && !stopped.get() && !ending.get()) {
            queueFloorControl(control)
        }
    }

    /** Invoked on the SAME sole writer, immediately after the last PCM/END. */
    private fun releaseWrittenVoiceFloor() {
        val frame = voiceFloor?.releaseLocal() ?: return
        if (!ready || stopped.get() || ending.get()) return
        try { sendPacket(TestPacketCodec.debugVoiceFloor(frame)) }
        catch (_: Exception) {
            emitTerminal(Phase.ENDED, LanDemoStatus.EndCode.LINK_INTERRUPTED)
            close()
        }
    }

    /**
     * Debug-only, mutually approved 16kHz mono voice burst over this SAME
     * TLS session. A bounded 3-second clip is queued as 20ms PCM frames.
     * The writer owns an independent copy and erases it after send/abort.
     * This is NOT live full-duplex or reviewed secure audio.
     */
    fun sendVoiceBurst(input: ByteArray): VoiceSendResult {
        if (!ready || stopped.get() || ending.get()) return VoiceSendResult.NOT_CONNECTED
        if (!approvalGate.maySendPreset()) return VoiceSendResult.AWAITING_APPROVAL
        if (voiceFloor?.maySendAudio() != true) return VoiceSendResult.BUSY
        if (input.isEmpty() || input.size > DebugLanVoiceWireCodec.MAX_PCM_BYTES ||
            input.size % DebugLanVoiceWireCodec.PCM_BYTES != 0
        ) return VoiceSendResult.INVALID_AUDIO
        if (!voiceQueued.compareAndSet(false, true)) return VoiceSendResult.BUSY
        val turn = nextVoiceTurn.getAndIncrement()
        if (turn <= 0L) {
            voiceQueued.set(false)
            return VoiceSendResult.INVALID_AUDIO
        }
        val clip = input.copyOf()
        return try {
            writer.execute {
                try {
                    if (!ready || stopped.get() || ending.get() ||
                        !approvalGate.maySendPreset()) return@execute
                    sendVoiceFrame(DebugLanVoiceWireCodec.start(turn))
                    val count = clip.size / DebugLanVoiceWireCodec.PCM_BYTES
                    // F4-D receiver plays early on a tiny 240ms queue.
                    // Legacy record-then-send must now pace each buffered
                    // 20ms frame near real time instead of dumping up to 3s
                    // instantly and overflowing the bounded speaker queue.
                    val burstStartMs = SystemClock.elapsedRealtime()
                    repeat(count) { sequence ->
                        val dueMs = burstStartMs + sequence * 20L
                        val waitMs = dueMs - SystemClock.elapsedRealtime()
                        if (waitMs > 0L) Thread.sleep(waitMs)
                        if (!ready || ending.get() || stopped.get() ||
                            !approvalGate.maySendPreset()) throw SocketException("Voice test ended")
                        val pcm = clip.copyOfRange(
                            sequence * DebugLanVoiceWireCodec.PCM_BYTES,
                            (sequence + 1) * DebugLanVoiceWireCodec.PCM_BYTES
                        )
                        try {
                            sendVoiceFrame(DebugLanVoiceWireCodec.data(turn, sequence, pcm))
                        } finally { pcm.fill(0) }
                    }
                    sendVoiceFrame(DebugLanVoiceWireCodec.end(turn, count))
                    emit(Event(
                        Phase.VOICE_WRITTEN,
                        "Test voice burst written to local TLS; peer playback not confirmed"
                    ))
                } catch (_: Exception) {
                    emitTerminal(Phase.ENDED, LanDemoStatus.EndCode.LINK_INTERRUPTED)
                    close()
                } finally {
                    clip.fill(0)
                    releaseWrittenVoiceFloor()
                    voiceQueued.set(false)
                }
            }
            VoiceSendResult.QUEUED
        } catch (_: RuntimeException) {
            clip.fill(0)
            voiceQueued.set(false)
            VoiceSendResult.NOT_CONNECTED
        }
    }

    /**
     * Real foreground in-press PCM: the microphone supplies one 20ms frame
     * at a time. The pre-existing, single TLS writer sends each frame soon
     * after capture, NOT after the finger lifts. At most 12 frames queue.
     *
     * A voice control START is deliberately deferred until a real PCM frame
     * exists, so a quick tap emits no empty/invalid wire turn.
     */
    inner class LiveVoiceHandle internal constructor(
        private val queue: DebugLanLiveVoiceQueue
    ) {
        fun offerPcm(frame: ByteArray): Boolean =
            !stopped.get() && !ending.get() &&
                approvalGate.maySendPreset() && queue.offer(frame)

        fun finish() = queue.finish()

        /**
         * Aborts any pending, unsent samples. Frames already written over
         * TLS may already have been heard; END still closes that turn.
         */
        fun cancel() = queue.cancel()
    }

    data class LiveVoiceStart(
        val status: VoiceSendResult,
        val handle: LiveVoiceHandle? = null
    )

    fun startLiveVoice(): LiveVoiceStart {
        if (!ready || stopped.get() || ending.get())
            return LiveVoiceStart(VoiceSendResult.NOT_CONNECTED)
        if (!approvalGate.maySendPreset())
            return LiveVoiceStart(VoiceSendResult.AWAITING_APPROVAL)
        if (voiceFloor?.maySendAudio() != true)
            return LiveVoiceStart(VoiceSendResult.BUSY)
        // Courtesy half-duplex: don't start while we know the peer is
        // speaking. Simultaneous unobserved presses still need floor arbitration.
        if (peerVoiceActive.get() || !voiceQueued.compareAndSet(false, true))
            return LiveVoiceStart(VoiceSendResult.BUSY)
        val turn = nextVoiceTurn.getAndIncrement()
        if (turn <= 0L) {
            voiceQueued.set(false)
            return LiveVoiceStart(VoiceSendResult.INVALID_AUDIO)
        }
        val queue = DebugLanLiveVoiceQueue()
        val handle = LiveVoiceHandle(queue)
        liveVoice = handle
        return try {
            writer.execute {
                var sent = 0
                var opened = false
                try {
                    val deadline = SystemClock.elapsedRealtime() + 4_000L
                    while (!stopped.get() && !ending.get() &&
                        approvalGate.maySendPreset() &&
                        SystemClock.elapsedRealtime() < deadline
                    ) {
                        val pcm = queue.poll()
                        if (pcm != null) {
                            try {
                                if (!opened) {
                                    sendVoiceFrame(DebugLanVoiceWireCodec.start(turn))
                                    opened = true
                                }
                                sendVoiceFrame(DebugLanVoiceWireCodec.data(turn, sent, pcm))
                                sent++
                            } finally {
                                pcm.fill(0)
                            }
                            if (sent >= DebugLanVoiceWireCodec.MAX_FRAMES) break
                        } else if (queue.isFinishedAndEmpty()) break
                    }
                    // Only an actual opened turn receives END. Cancellation
                    // cannot undo already transmitted frames, but it can
                    // stop the next frame and release the remote receiver.
                    if (opened && !stopped.get() && !ending.get()) {
                        sendVoiceFrame(DebugLanVoiceWireCodec.end(turn, sent))
                        emit(Event(
                            Phase.VOICE_WRITTEN,
                            "Live voice frames sent while held; local TLS write is not peer audibility"
                        ))
                    }
                    if (queue.overran()) emit(Event(
                        Phase.PROGRESS,
                        "Voice network was busy; live test audio was cut short"
                    ))
                } catch (_: Exception) {
                    emitTerminal(Phase.ENDED, LanDemoStatus.EndCode.LINK_INTERRUPTED)
                    close()
                } finally {
                    queue.clear()
                    releaseWrittenVoiceFloor()
                    if (liveVoice === handle) liveVoice = null
                    voiceQueued.set(false)
                }
            }
            LiveVoiceStart(VoiceSendResult.QUEUED, handle)
        } catch (_: RuntimeException) {
            queue.clear()
            if (liveVoice === handle) liveVoice = null
            voiceQueued.set(false)
            LiveVoiceStart(VoiceSendResult.NOT_CONNECTED)
        }
    }

    private fun sendVoiceFrame(frame: DebugLanVoiceWireCodec.Frame) {
        try { sendPacket(TestPacketCodec.debugVoice(frame)) }
        finally { frame.clear() }
    }

    /** The legacy short-burst receiver remains available as a fallback. */
    fun setVoicePlayback(listener: ((ByteArray) -> Unit)?) {
        voicePlayback = if (!stopped.get()) listener else null
    }

    /**
     * Experimental in-progress playout on the SAME validated LAN frames.
     * Listener must copy audio promptly and must NEVER block the TLS reader.
     * This is a device-local PCM callback, not evidence of human audibility.
     */
    fun setLiveVoicePlayback(
        listener: ((DebugLanVoiceWireCodec.Kind, ByteArray) -> Unit)?
    ) {
        liveVoicePlayback = if (!stopped.get()) listener else null
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
                    val commitment = debugConsentCommitment?.copyOf()
                        ?: throw TestPacketException()
                    try {
                        sendPacket(TestPacketCodec.approve(commitment))
                    } finally {
                        commitment.fill(0)
                    }
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
        val commitment = debugConsentCommitment?.copyOf()
        if (!ready || stopped.get() || commitment == null) {
            commitment?.fill(0)
            close()
            return
        }
        try {
            stopWithControl(TestPacketCodec.decline(commitment))
        } finally {
            commitment.fill(0)
        }
    }

    private fun emitApprovalState(state: DebugApprovalGate.State) {
        when (state) {
            DebugApprovalGate.State.CONFIRMED ->
                emit(Event(Phase.APPROVAL_COMPLETE,
                    "Both approvals and both final confirmations observed here; preset test only.",
                    approvalState = state))
            DebugApprovalGate.State.LOCAL_ONLY,
            DebugApprovalGate.State.PEER_ONLY,
            DebugApprovalGate.State.BOTH_OBSERVED,
            DebugApprovalGate.State.LOCAL_CONFIRMED,
            DebugApprovalGate.State.PEER_CONFIRMED ->
                emit(Event(Phase.APPROVAL_UPDATE,
                    "Waiting for both approvals and two session confirmations.",
                    approvalState = state))
            else -> Unit
        }
        queueFinalConfirmationIfReady()
    }

    /** Auto-send exactly one session-bound final control after both taps. */
    private fun queueFinalConfirmationIfReady() {
        if (!ready || stopped.get() || ending.get() ||
            !approvalGate.mayWriteConfirmation()
        ) return
        if (!confirmationQueued.compareAndSet(false, true)) return
        try {
            writer.execute {
                try {
                    if (!ready || stopped.get() || ending.get() ||
                        !approvalGate.mayWriteConfirmation()
                    ) return@execute
                    val commitment = debugConsentCommitment?.copyOf()
                        ?: throw TestPacketException()
                    try {
                        sendPacket(TestPacketCodec.confirm(commitment))
                    } finally {
                        commitment.fill(0)
                    }
                    val state = approvalGate.localConfirmationWritten()
                    if (state == DebugApprovalGate.State.INVALID ||
                        state == DebugApprovalGate.State.CLOSED ||
                        state == DebugApprovalGate.State.DECLINED
                    ) throw TestPacketException()
                    emitApprovalState(state)
                } catch (_: IOException) {
                    emitTerminal(Phase.ENDED, LanDemoStatus.EndCode.LINK_INTERRUPTED)
                    close()
                } finally {
                    confirmationQueued.set(false)
                }
            }
        } catch (_: RuntimeException) {
            confirmationQueued.set(false)
            emitTerminal(Phase.ENDED, LanDemoStatus.EndCode.LINK_INTERRUPTED)
            close()
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

    /** Reject controls from any other session, even on a pinned TLS socket. */
    private fun requireCurrentConsentCommitment(packet: TestPacketCodec.Packet) {
        val local = debugConsentCommitment ?: throw TestPacketException()
        if (!DebugApprovalCommitment.matches(
                local, packet.sessionCommitment.toByteArray()
            )
        ) throw TestPacketException()
    }

    private fun sendPacket(packet: TestPacketCodec.Packet) {
        synchronized(outputLock) {
            // The writer may have passed its earlier ending check before
            // the user tapped Disconnect. Recheck immediately at the shared
            // socket boundary, without blocking the UI on outputLock.
            // Only best-effort CLOSE/DECLINE controls survive local teardown.
            if (!ready || stopped.get() ||
                !DebugSessionTeardownPolicy.mayWrite(ending.get(), packet.type)
            ) throw SocketException("Test already closed")
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
                // Recompute immediately before the blocking connect: the
                // final attempt must not start a full 1.5-second wait after
                // almost all of the two-minute pre-TLS budget was consumed.
                val connectTimeoutMs = if (retryWhileWaiting) {
                    LanConnectRetryPolicy.attemptTimeoutMs(
                        SystemClock.elapsedRealtime() - started
                    )
                } else CONNECT_TIMEOUT_MS
                // Socket.connect(timeout=0) means an UNBOUNDED wait in Java.
                // A zero budget is terminal, never passed into the socket.
                if (connectTimeoutMs <= 0) {
                    throw SocketTimeoutException("Local test connection retry window ended")
                }
                raw.connect(InetSocketAddress(peer, PORT), connectTimeoutMs)
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
        // Never add a stale SENT/APPROVAL/READY event while a voluntary
        // disconnect is already in progress.
        if (DebugSessionTeardownPolicy.mayReportActivity(
                stopped.get(), terminal.get(), ending.get()
            )) report(event)
    }

    private fun emitTerminal(phase: Phase, endCode: LanDemoStatus.EndCode) {
        // If the owner has ended or declined, a pending writer being closed
        // must not overwrite that decision with LINK_INTERRUPTED.
        if (DebugSessionTeardownPolicy.mayReportUnexpectedEnd(
                stopped.get(), ending.get()
            ) && terminal.compareAndSet(false, true)) {
            report(Event(phase, endCode.display, endCode = endCode))
        }
    }

    private fun emitTerminal(phase: Phase, failure: LanProbeDiagnostics.Failure) {
        if (DebugSessionTeardownPolicy.mayReportUnexpectedEnd(
                stopped.get(), ending.get()
            ) && terminal.compareAndSet(false, true)) {
            report(Event(phase, failure.display(), failureCode = failure.code))
        }
    }

    override fun close() {
        if (!stopped.compareAndSet(false, true)) return
        ready = false
        ending.set(true)
        approvalGate.close()
        debugConsentCommitment?.fill(0)
        debugConsentCommitment = null
        peerAckLedger.close()
        liveVoice?.cancel()
        liveVoice = null
        peerVoiceActive.set(false)
        voiceFloor?.close()
        voiceFloor = null
        incomingVoice.clear()
        voicePlayback = null
        liveVoicePlayback = null
        sockets.close()
        preReadyWifiMonitor?.cancel(false)
        pulses.shutdownNow()
        cancellationTimer.shutdownNow()
        floorWriter.shutdownNow()
        writer.shutdownNow()
        worker.shutdownNow()
    }
}
