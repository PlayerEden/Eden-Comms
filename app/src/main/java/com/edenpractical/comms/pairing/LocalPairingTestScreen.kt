package com.edenpractical.comms.pairing

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.provider.Settings
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.edenpractical.comms.privacy.ContextualPrivacyHelp
import com.edenpractical.comms.privacy.ConversationPrivacyContent
import com.edenpractical.comms.security.AndroidKeystoreIdentity
import com.edenpractical.comms.security.LocalTlsIdentity
import com.edenpractical.comms.security.SessionProofBinding
import com.edenpractical.comms.protocol.TestPacketCodec
import com.edenpractical.comms.transport.DebugApprovalGate
import com.edenpractical.comms.transport.LanDemoStatus
import com.edenpractical.comms.transport.LanProbeDiagnostics
import com.edenpractical.comms.transport.LanDemoSession
import com.edenpractical.comms.transport.LanWifiChangedException
import com.edenpractical.comms.transport.WifiLinkContinuity
import com.edenpractical.comms.transport.LocalIpv4Policy
import com.edenpractical.comms.transport.WifiLanEndpoint
import com.edenpractical.comms.ui.FieldFrame
import com.edenpractical.comms.ui.FieldHelp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Guided two-QR LOCAL CONNECTION TEST. The second QR is still required:
 * it authenticates the joiner's identity to the presenter.
 *
 * No manual listener, remembered peers, cloud, background discovery or
 * plaintext fallback. Debug builds can exchange four presets and bounded
 * ephemeral typed text on an authenticated test socket ONLY after both QR
 * proofs, matching TLS binding, approvals and confirmations. No voice.
 * This is unreviewed development functionality, not safe private messaging.
 */
@Composable
internal fun LocalPairingTestScreen(
    interruptedBeforeRecreation: Boolean,
    onStageChanged: (String) -> Unit,
    onBackToPrevious: () -> Unit
) {
    val context = LocalContext.current
    if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) {
        Text("Local pairing tests are available only in development builds.")
        return
    }
    val installedBuildEvidence = remember(context) { InstalledBuildEvidence.read(context) }
    val repository = remember { AndroidKeystoreIdentity() }
    val scope = rememberCoroutineScope()
    val handler = remember { Handler(Looper.getMainLooper()) }

    var identity by remember { mutableStateOf<LocalTlsIdentity?>(null) }
    var signingStatus by remember {
        mutableStateOf(AndroidKeystoreIdentity.TlsSigningStatus.NOT_CREATED)
    }
    var wifi by remember { mutableStateOf<WifiLanEndpoint.Link?>(null) }
    var stage by remember { mutableStateOf("checking") }
    var status by remember { mutableStateOf("Checking this phone…") }
    // This only explains a restored navigation route. No previous socket,
    // peer, QR or text is restored to the new Activity instance.
    var showRecreationNotice by remember { mutableStateOf(interruptedBeforeRecreation) }
    var details by remember { mutableStateOf("") }
    var showDetails by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var showIdentityConsent by remember { mutableStateOf(false) }
    var showPairingHelp by remember { mutableStateOf(false) }
    var showBuildEvidence by remember { mutableStateOf(false) }
    var showResultDetails by remember { mutableStateOf(false) }
    var showUpgradeConfirmation by remember { mutableStateOf(false) }
    var pendingRole by remember { mutableStateOf<String?>(null) }
    var hostInvite by remember { mutableStateOf<LocalPairingQrCodec.HostInvite?>(null) }
    var joinReply by remember { mutableStateOf<LocalPairingQrCodec.JoinReply?>(null) }
    var peerPin by remember { mutableStateOf<ByteArray?>(null) }
    var qrText by remember { mutableStateOf("") }
    var scanRevision by remember { mutableIntStateOf(0) }
    var attemptVersion by remember { mutableIntStateOf(0) }
    var probe by remember { mutableStateOf<LanDemoSession?>(null) }
    // Never saveable: this in-memory ledger is scoped to this composable.
    // No user-supplied text or persistent message history can enter it.
    var transcript by remember { mutableStateOf(DebugSessionTranscript.fresh()) }
    // Session-only chat content; never rememberSaveable or persistent.
    var debugText by remember { mutableStateOf(DebugTextTranscript.fresh()) }
    // A returned peer-app ACK can race ahead of TEXT_WRITTEN's main-thread
    // callback. Store only a bounded set of IDs, never message content.
    var ackedTextIds by remember { mutableStateOf(emptySet<Long>()) }
    // Event count only: no text, peer identity, QR, or ACK IDs exported.
    // Keep after stopTest for result QA; reset only on a fresh pairing attempt.
    var qaPeerAppAckEvents by remember { mutableIntStateOf(0) }
    var textDraft by remember { mutableStateOf("") }
    var privacyHidden by remember { mutableStateOf(false) }
    var showClearConfirmation by remember { mutableStateOf(false) }
    var showTestTools by remember { mutableStateOf(false) }
    var qaRole by remember { mutableStateOf(LocalQaReport.Role.NOT_SELECTED) }
    var qaFinish by remember { mutableStateOf(LocalQaReport.Finish.NOT_RECORDED) }
    var qaLastCheckpoint by remember { mutableStateOf<LanDemoStatus.Progress?>(null) }
    var qaEndCode by remember { mutableStateOf<LanDemoStatus.EndCode?>(null) }
    var qaFailureCode by remember { mutableStateOf<LanProbeDiagnostics.Code?>(null) }
    var approvalStage by remember { mutableStateOf(DebugApprovalGate.State.WAITING) }
    var approvalSending by remember { mutableStateOf(false) }
    var qaTlsReady by remember { mutableStateOf(false) }
    var qaLocalApprovalWritten by remember { mutableStateOf(false) }
    var qaPeerApprovalObserved by remember { mutableStateOf(false) }
    var qaLocalConfirmationWritten by remember { mutableStateOf(false) }
    var qaPeerConfirmationObserved by remember { mutableStateOf(false) }
    val latestProbe by rememberUpdatedState(probe)
    LaunchedEffect(stage) {
        // Observational UI only: do not pass peer identity, messages or pins
        // into app-level navigation state.
        onStageChanged(stage)
    }

    fun stopTest() {
        // Ignore messages posted by previous or cancelled attempts.
        attemptVersion += 1
        probe?.close()
        probe = null
        debugText = debugText.finish()
        ackedTextIds = emptySet()
        textDraft = ""
        privacyHidden = false
        showClearConfirmation = false
    }

    fun wifiIsOriginalPairingLink(): Boolean {
        val initial = wifi ?: return false
        val now = WifiLanEndpoint.current(context)
        return WifiLinkContinuity.unchanged(
            initial.network, initial.address, initial.prefixLength,
            now?.network, now?.address, now?.prefixLength
        )
    }

    fun clearTemporaryPairing() {
        // QR fingerprints/nonces are public but privacy-sensitive metadata.
        // Drop Compose references when no longer needed (not secure erase).
        hostInvite = null
        joinReply = null
        peerPin = null
        qrText = ""
    }

    fun refreshWifiStatus() {
        // No pairing, network switch, radio discovery or auto-connect here.
        // Never expose address, SSID or raw ConnectivityManager errors.
        wifi = try { WifiLanEndpoint.current(context) } catch (_: Exception) { null }
    }

    fun openWifiSettings() {
        // User-directed system settings only; returning must NOT continue a
        // pairing attempt or reuse any QR/certificate pin.
        try {
            context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        } catch (_: Exception) {
            status = "Couldn't open Wi-Fi settings. Open the phone's Settings manually."
        }
    }

    fun reset() {
        showRecreationNotice = false
        stopTest()
        clearTemporaryPairing()
        details = ""
        showDetails = false
        showTestTools = false
        showPairingHelp = false
        showBuildEvidence = false
        showResultDetails = false
        showIdentityConsent = false
        showUpgradeConfirmation = false
        cameraError = false
        // A stale camera callback must not be accepted by the next QR scan.
        scanRevision += 1
        transcript = DebugSessionTranscript.fresh()
        debugText = DebugTextTranscript.fresh()
        ackedTextIds = emptySet()
        qaPeerAppAckEvents = 0
        textDraft = ""
        privacyHidden = false
        showClearConfirmation = false
        qaRole = LocalQaReport.Role.NOT_SELECTED
        qaFinish = LocalQaReport.Finish.NOT_RECORDED
        qaLastCheckpoint = null
        qaEndCode = null
        qaFailureCode = null
        approvalStage = DebugApprovalGate.State.WAITING
        approvalSending = false
        qaTlsReady = false
        qaLocalApprovalWritten = false
        qaPeerApprovalObserved = false
        qaLocalConfirmationWritten = false
        qaPeerConfirmationObserved = false
        pendingRole = null
        refreshWifiStatus()
        stage = "choose"
        status = if (wifi == null) "Connect both phones to the same Wi-Fi."
            else "Choose how to begin."
    }

    fun startRole(role: String) {
        showRecreationNotice = false
        // The buttons are disabled while loading; continuation after explicit
        // identity consent also needs to enter this flow before loading clears.
        pendingRole = role
        if (identity == null) {
            showIdentityConsent = true
            return
        }
        if (signingStatus == AndroidKeystoreIdentity.TlsSigningStatus.LEGACY_SHA256_ONLY) {
            showUpgradeConfirmation = true
            return
        }
        try {
            val link = WifiLanEndpoint.current(context) ?: error("Wi-Fi unavailable")
            wifi = link
            qaRole = if (role == "show") LocalQaReport.Role.PRESENTER
                else LocalQaReport.Role.JOINER
            if (role == "show") {
                val invite = LocalPairingQrCodec.newHostInvite(
                    link.address.hostAddress ?: error("Wi-Fi address unavailable"),
                    identity!!.publicFingerprint()
                )
                hostInvite = invite
                joinReply = null
                peerPin = null
                qrText = LocalPairingQrCodec.encodeHost(invite)
                stage = "showHost"
                status = "Ask the other person to scan this code."
            } else {
                hostInvite = null
                joinReply = null
                peerPin = null
                qrText = ""
                scanRevision += 1
                cameraError = false
                stage = "scanHost"
                status = "Point your camera at the code on their phone."
            }
        } catch (_: Exception) {
            clearTemporaryPairing()
            refreshWifiStatus()
            stage = "choose"
            status = "Connect both phones to the same Wi-Fi and try again."
        }
    }

    fun runProbe(asPresenter: Boolean) {
        val own = identity
        val expected = peerPin
        val invite = hostInvite
        val reply = joinReply
        if (own == null || expected == null || invite == null || reply == null) {
            // This is a failed preflight, not a resumable QR flow. Do not
            // retain a partial invite, peer pin or old debug transcript.
            stopTest()
            clearTemporaryPairing()
            qaFinish = LocalQaReport.Finish.CONNECTION_FAILED
            qaEndCode = null
            qaFailureCode = null
            qaTlsReady = false
            approvalStage = DebugApprovalGate.State.WAITING
            approvalSending = false
            transcript = transcript.finish()
            debugText = debugText.finish()
            details = "Pairing was incomplete; no test connection was started."
            stage = "result"
            status = "Pairing was incomplete. Start again with fresh codes."
            return
        }
        try {
            if (!wifiIsOriginalPairingLink()) throw LanWifiChangedException()
            LocalPairingQrCodec.requireFresh(invite, System.currentTimeMillis() / 1000)
            val binding = SessionProofBinding(
                invite.certificateSha256, reply.certificateSha256, invite.nonce, reply.nonce
            )
            val ownPin = if (asPresenter) invite.certificateSha256 else reply.certificateSha256
            require(own.publicFingerprint().contentEquals(ownPin)) {
                "Local identity changed"
            }
            if (!asPresenter) {
                val link = WifiLanEndpoint.current(context) ?: error("Wi-Fi lost")
                LocalIpv4Policy.requireSamePrivateSubnet(
                    invite.wifiIp, link.address, link.prefixLength
                )
            }
            stopTest()
            transcript = DebugSessionTranscript.fresh()
            debugText = DebugTextTranscript.fresh()
            ackedTextIds = emptySet()
            qaPeerAppAckEvents = 0
            textDraft = ""
            privacyHidden = false
            showClearConfirmation = false
            showTestTools = false
            qaLastCheckpoint = null
            qaEndCode = null
            qaFailureCode = null
            approvalStage = DebugApprovalGate.State.WAITING
            approvalSending = false
            qaTlsReady = false
            qaLocalApprovalWritten = false
            qaPeerApprovalObserved = false
            qaLocalConfirmationWritten = false
            qaPeerConfirmationObserved = false
            val thisAttempt = attemptVersion
            val connection = LanDemoSession(context.applicationContext) { event ->
                handler.post {
                    if (!DebugSessionEventAdmission.current(
                            thisAttempt, attemptVersion
                        )) return@post
                    when (event.phase) {
                        LanDemoSession.Phase.LISTENING -> {
                            if (DebugSessionEventAdmission.canAcceptReady(
                                    thisAttempt, attemptVersion, stage, transcript
                                )) status = "Waiting for the other phone to connect…"
                        }
                        LanDemoSession.Phase.CONNECTING -> {
                            if (DebugSessionEventAdmission.canAcceptReady(
                                    thisAttempt, attemptVersion, stage, transcript
                                )) status = "Connecting to the other phone over local Wi-Fi…"
                        }
                        LanDemoSession.Phase.PROGRESS -> {
                            // These labels describe the current operation,
                            // never a verified connection or user approval.
                            if (DebugSessionEventAdmission.canAcceptReady(
                                    thisAttempt, attemptVersion, stage, transcript
                                )) {
                                event.progress?.let { progress ->
                                    qaLastCheckpoint = progress
                                    status = progress.display
                                }
                            }
                        }
                        LanDemoSession.Phase.READY -> {
                            // A delayed READY can arrive after an END from a
                            // different socket thread. Never reopen the UI.
                            if (!DebugSessionEventAdmission.canAcceptReady(
                                    thisAttempt, attemptVersion, stage, transcript
                                )) return@post
                            qaTlsReady = true
                            clearTemporaryPairing()
                            details = event.description
                            status = "Test TLS/QR link ready. Both people must explicitly approve."
                            stage = "approval"
                        }
                        LanDemoSession.Phase.APPROVAL_UPDATE -> {
                            if (stage != "approval" ||
                                transcript.state != DebugSessionTranscript.State.WAITING
                            ) return@post
                            event.approvalState?.let { observation ->
                                approvalStage = observation
                                approvalSending = false
                                when (observation) {
                                    DebugApprovalGate.State.LOCAL_ONLY -> {
                                        qaLocalApprovalWritten = true
                                        status = "Your approval was written to TLS. Waiting for the other phone."
                                    }
                                    DebugApprovalGate.State.PEER_ONLY -> {
                                        qaPeerApprovalObserved = true
                                        status = "The other phone approved. Your approval is still needed."
                                    }
                                    DebugApprovalGate.State.BOTH_OBSERVED -> {
                                        qaLocalApprovalWritten = true
                                        qaPeerApprovalObserved = true
                                        status = "Both debug approvals observed. Exchanging final confirmations…"
                                    }
                                    DebugApprovalGate.State.LOCAL_CONFIRMED -> {
                                        qaLocalApprovalWritten = true
                                        qaPeerApprovalObserved = true
                                        qaLocalConfirmationWritten = true
                                        status = "This phone confirmed the session. Waiting for the other phone."
                                    }
                                    DebugApprovalGate.State.PEER_CONFIRMED -> {
                                        qaLocalApprovalWritten = true
                                        qaPeerApprovalObserved = true
                                        qaPeerConfirmationObserved = true
                                        status = "The peer confirmed the session. Waiting for this phone."
                                    }
                                    else -> Unit
                                }
                            }
                        }
                        LanDemoSession.Phase.APPROVAL_COMPLETE -> {
                            if (stage != "approval" ||
                                transcript.state != DebugSessionTranscript.State.WAITING ||
                                event.approvalState != DebugApprovalGate.State.CONFIRMED
                            ) return@post
                            approvalStage = DebugApprovalGate.State.CONFIRMED
                            qaLocalApprovalWritten = true
                            qaPeerApprovalObserved = true
                            qaLocalConfirmationWritten = true
                            qaPeerConfirmationObserved = true
                            approvalSending = false
                            transcript = transcript.ready()
                            debugText = debugText.ready()
                            privacyHidden = false
                            showClearConfirmation = false
                            stage = "demo"
                            status = "Debug session confirmed. Presets and short test text available."
                        }
                        LanDemoSession.Phase.SENT, LanDemoSession.Phase.RECEIVED -> {
                            // Writer/reader callbacks may race with disconnect;
                            // only record known phrase IDs in the active attempt.
                            if (!DebugSessionEventAdmission.canRecord(
                                    thisAttempt, attemptVersion, stage, transcript
                                )) return@post
                            event.phraseId?.let { id ->
                                transcript = transcript.record(
                                    if (event.phase == LanDemoSession.Phase.SENT)
                                        DebugSessionTranscript.Direction.WRITTEN_TO_TLS
                                    else DebugSessionTranscript.Direction.RECEIVED_HERE,
                                    id
                                )
                            }
                        }
                        LanDemoSession.Phase.TEXT_WRITTEN, LanDemoSession.Phase.TEXT_RECEIVED -> {
                            if (!DebugSessionEventAdmission.canRecord(
                                    thisAttempt, attemptVersion, stage, transcript
                                ) || debugText.state != DebugTextTranscript.State.ACTIVE
                            ) return@post
                            val text = event.text ?: return@post
                            val messageId = event.messageId ?: return@post
                            debugText = debugText.record(
                                if (event.phase == LanDemoSession.Phase.TEXT_WRITTEN)
                                    DebugTextTranscript.Direction.WRITTEN_TO_TLS
                                else DebugTextTranscript.Direction.RECEIVED_HERE,
                                messageId, text
                            )
                        }
                        LanDemoSession.Phase.TEXT_PEER_ACK -> {
                            if (!DebugSessionEventAdmission.canRecord(
                                    thisAttempt, attemptVersion, stage, transcript
                                ) || debugText.state != DebugTextTranscript.State.ACTIVE
                            ) return@post
                            val id = event.messageId ?: return@post
                            if (id > 0L) {
                                // Only this attempt\'s actual peer-app ACK callback is counted.
                                // Never infer display, reading, or delivery from this event.
                                if (qaPeerAppAckEvents < 10_000) qaPeerAppAckEvents += 1
                                // Bound UI-only IDs even if peer app responds
                                // before this thread observes the write bubble.
                                ackedTextIds = (ackedTextIds + id)
                                    .toList().takeLast(16).toSet()
                            }
                        }
                        LanDemoSession.Phase.ENDED -> {
                            if (!DebugSessionEventAdmission.canAcceptTerminal(
                                    thisAttempt, attemptVersion, stage, transcript
                                )) return@post
                            // Apply the first valid terminal event only. Close
                            // and advance the attempt before writing result
                            // state so already-posted events cannot replay it.
                            stopTest()
                            qaFinish = LocalQaReport.Finish.REMOTE_OR_LINK_STOPPED
                            qaEndCode = event.endCode
                            qaFailureCode = null
                            clearTemporaryPairing()
                            details = event.description
                            status = "Test connection ended."
                            transcript = transcript.finish()
                            debugText = debugText.finish()
                            textDraft = ""
                            stage = "result"
                        }
                        LanDemoSession.Phase.FAILED -> {
                            if (!DebugSessionEventAdmission.canAcceptTerminal(
                                    thisAttempt, attemptVersion, stage, transcript
                                )) return@post
                            stopTest()
                            qaFinish = LocalQaReport.Finish.CONNECTION_FAILED
                            qaFailureCode = event.failureCode
                            qaEndCode = null
                            clearTemporaryPairing()
                            details = event.description
                            status = "Couldn't connect. Check both phones are on the same Wi-Fi."
                            transcript = transcript.finish()
                            debugText = debugText.finish()
                            textDraft = ""
                            stage = "result"
                        }
                    }
                }
            }
            probe = connection
            // Keep reply QR visible while the other phone scans it.
            stage = if (asPresenter) "connecting" else "showReply"
            status = if (asPresenter) "Waiting for the other phone to connect…"
                else "Show this code to the other phone. Connecting automatically…"
            if (asPresenter) connection.host(own, expected, binding)
            else connection.join(
                own, expected, invite.wifiIp, binding, retryWhileWaiting = true
            )
        } catch (e: Exception) {
            qaFinish = LocalQaReport.Finish.CONNECTION_FAILED
            stopTest()
            clearTemporaryPairing()
            stage = "result"
            transcript = transcript.finish()
            debugText = debugText.finish()
            textDraft = ""
            if (e is LanWifiChangedException) {
                qaFailureCode = LanProbeDiagnostics.Code.WIFI_CHANGED
                details = "Wi-Fi changed during pairing. Restart both phones' tests with fresh QR codes."
            } else {
                details = "The codes may be invalid or expired. Verify both phones and start with fresh QR codes."
            }
            status = "Couldn't start the test. Start again with fresh codes."
        }
    }

    LaunchedEffect(Unit) {
        try {
            val state = withContext(Dispatchers.IO) {
                Triple(repository.existing(), WifiLanEndpoint.current(context),
                    repository.tlsSigningStatus())
            }
            identity = state.first
            wifi = state.second
            signingStatus = state.third
            stage = "choose"
            status = if (wifi == null) "Connect both phones to the same Wi-Fi."
                else "Choose how to begin."
        } catch (_: Exception) {
            stage = "identityError"
            status = "This phone couldn't open its local identity."
        }
    }

    // Stop test sockets if the user leaves the app, including during retries.
    // Launching a camera permission dialog normally does not stop the Activity.
    val lifecycleOwner = context as? LifecycleOwner
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME &&
                stage in listOf("choose", "result")
            ) {
                // Reflect Wi-Fi changes after returning from system Settings.
                // Never mutate the link captured by an active QR handshake.
                refreshWifiStatus()
                if (stage == "choose") {
                    status = if (wifi == null) "Connect both phones to the same Wi-Fi."
                        else "Choose how to begin."
                }
            }
            if (event == Lifecycle.Event.ON_STOP && stage == "choose") {
                // Consent/identity work can finish off the main thread after
                // the app is backgrounded. It may update the local identity,
                // but must never resume the old role or open QR setup.
                attemptVersion += 1
                pendingRole = null
                showIdentityConsent = false
                showUpgradeConfirmation = false
            }
            if (event == Lifecycle.Event.ON_STOP &&
                stage !in listOf("checking", "choose", "result", "identityError")
            ) {
                val hadConnected = stage == "demo"
                qaFinish = LocalQaReport.Finish.APP_BACKGROUNDED
                stopTest()
                clearTemporaryPairing()
                transcript = transcript.finish()
                debugText = debugText.finish()
                textDraft = ""
                details = "The app went into the background, closing the local test socket."
                status = if (hadConnected) "Test connection ended when the app left the screen."
                    else "Connection cancelled. Start again when both phones are ready."
                stage = "result"
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
            // Invalidate already posted callbacks before closing the socket.
            attemptVersion += 1
            latestProbe?.close()
            clearTemporaryPairing()
            transcript = transcript.finish()
            debugText = debugText.finish()
            textDraft = ""
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            if (stage == "demo") "Nearby conversation" else "Connect nearby",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            if (stage == "demo") "Temporary two-way chat • Unreviewed development test"
            else "Connection test only • Both phones need the same Wi-Fi. No internet required.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FieldFrame(accent = true) {
            Text(
                "LOCAL STATUS  •  " + PairingSessionUi.statusLabel(stage),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            val guidance = NearbyConnectionCues.forStage(
                stage = stage,
                localApprovalObserved = qaLocalApprovalWritten ||
                    approvalStage == DebugApprovalGate.State.LOCAL_ONLY,
                peerApprovalObserved = qaPeerApprovalObserved ||
                    approvalStage == DebugApprovalGate.State.PEER_ONLY,
                localConfirmationObserved = qaLocalConfirmationWritten,
                peerConfirmationObserved = qaPeerConfirmationObserved
            )
            if (guidance != null) {
                Text(
                    guidance.phase,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (stage != "demo") {
                    Text(guidance.heading,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text(guidance.next,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                PairingTestUiCopy.progress(stage)?.let { progress ->
                    Text(progress, style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            Text(status, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            when (stage) {
                "checking" -> CircularProgressIndicator(modifier = Modifier.size(28.dp))
                "choose" -> {
                    if (showRecreationNotice) {
                        Text(
                            "Android recreated this screen. Any previous QR pairing or " +
                                "test conversation on this phone has ended. Nothing was " +
                                "reconnected or restored; start both phones again with " +
                                "fresh QR codes.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        "One phone shows a code. The other scans it.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        NearbyRecoveryGuide.wifiStatus(wifi != null),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (wifi == null) {
                        Text(
                            "Wi-Fi must be connected on both phones. You can use a router " +
                                "without internet. Avoid guest networks that isolate devices.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedButton(
                            onClick = { openWifiSettings() },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) { Text("Open Wi-Fi settings") }
                        OutlinedButton(
                            onClick = {
                                refreshWifiStatus()
                                status = if (wifi == null) "Wi-Fi is still unavailable."
                                    else "Wi-Fi found here. Choose a role on both phones."
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) { Text("Check Wi-Fi again") }
                    } else {
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val stack = PairingTestUiCopy.useStackedActions(
                                maxWidth.value, LocalDensity.current.fontScale)
                            if (stack) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { startRole("show") },
                                        enabled = !loading,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                        shape = CutCornerShape(6.dp)
                                    ) { Text("Show a code") }
                                    OutlinedButton(
                                        onClick = { startRole("scan") },
                                        enabled = !loading,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                        shape = CutCornerShape(6.dp)
                                    ) { Text("Scan a code") }
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = { startRole("show") },
                                        enabled = !loading,
                                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                                        shape = CutCornerShape(6.dp)
                                    ) { Text("Show a code") }
                                    OutlinedButton(
                                        onClick = { startRole("scan") },
                                        enabled = !loading,
                                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                                        shape = CutCornerShape(6.dp)
                                    ) { Text("Scan a code") }
                                }
                            }
                        }
                    }
                    if (loading) CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    FieldHelp(
                        question = "Which button should I choose?",
                        answer = "If your phone is displaying the code, the other person " +
                            "taps Scan a code. If their phone is displaying a code, " +
                            "you tap Scan a code. Either person can start. Keep both " +
                            "apps open and use the same Wi-Fi network."
                    )
                    TextButton(
                        onClick = { showPairingHelp = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text("?  Why scan two codes?") }
                }
                "showHost" -> {
                    LocalPairingQrDisplay(qrText)
                    Button(
                        onClick = {
                            scanRevision += 1
                            cameraError = false
                            stage = "scanReply"
                            status = "Now scan the code shown on the other phone."
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) { Text("Scan their code") }
                    TextButton(onClick = { reset() }) { Text("Cancel") }
                }
                "scanHost", "scanReply" -> {
                    key(scanRevision) {
                        // Scanner callbacks can be delivered after a camera
                        // revision or pairing attempt has already been replaced.
                        val scanAttempt = attemptVersion
                        val scannerRevision = scanRevision
                        LocalPairingQrScanner(
                            onPayload = { payload ->
                                if (PairingRestartAdmission.mayAcceptScan(
                                        scanAttempt, attemptVersion,
                                        scannerRevision, scanRevision, stage
                                    )) try {
                                    if (stage == "scanHost") {
                                        if (!wifiIsOriginalPairingLink()) throw LanWifiChangedException()
                                        val invite = LocalPairingQrCodec.decodeHost(payload)
                                        val link = WifiLanEndpoint.current(context)
                                            ?: error("Wi-Fi unavailable")
                                        LocalIpv4Policy.requireSamePrivateSubnet(
                                            invite.wifiIp, link.address, link.prefixLength
                                        )
                                        val reply = LocalPairingQrCodec.newJoinReply(
                                            invite, identity!!.publicFingerprint()
                                        )
                                        hostInvite = invite
                                        joinReply = reply
                                        peerPin = invite.certificateSha256
                                        qrText = LocalPairingQrCodec.encodeJoin(reply)
                                        stage = "showReply"
                                        runProbe(false)
                                    } else if (stage == "scanReply") {
                                        if (!wifiIsOriginalPairingLink()) throw LanWifiChangedException()
                                        val invite = hostInvite ?: error("No active invitation")
                                        val reply = LocalPairingQrCodec.decodeJoin(payload, invite)
                                        joinReply = reply
                                        peerPin = reply.certificateSha256
                                        stage = "connecting"
                                        runProbe(true)
                                    }
                                } catch (e: LanWifiChangedException) {
                                    // Never reuse a QR/pin across a different
                                    // network after roaming.
                                    reset()
                                    wifi = WifiLanEndpoint.current(context)
                                    status = "Wi-Fi changed. Start pairing again with fresh codes."
                                } catch (_: Exception) {
                                    status = "That code didn't work. Try scanning again."
                                    scanRevision += 1
                                }
                            },
                            onCameraError = {
                                // CameraX may report a late failure after a
                                // user cancels; never overwrite another screen.
                                if (PairingRestartAdmission.mayAcceptScan(
                                        scanAttempt, attemptVersion,
                                        scannerRevision, scanRevision, stage
                                    )) {
                                    cameraError = true
                                    status = it
                                }
                            }
                        )
                    }
                    if (cameraError) {
                        OutlinedButton(
                            onClick = {
                                cameraError = false
                                scanRevision += 1
                                status = "Restarting the QR camera…"
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) { Text("Restart camera") }
                    }
                    TextButton(onClick = { reset() }) { Text("Cancel") }
                }
                "showReply" -> {
                    LocalPairingQrDisplay(qrText)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Connecting automatically. Keep this code visible…",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { reset() }) { Text("Cancel test") }
                }
                "connecting" -> {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    TextButton(onClick = { reset() }) { Text("Cancel test") }
                }
                "approval" -> {
                    Text("Approve this test connection?", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Only approve if the person holding the other phone is who " +
                            "you intended to reach. You can decline at any time.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "UNREVIEWED DEBUG TEST • After both phones approve and confirm, " +
                            "short typed TEST messages and preset phrases can be exchanged. " +
                            "Never send secrets. Voice stays disabled.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FieldHelp(
                        question = "What did Eden Comms check?",
                        answer = "The test checked the scanned QR identities, pinned TLS " +
                            "and matching session values. Those are technical checks, " +
                            "not proof of who is holding the other phone or an independent " +
                            "security certification. Both people still have to approve."
                    )
                    val localObserved = qaLocalApprovalWritten ||
                        approvalStage == DebugApprovalGate.State.LOCAL_ONLY
                    val peerObserved = qaPeerApprovalObserved ||
                        approvalStage == DebugApprovalGate.State.PEER_ONLY
                    Text(
                        "Your approval: " +
                            if (localObserved) "Written to local TLS" else
                                if (approvalSending) "Sending…" else "Not yet sent",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        "Other phone's approval: " +
                            if (peerObserved) "Observed by this phone" else "Waiting",
                        style = MaterialTheme.typography.labelMedium
                    )
                    if (localObserved && peerObserved) {
                        Text(
                            when (approvalStage) {
                                DebugApprovalGate.State.LOCAL_CONFIRMED ->
                                    "Final check: this phone confirmed; waiting for peer."
                                DebugApprovalGate.State.PEER_CONFIRMED ->
                                    "Final check: peer confirmed; waiting for this phone."
                                else -> "Final check: exchanging matching session confirmations…"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            when (probe?.approveForTest()) {
                                LanDemoSession.ApprovalResult.QUEUED -> {
                                    approvalSending = true
                                    status = "Sending your debug approval over the test link…"
                                }
                                LanDemoSession.ApprovalResult.ALREADY_PENDING ->
                                    status = "Your approval is already being processed."
                                LanDemoSession.ApprovalResult.NOT_CONNECTED, null ->
                                    status = "The test link is no longer connected."
                            }
                        },
                        enabled = !approvalSending && !localObserved &&
                            approvalStage in listOf(
                                DebugApprovalGate.State.WAITING,
                                DebugApprovalGate.State.PEER_ONLY
                            ),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) { Text("Approve this debug test") }
                    OutlinedButton(
                        onClick = {
                            val ending = probe
                            probe = null
                            attemptVersion += 1
                            qaFinish = LocalQaReport.Finish.USER_ENDED
                            ending?.declineForTest()
                            clearTemporaryPairing()
                            approvalSending = false
                            transcript = transcript.finish()
                            debugText = debugText.finish()
                            textDraft = ""
                            details = "You declined the experimental connection."
                            status = "Debug approval declined. Test disconnected."
                            stage = "result"
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) { Text("Decline and disconnect") }
                }
                "demo" -> {
                    // Make actual development text the PRIMARY interaction.
                    // Fixed phrases, packet counts and TLS diagnostics remain
                    // available but don't bury the composer on small screens.
                    DebugConversationPanel(
                        transcript = debugText,
                        peerAckIds = ackedTextIds,
                        draft = textDraft,
                        hidden = privacyHidden,
                        onDraftChange = { textDraft = it },
                        onToggleHidden = { privacyHidden = !privacyHidden },
                        onClearRequested = { showClearConfirmation = true },
                        onSend = {
                            when (probe?.sendDebugText(textDraft)) {
                                LanDemoSession.TextSendResult.QUEUED -> {
                                    textDraft = ""
                                    status = "Test text queued. A local TLS write is not delivery."
                                }
                                LanDemoSession.TextSendResult.INVALID_TEXT ->
                                    status = "Use a short test message without invalid characters."
                                LanDemoSession.TextSendResult.BUSY ->
                                    status = "Busy sending. Please try again."
                                LanDemoSession.TextSendResult.AWAITING_APPROVAL ->
                                    status = "Both phones must approve and confirm this test."
                                LanDemoSession.TextSendResult.NOT_CONNECTED, null ->
                                    status = "The test connection is no longer active."
                            }
                        }
                    )
                    Button(
                        onClick = {
                            val ending = probe
                            qaFinish = LocalQaReport.Finish.USER_ENDED
                            probe = null
                            attemptVersion += 1
                            ending?.endByUser()
                            clearTemporaryPairing()
                            details = "Test ended on this phone."
                            status = "Test ended."
                            transcript = transcript.finish()
                            debugText = debugText.finish()
                            textDraft = ""
                            showTestTools = false
                            stage = "result"
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) { Text("End chat and disconnect") }
                    Text(
                        "End the current session at any time. This app clears visible " +
                            "test text as best effort; the other phone may still keep a copy.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { showTestTools = !showTestTools },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = CutCornerShape(6.dp)
                    ) {
                        Text(
                            if (showTestTools) "Hide test tools and connection details"
                            else "Show preset phrases and connection details"
                        )
                    }
                    if (showTestTools) {
                        Text(
                            "PRESET PHRASES / DIAGNOSTICS",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelSmall
                        )
                        TextButton(onClick = { showDetails = !showDetails }) {
                            Text(if (showDetails) "Hide technical link status" else "Technical link status")
                        }
                        if (showDetails) {
                            Text(
                                details,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "TLS channel binding is part of an experimental test, " +
                                    "not an independent security review.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    Text("Written to TLS here: ${transcript.writtenToTls}  •  Received here: ${transcript.receivedHere}",
                        style = MaterialTheme.typography.labelMedium)
                    if (transcript.recent.isEmpty()) {
                        Text("No preset phrases yet. Send one and compare both phones.",
                            style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text("RECENT TEST ACTIVITY • LAST 8",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp),
                            modifier = Modifier.fillMaxWidth()) {
                            transcript.recent.forEach { line ->
                                val fromThisPhone =
                                    line.direction == DebugSessionTranscript.Direction.WRITTEN_TO_TLS
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = if (fromThisPhone) Arrangement.End
                                        else Arrangement.Start
                                ) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(0.9f),
                                        shape = CutCornerShape(6.dp),
                                        color = if (fromThisPhone)
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text(
                                                if (fromThisPhone) "You • Written to TLS"
                                                else "Other phone • Received",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                TestPacketCodec.phrase(line.phraseId),
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val columns = if (PairingTestUiCopy.useStackedActions(
                            maxWidth.value, LocalDensity.current.fontScale)) 1 else 2
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TestPacketCodec.phrases.chunked(columns).forEach { group ->
                                Row(modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    group.forEach { phrase ->
                                        OutlinedButton(
                                            onClick = {
                                                when (probe?.sendPreset(phrase.id)) {
                                                    LanDemoSession.SendResult.QUEUED -> Unit
                                                    LanDemoSession.SendResult.BUSY ->
                                                        status = "Sending… Try again in a moment."
                                                    LanDemoSession.SendResult.AWAITING_APPROVAL ->
                                                        status = "Both debug approvals are required before presets."
                                                    LanDemoSession.SendResult.NOT_CONNECTED, null ->
                                                        status = "The test is no longer connected."
                                                }
                                            },
                                            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                                            shape = CutCornerShape(6.dp)
                                        ) {
                                            Text(phrase.label,
                                                style = MaterialTheme.typography.bodyMedium,
                                                textAlign = TextAlign.Center,
                                                maxLines = 3)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    }

                }
                "result" -> {
                    Text(
                        PairingTestUiCopy.resultHeading(transcript.reachedReady, qaTlsReady),
                        fontWeight = FontWeight.SemiBold
                    )
                    val recovery = NearbyRecoveryGuide.forResult(
                        qaFinish, qaFailureCode, qaEndCode, wifi != null
                    )
                    Text(
                        recovery.title,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Next: ${recovery.nextStep}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "This connection test has ended. Visible text is cleared " +
                            "as best effort, not guaranteed erasure. Production " +
                            "messaging and voice remain disabled.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (recovery.wifiCheckHelpful) {
                        OutlinedButton(
                            onClick = { openWifiSettings() },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) { Text("Open Wi-Fi settings") }
                        OutlinedButton(
                            onClick = {
                                refreshWifiStatus()
                                status = NearbyRecoveryGuide.wifiStatus(wifi != null)
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) { Text("Check this phone's Wi-Fi again") }
                    }
                    // Convenience only: the previous role is not a peer/session.
                    // The reset closes the old socket, invalidates all callbacks,
                    // removes QR material and clears approval before startRole
                    // creates entirely new codes or opens the scanner.
                    val retryChoice = PairingFreshRetry.forLastRole(qaRole)
                    if (retryChoice != null && wifi != null) {
                        Button(
                            onClick = {
                                reset()
                                startRole(retryChoice.role)
                            },
                            enabled = !loading,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) { Text(retryChoice.buttonLabel) }
                        Text(
                            "Use a fresh code on both phones. Both people must approve again.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = { reset() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) { Text("Start over with fresh QR codes") }
                    OutlinedButton(
                        onClick = onBackToPrevious,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text("Done — return to previous screen") }
                    OutlinedButton(
                        onClick = { showResultDetails = !showResultDetails },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text(if (showResultDetails)
                            "− Hide test details" else "+ View test details and QA report")
                    }
                    if (showResultDetails) {
                        Text(
                            PairingTestUiCopy.resultSummary(
                                transcript.reachedReady, transcript.writtenToTls,
                                transcript.receivedHere, qaTlsReady
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            recovery.detail,
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (details.isNotEmpty()) {
                            Text(details, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            "Peer-app ACK events observed on this phone: $qaPeerAppAckEvents " +
                                "(not a read receipt or remote display confirmation).",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "Technical details are local observations, not proof of " +
                                "secure messaging or a delivery/read receipt.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    OutlinedButton(
                        onClick = {
                            // Explicit tap only. Public APK build metadata is
                            // separate from TLS peer identity, QR and private keys.
                            val observation = LocalQaReport.Observation(
                                versionName = installedBuildEvidence.versionName,
                                installedVersionCode = installedBuildEvidence.versionCode,
                                sourceRevision = installedBuildEvidence.sourceRevision,
                                apkSignerSha256 = installedBuildEvidence.apkSignerSha256,
                                androidApiLevel = Build.VERSION.SDK_INT,
                                role = qaRole,
                                reachedReady = transcript.reachedReady,
                                writtenToTls = transcript.writtenToTls,
                                receivedHere = transcript.receivedHere,
                                finish = qaFinish,
                                lastCheckpoint = qaLastCheckpoint,
                                endCode = qaEndCode,
                                failureCode = qaFailureCode,
                                tlsAndQrReady = qaTlsReady,
                                localApprovalWritten = qaLocalApprovalWritten,
                                peerApprovalObserved = qaPeerApprovalObserved,
                                localConfirmationWritten = qaLocalConfirmationWritten,
                                peerConfirmationObserved = qaPeerConfirmationObserved,
                                testTextWrittenToTls = debugText.writtenHere,
                                testTextReceivedHere = debugText.receivedHere,
                                testTextPeerAckEvents = qaPeerAppAckEvents
                            )
                            try {
                                val clipboard = context.getSystemService(
                                    ClipboardManager::class.java
                                )
                                clipboard?.setPrimaryClip(ClipData.newPlainText(
                                    "Eden Comms local test observation",
                                    LocalQaReport.format(observation)
                                ))
                                status = "Local test summary copied. Paste it into your QA notes."
                            } catch (_: Exception) {
                                status = "Couldn't copy the test summary on this phone."
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) { Text("Copy this phone's test summary") }
                    Text(
                        "Optional: copies test status, counts, Android API and public APK build " +
                            "metadata (source revision and signing certificate), not pairing codes " +
                            "or peer identities. " +
                            "Clipboard content can be visible to the OS and other apps.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    }
                }
                "identityError" -> {
                    Text("Open Device identity in Developer Tools for diagnostics.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (stage != "demo") {
            TextButton(
                onClick = { showBuildEvidence = !showBuildEvidence },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Text(if (showBuildEvidence) "− Hide advanced build details"
                    else "?  Advanced: build details")
            }
            if (showBuildEvidence) {
                Text(
                    "Installed test build: ${BuildEvidenceReport.brief(installedBuildEvidence)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = {
                        try {
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            if (clipboard == null) {
                                status = "Couldn't copy build details on this phone."
                            } else {
                                clipboard.setPrimaryClip(ClipData.newPlainText(
                                    "Eden Comms installed build evidence",
                                    BuildEvidenceReport.format(installedBuildEvidence)
                                ))
                                status = "Public build details copied for your QA notes."
                            }
                        } catch (_: Exception) {
                            status = "Couldn't copy build details on this phone."
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text("Copy installed build details") }
            }
        }
        Text(
            "Debug builds can exchange short typed test text and preset phrases after both " +
                "phones approve. Production messaging, retained history and Voice / PTT remain disabled.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // Clear applies only to this screen's in-memory test conversation, NOT
    // peer copies, OS keyboard content or local signing/identity state.
    if (showClearConfirmation && stage == "demo") {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text(ConversationPrivacyContent.clearTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(ConversationPrivacyContent.clearExplanation)
                    Text(
                        ConversationPrivacyContent.privacyLimits,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    debugText = debugText.clearVisible()
                    ackedTextIds = emptySet()
                    textDraft = ""
                    showClearConfirmation = false
                    status = "This phone's visible test messages and draft were cleared. " +
                        "The session is still connected."
                }) { Text("Clear this phone") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPairingHelp) {
        AlertDialog(
            onDismissRequest = { showPairingHelp = false },
            title = { Text(ContextualPrivacyHelp.PAIRING_TITLE) },
            text = { Text(ContextualPrivacyHelp.PAIRING_BODY) },
            confirmButton = {
                TextButton(onClick = { showPairingHelp = false }) { Text("Got it") }
            }
        )
    }

    if (showIdentityConsent) {
        AlertDialog(
            onDismissRequest = { showIdentityConsent = false; pendingRole = null },
            title = { Text("Set up a private device key?") },
            text = {
                Text("Eden Comms needs a local identity to verify this phone. " +
                    "Its private key stays in Android Keystore on this phone. " +
                    "It isn't uploaded or shared.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showIdentityConsent = false
                    val chosen = pendingRole
                    val consentAttempt = attemptVersion
                    scope.launch {
                        loading = true
                        try {
                            val created = withContext(Dispatchers.IO) {
                                Pair(repository.createWithConsent(), repository.tlsSigningStatus())
                            }
                            identity = created.first
                            signingStatus = created.second
                            if (!PairingRestartAdmission.mayContinueConsent(
                                    consentAttempt, attemptVersion,
                                    chosen, pendingRole, stage,
                                    (context as? LifecycleOwner)?.lifecycle?.currentState
                                        ?.isAtLeast(Lifecycle.State.RESUMED) == true
                                )) {
                                pendingRole = null
                                return@launch
                            }
                            if (created.second ==
                                AndroidKeystoreIdentity.TlsSigningStatus.LEGACY_SHA256_ONLY
                            ) {
                                showUpgradeConfirmation = true
                            } else if (chosen != null) {
                                startRole(chosen)
                            }
                        } catch (_: Exception) {
                            status = "Couldn't set up this phone's identity."
                        } finally {
                            loading = false
                        }
                    }
                }) { Text("Create and continue") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showIdentityConsent = false
                    pendingRole = null
                }) { Text("Not now") }
            }
        )
    }

    if (showUpgradeConfirmation) {
        AlertDialog(
            onDismissRequest = { showUpgradeConfirmation = false; pendingRole = null },
            title = { Text("Update this phone's test key?") },
            text = {
                Text("This older key may not work on some phones. " +
                    "Updating creates a compatible local key and changes this phone's identity. " +
                    "Previous QR codes will stop working, so both phones must use fresh codes.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showUpgradeConfirmation = false
                    val chosen = pendingRole
                    val consentAttempt = attemptVersion
                    scope.launch {
                        loading = true
                        try {
                            val updated = withContext(Dispatchers.IO) {
                                Pair(repository.upgradeLegacyWithConsent(),
                                    repository.tlsSigningStatus())
                            }
                            identity = updated.first
                            signingStatus = updated.second
                            if (!PairingRestartAdmission.mayContinueConsent(
                                    consentAttempt, attemptVersion,
                                    chosen, pendingRole, stage,
                                    (context as? LifecycleOwner)?.lifecycle?.currentState
                                        ?.isAtLeast(Lifecycle.State.RESUMED) == true
                                )) {
                                pendingRole = null
                                return@launch
                            }
                            if (chosen != null) startRole(chosen)
                        } catch (_: Exception) {
                            status = "Couldn't update the test key. The old key should still exist."
                        } finally {
                            loading = false
                        }
                    }
                }) { Text("Update and continue") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showUpgradeConfirmation = false
                    pendingRole = null
                }) { Text("Cancel") }
            }
        )
    }
}
