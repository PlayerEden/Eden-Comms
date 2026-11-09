package com.edenpractical.comms.pairing

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.Settings
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
import com.edenpractical.comms.privacy.LocalHistoryPolicy
import com.edenpractical.comms.privacy.DebugPeerMemoryStore
import com.edenpractical.comms.privacy.DebugLocalHistoryStore
import com.edenpractical.comms.security.AndroidKeystoreIdentity
import com.edenpractical.comms.security.LocalTlsIdentity
import com.edenpractical.comms.security.SessionProofBinding
import com.edenpractical.comms.protocol.TestPacketCodec
import com.edenpractical.comms.protocol.DebugLanVoiceWireCodec
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

/**
 * Guided two-QR LOCAL CONNECTION TEST. The second QR is still required:
 * it authenticates the joiner's identity to the presenter.
 *
 * No manual listener, remembered peers, cloud, background discovery or
 * plaintext fallback. Debug builds can exchange four presets and bounded
 * ephemeral typed text on an authenticated test socket ONLY after both QR
 * proofs, matching TLS binding, approvals and confirmations. Bounded voice
 * bursts are experimental only after both final confirmations.
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
    // F5-A: keystore encrypted history exists in debug build only; the
    // release variant is a no-op. Never save without a confirmed session.
    val localHistoryStore = remember(context) {
        DebugLocalHistoryStore(context.applicationContext)
    }
    // Independent encrypted PUBLIC pin cache, never an auth/TLS/QR input.
    val peerMemoryStore = remember(context) {
        DebugPeerMemoryStore(context.applicationContext)
    }
    val scope = rememberCoroutineScope()
    val handler = remember { Handler(Looper.getMainLooper()) }
    val voiceAudio = remember(context) { DebugLanVoiceAudio(context.applicationContext) }
    val voiceSessionOwner = remember { DebugSessionAudioOwner() }
    var talking by remember { mutableStateOf(false) }
    // F4-E: only validated START/END frames from this active TLS reader
    // change the remote-radio indicator, never a local timer or permission.
    var peerTalking by remember { mutableStateOf(false) }
    // "hold" or "tap" while the joiner awaits an explicit peer GRANT.
    // No microphone starts during this pending state.
    var pendingFloorMode by remember { mutableStateOf<String?>(null) }
    // Block another capture while an ended recording is still finalizing
    // and queuing its one bounded voice turn.
    var voiceFinalizing by remember { mutableStateOf(false) }
    var liveVoiceHandle by remember { mutableStateOf<LanDemoSession.LiveVoiceHandle?>(null) }
    // The separate AudioRecord worker must not queue a canceled tap clip.
    var tapSendGate by remember { mutableStateOf<DebugTapVoiceSendGate?>(null) }
    var showTapVoiceControls by remember { mutableStateOf(false) }
    var voiceNote by remember { mutableStateOf("No voice burst sent yet.") }
    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        voiceNote = if (granted) "Microphone allowed. Hold to talk again."
            else "Microphone denied. Voice remains off; text still works."
    }

    var identity by remember { mutableStateOf<LocalTlsIdentity?>(null) }
    var signingStatus by remember {
        mutableStateOf(AndroidKeystoreIdentity.TlsSigningStatus.NOT_CREATED)
    }
    var wifi by remember { mutableStateOf<WifiLanEndpoint.Link?>(null) }
    var stage by remember { mutableStateOf("checking") }
    // The OS needs an actively visible view to honor keepScreenOn. This is
    // only a pairing/chat usability request, never a background WakeLock.
    // Retired automatically on any stage transition, back navigation or
    // composition removal. Backgrounding still stops the test socket below.
    val pairingView = LocalView.current
    DisposableEffect(pairingView, stage) {
        val keepAwake = PairingScreenAwakePolicy.shouldKeepOn(stage)
        pairingView.keepScreenOn = keepAwake
        onDispose { pairingView.keepScreenOn = false }
    }
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
    var activeHistoryPin by remember { mutableStateOf<ByteArray?>(null) }
    // Never persist the user's CHOICE: the next connection defaults to RAM.
    var historyDurationMs by remember {
        mutableLongStateOf(LocalHistoryPolicy.SESSION_ONLY)
    }
    var savedHistory by remember {
        mutableStateOf(emptyList<LocalHistoryPolicy.SavedMessage>())
    }
    var historyNotice by remember {
        mutableStateOf("Session-only default. Nothing is saved until you opt in.")
    }
    var rememberedUntilMs by remember { mutableStateOf<Long?>(null) }
    var peerMemoryBusy by remember { mutableStateOf(false) }
    var peerMemoryNote by remember {
        mutableStateOf("No public peer identity has been saved by this session.")
    }
    // E3 local-only timer: no data persistence or peer wire setting.
    var disappearAfterMs by remember { mutableLongStateOf(DebugLocalDisappearingPolicy.OFF) }
    LaunchedEffect(stage, debugText) {
        if (stage != "demo" || debugText.state != DebugTextTranscript.State.ACTIVE) return@LaunchedEffect
        val expiry = debugText.nextExpiryElapsedMs() ?: return@LaunchedEffect
        val now = SystemClock.elapsedRealtime()
        // Avoid a busy loop; a canceled or replaced transcript cancels this job.
        if (expiry > now) delay(expiry - now)
        debugText = debugText.pruneExpired(SystemClock.elapsedRealtime())
    }
    // A returned peer-app ACK can race ahead of TEXT_WRITTEN's main-thread
    // callback. Store only a bounded set of IDs, never message content.
    var ackedTextIds by remember { mutableStateOf(emptySet<Long>()) }
    // Event count only: no text, peer identity, QR, or ACK IDs exported.
    // Keep after stopTest for result QA; reset only on a fresh pairing attempt.
    var qaPeerAppAckEvents by remember { mutableIntStateOf(0) }
    // F4-Q: per-attempt numeric evidence; never store PCM or peer identifiers.
    var qaVoiceSpeakerStarts by remember { mutableIntStateOf(0) }
    var qaVoicePlaybackProblemTurns by remember { mutableIntStateOf(0) }
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

    /**
     * Live hold-to-talk uses the same proven QR / TLS / bilateral approvals
     * and existing ECVP frames, but sends each 20ms frame DURING the hold.
     * The UI never creates a socket, unverified session or background service.
     */
    fun beginLiveCaptureWithFloor(session: LanDemoSession): Boolean {
        // This is reached only after the host owns the floor locally or the
        // joiner received the exact REQUEST-correlated GRANT.
        val startedSession = session.startLiveVoice()
        val handle = startedSession.handle
        if (startedSession.status != LanDemoSession.VoiceSendResult.QUEUED ||
            handle == null
        ) {
            // Even when the microphone never starts, a successful host
            // reservation or a just-received GRANT must be released.
            session.cancelUnstartedVoiceFloor()
            voiceNote = when (startedSession.status) {
                LanDemoSession.VoiceSendResult.BUSY ->
                    "Other phone may be speaking, or voice is busy. Wait and try again."
                LanDemoSession.VoiceSendResult.AWAITING_APPROVAL ->
                    "Both phones must approve this session first."
                LanDemoSession.VoiceSendResult.NOT_CONNECTED ->
                    "Connection ended. Start again with fresh QR codes."
                LanDemoSession.VoiceSendResult.INVALID_AUDIO ->
                    "Voice test unavailable. Try again."
                LanDemoSession.VoiceSendResult.QUEUED ->
                    "Couldn't start voice. Try again."
            }
            return false
        }
        liveVoiceHandle = handle
        val voiceAttempt = attemptVersion
        val startedAudio = voiceAudio.startLiveCapture(
            onFrame = { pcm -> handle.offerPcm(pcm) },
            onStopped = { hadPcm ->
                handle.finish()
                handler.post {
                    if (DebugSessionEventAdmission.current(
                            voiceAttempt, attemptVersion
                        ) && stage == "demo" && liveVoiceHandle === handle
                    ) {
                        liveVoiceHandle = null
                        talking = false
                        voiceFinalizing = false
                        voiceNote = if (hadPcm)
                            "Microphone stopped; live test frames were queued (peer sound not verified)."
                        else "No usable microphone audio. Hold a little longer and try again."
                    }
                }
            }
        )
        if (!startedAudio) {
            handle.cancel()
            liveVoiceHandle = null
            voiceNote = "Microphone or speaker busy. Wait and try again."
            return false
        }
        talking = true
        voiceFinalizing = false
        voiceNote = "Transmitting live test frames while held • max 3 seconds"
        return true

    }

    fun startLiveVoiceCapture(): Boolean {
        if (stage != "demo" || privacyHidden || talking || voiceFinalizing ||
            peerTalking || pendingFloorMode != null) return false
        if (context.checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
            return false
        }
        val session = probe ?: run {
            voiceNote = "No active nearby test connection."
            return false
        }
        return when (session.requestVoiceFloor()) {
            LanDemoSession.FloorRequestResult.GRANTED ->
                beginLiveCaptureWithFloor(session)
            LanDemoSession.FloorRequestResult.PENDING -> {
                pendingFloorMode = "hold"
                voiceNote = "Requesting talk channel… Microphone is still OFF."
                true
            }
            LanDemoSession.FloorRequestResult.BUSY -> {
                voiceNote = "Talk channel busy. Wait for the other phone to finish."
                false
            }
            LanDemoSession.FloorRequestResult.AWAITING_APPROVAL -> {
                voiceNote = "Both phones must approve and confirm first."
                false
            }
            LanDemoSession.FloorRequestResult.NOT_CONNECTED -> {
                voiceNote = "Connection ended. Start again with fresh QR codes."
                false
            }
        }
    }

    fun finishLiveVoiceCapture() {
        if (pendingFloorMode == "hold") {
            pendingFloorMode = null
            probe?.cancelUnstartedVoiceFloor()
            voiceNote = "Talk request cancelled before microphone started."
            return
        }
        if (!talking || stage != "demo" || liveVoiceHandle == null) return
        talking = false
        voiceFinalizing = true
        voiceNote = "Stopping voice transmission…"
        voiceAudio.finishCapture()
        // The mic worker owns sending END after its last valid PCM frame.
    }

    fun cancelLiveVoiceCapture() {
        if (pendingFloorMode == "hold") {
            pendingFloorMode = null
            probe?.cancelUnstartedVoiceFloor()
            voiceNote = "Talk request cancelled; microphone never started."
            return
        }
        val handle = liveVoiceHandle ?: return
        handle.cancel()
        liveVoiceHandle = null
        voiceAudio.cancelAll()
        talking = false
        voiceFinalizing = false
        // Unlike record-then-send, earlier frames have ALREADY traveled.
        voiceNote = "Transmission stopped. Earlier live audio cannot be unsent."
    }

    // One shared capture path for hold gestures, TalkBack activation and
    // optional visible tap controls. This still records, THEN sends one bounded
    // burst on the same debug-only, already-approved TLS session.
    fun beginTapCaptureWithFloor(live: LanDemoSession): Boolean {
        // Accessible two-tap fallback retains record-then-send, but both the
        // capture and queued burst require the same explicit floor ownership.
        val voiceAttempt = attemptVersion
        val sendGate = DebugTapVoiceSendGate()
        tapSendGate = sendGate
        val started = voiceAudio.startCapture(
            onNoAudio = {
                sendGate.cancel()
                // Always free an unused floor, even if the UI was hidden or
                // the callback's composable session attempt is now stale.
                live.cancelUnstartedVoiceFloor()
                handler.post {
                    if (tapSendGate === sendGate) tapSendGate = null
                    if (DebugSessionEventAdmission.current(
                            voiceAttempt, attemptVersion
                        ) && stage == "demo" && !privacyHidden
                    ) {
                        talking = false
                        voiceFinalizing = false
                        voiceNote =
                            "No usable microphone audio. Speak a little longer and try again."
                    }
                }
            },
            onComplete = { clip ->
                // This critical section orders the user's Cancel/Hide against
                // the worker's short enqueue operation. A prior cancel wins
                // and *never* sends. A prior enqueue cannot be recalled.
                val result = sendGate.submitIfActive { live.sendVoiceBurst(clip) }
                if (result != LanDemoSession.VoiceSendResult.QUEUED)
                    live.cancelUnstartedVoiceFloor()
                handler.post {
                    if (tapSendGate === sendGate) tapSendGate = null
                    if (DebugSessionEventAdmission.current(
                            voiceAttempt, attemptVersion
                        ) && stage == "demo" && !privacyHidden
                    ) {
                        voiceNote = when (result) {
                            null -> "Recording cancelled before its voice burst was queued."
                            LanDemoSession.VoiceSendResult.QUEUED ->
                                "Sending short encrypted voice burst…"
                            LanDemoSession.VoiceSendResult.BUSY ->
                                "Voice sender busy. Try again."
                            LanDemoSession.VoiceSendResult.INVALID_AUDIO ->
                                "Too short or microphone unavailable. Try again."
                            LanDemoSession.VoiceSendResult.AWAITING_APPROVAL ->
                                "Both phones must approve before voice testing."
                            LanDemoSession.VoiceSendResult.NOT_CONNECTED ->
                                "Test session ended. Reconnect with fresh codes."
                        }
                        talking = false
                        voiceFinalizing = false
                    }
                }
            }
        )
        if (started) {
            talking = true
            voiceFinalizing = false
            voiceNote = "Microphone recording now • max 3 seconds"
        } else {
            sendGate.cancel()
            if (tapSendGate === sendGate) tapSendGate = null
            live.cancelUnstartedVoiceFloor()
            voiceNote = "Microphone or speaker busy. Try again."
        }
        return started

    }

    fun startVoiceCapture(): Boolean {
        if (stage != "demo" || privacyHidden || talking || voiceFinalizing ||
            peerTalking || pendingFloorMode != null) return false
        if (context.checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
            return false
        }
        val session = probe ?: run {
            voiceNote = "No active nearby test connection."
            return false
        }
        return when (session.requestVoiceFloor()) {
            LanDemoSession.FloorRequestResult.GRANTED ->
                beginTapCaptureWithFloor(session)
            LanDemoSession.FloorRequestResult.PENDING -> {
                pendingFloorMode = "tap"
                voiceNote = "Requesting talk channel… Microphone is still OFF."
                true
            }
            LanDemoSession.FloorRequestResult.BUSY -> {
                voiceNote = "Talk channel busy. Wait and try again."
                false
            }
            LanDemoSession.FloorRequestResult.AWAITING_APPROVAL -> {
                voiceNote = "Both phones must approve and confirm first."
                false
            }
            LanDemoSession.FloorRequestResult.NOT_CONNECTED -> {
                voiceNote = "Connection ended. Start again with fresh QR codes."
                false
            }
        }
    }

    fun finishVoiceCapture() {
        if (!talking || stage != "demo") return
        talking = false
        voiceFinalizing = true
        voiceNote = "Finishing voice capture and sending…"
        voiceAudio.finishCapture()
    }

    fun cancelVoiceCapture() {
        if (pendingFloorMode == "tap") {
            pendingFloorMode = null
            probe?.cancelUnstartedVoiceFloor()
            voiceNote = "Talk request cancelled; microphone never started."
            return
        }
        if (stage != "demo" || (!talking && !voiceFinalizing)) return
        val cancelledBeforeSend = tapSendGate?.cancel() ?: false
        tapSendGate = null
        probe?.cancelUnstartedVoiceFloor()
        // Generation invalidation also suppresses old AudioRecord callbacks.
        voiceAudio.cancelAll()
        talking = false
        voiceFinalizing = false
        voiceNote = if (cancelledBeforeSend)
            "Recording cancelled before sending. Hold again when ready."
        else "A voice burst may already be queued and cannot be recalled."
    }

    fun tapVoiceCapture() {
        if (pendingFloorMode == "tap") cancelVoiceCapture()
        else if (talking) finishVoiceCapture()
        else startVoiceCapture()
    }

    /**
     * F5-A: only the actual confirmed live peer pin may scope saved messages.
     * Peer identity is not remembered or granted auto-reconnect by this store.
     */
    fun changeLocalHistoryDuration(newDuration: Long) {
        val verifiedPin = activeHistoryPin ?: return
        if (stage != "demo" ||
            debugText.state != DebugTextTranscript.State.ACTIVE ||
            !LocalHistoryPolicy.supported(newDuration)
        ) return
        val now = System.currentTimeMillis()
        val applied = localHistoryStore.changeRetention(
            verifiedPin, newDuration, now
        )
        if (!applied) {
            historyNotice = "Local history setting couldn't be applied. Storage unchanged."
            return
        }
        historyDurationMs = newDuration
        savedHistory = if (newDuration == LocalHistoryPolicy.SESSION_ONLY)
            emptyList() else localHistoryStore.read(verifiedPin, now)
        historyNotice = if (newDuration == LocalHistoryPolicy.SESSION_ONLY)
            "Session-only. This phone's saved history for this peer was deleted."
        else "Save NEW test messages for ${LocalHistoryPolicy.label(newDuration)}. " +
            "Short disappearing display timers pause saving."
    }

    fun readLocalHistory() {
        val pin = activeHistoryPin ?: return
        if (stage != "demo" ||
            debugText.state != DebugTextTranscript.State.ACTIVE
        ) return
        savedHistory = localHistoryStore.read(pin, System.currentTimeMillis())
        historyNotice = "Only unexpired local ciphertext was read. " +
            "No peer trust, remote copy or read status is inferred."
    }

    fun eraseLocalHistory() {
        val pin = activeHistoryPin ?: return
        if (stage != "demo" ||
            debugText.state != DebugTextTranscript.State.ACTIVE
        ) return
        val erased = localHistoryStore.erasePeer(pin, System.currentTimeMillis())
        if (erased) {
            savedHistory = emptyList()
            historyDurationMs = LocalHistoryPolicy.SESSION_ONLY
            historyNotice = "Saved local history deleted for this verified peer. " +
                "The live chat stays connected."
        } else {
            historyNotice = "Could not verify local history deletion on this phone."
        }
    }

    // F5-B: choices occur only after the real QR/TLS/exporter + BOTH human
    // confirmations. Saved public pins never flow into connection admission.
    fun rememberCurrentPeer(days: Int) {
        val ownPin = identity?.publicFingerprint() ?: return
        val peer = activeHistoryPin?.copyOf() ?: return
        if (stage != "demo" || peerMemoryBusy ||
            debugText.state != DebugTextTranscript.State.ACTIVE ||
            days !in 1..30
        ) return
        val attempt = attemptVersion
        peerMemoryBusy = true
        scope.launch {
            val expiry = withContext(Dispatchers.IO) {
                peerMemoryStore.remember(
                    ownPin, peer, days, System.currentTimeMillis()
                )
            }
            if (DebugSessionEventAdmission.current(attempt, attemptVersion) &&
                stage == "demo" && debugText.state == DebugTextTranscript.State.ACTIVE
            ) {
                rememberedUntilMs = expiry
                peerMemoryNote = if (expiry == null)
                    "Could not save peer identity on this phone. No trust is inferred."
                else "Public fingerprint saved locally. Fresh QR, TLS and two " +
                    "human approvals are required next time."
                peerMemoryBusy = false
            }
            peer.fill(0)
            ownPin.fill(0)
        }
    }

    fun forgetCurrentPeer() {
        val ownPin = identity?.publicFingerprint() ?: return
        val peer = activeHistoryPin?.copyOf() ?: return
        if (stage != "demo" || peerMemoryBusy ||
            debugText.state != DebugTextTranscript.State.ACTIVE
        ) return
        val attempt = attemptVersion
        peerMemoryBusy = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                peerMemoryStore.forget(ownPin, peer, System.currentTimeMillis())
            }
            if (DebugSessionEventAdmission.current(attempt, attemptVersion) &&
                stage == "demo" && debugText.state == DebugTextTranscript.State.ACTIVE
            ) {
                if (ok) rememberedUntilMs = null
                peerMemoryNote = if (ok)
                    "Public peer identity forgotten here. Live chat stays active."
                else "Could not verify local peer identity deletion."
                peerMemoryBusy = false
            }
            peer.fill(0)
            ownPin.fill(0)
        }
    }

    fun forgetAllPeers() {
        if (stage != "demo" || peerMemoryBusy ||
            debugText.state != DebugTextTranscript.State.ACTIVE
        ) return
        val attempt = attemptVersion
        peerMemoryBusy = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) { peerMemoryStore.forgetAll() }
            if (DebugSessionEventAdmission.current(attempt, attemptVersion) &&
                stage == "demo" && debugText.state == DebugTextTranscript.State.ACTIVE
            ) {
                if (ok) rememberedUntilMs = null
                peerMemoryNote = if (ok)
                    "All remembered public peer identities were cleared on this phone."
                else "Could not verify deletion of all saved peer identities."
                peerMemoryBusy = false
            }
        }
    }

    fun stopTest() {
        // Ignore messages posted by previous or cancelled attempts.
        attemptVersion += 1
        liveVoiceHandle?.cancel()
        liveVoiceHandle = null
        tapSendGate?.cancel()
        tapSendGate = null
        // Voluntarily stopping revokes the old closure hook BEFORE closing
        // the socket. A delayed old callback cannot affect a new session.
        voiceSessionOwner.revoke()
        probe?.close()
        probe = null
        voiceAudio.cancelAll()
        talking = false
        peerTalking = false
        pendingFloorMode = null
        voiceFinalizing = false
        voiceNote = "No voice burst sent yet."
        debugText = debugText.finish()
        activeHistoryPin?.fill(0)
        activeHistoryPin = null
        historyDurationMs = LocalHistoryPolicy.SESSION_ONLY
        savedHistory = emptyList()
        historyNotice = "Session-only default. Nothing is saved until you opt in."
        rememberedUntilMs = null
        peerMemoryBusy = false
        peerMemoryNote = "No public peer identity has been saved by this session."
        disappearAfterMs = DebugLocalDisappearingPolicy.OFF
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
        disappearAfterMs = DebugLocalDisappearingPolicy.OFF
        ackedTextIds = emptySet()
        qaPeerAppAckEvents = 0
        qaVoiceSpeakerStarts = 0
        qaVoicePlaybackProblemTurns = 0
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
        qaVoiceSpeakerStarts = 0
        qaVoicePlaybackProblemTurns = 0
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
            // Incoming TLS packets and Android speaker callbacks use separate
            // threads. The turn object prevents stale audio observations.
            val currentPlayoutTurn = AtomicReference<DebugVoicePlayoutTurn?>(null)
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
                            // Only this actual approved and mutually confirmed
                            // TLS/QR attempt permits decrypting this peer's
                            // previously saved local test history.
                            activeHistoryPin = expected.copyOf()
                            historyDurationMs = LocalHistoryPolicy.SESSION_ONLY
                            savedHistory = emptyList()
                            historyNotice = "Session-only. You can view past saved " +
                                "messages for this verified peer or opt in for new ones."
                            // Read previously saved PUBLIC metadata only AFTER
                            // this same real attempt reaches bilateral confirmation.
                            // This observation NEVER feeds approval/connection.
                            rememberedUntilMs = null
                            peerMemoryNote = "Checking this phone's optional saved public pin."
                            val ownPinForMemory = own.publicFingerprint()
                            val peerPinForMemory = expected.copyOf()
                            scope.launch {
                                val prior = withContext(Dispatchers.IO) {
                                    peerMemoryStore.rememberedUntil(
                                        ownPinForMemory, peerPinForMemory,
                                        System.currentTimeMillis()
                                    )
                                }
                                if (DebugSessionEventAdmission.current(
                                        thisAttempt, attemptVersion
                                    ) && stage == "demo" &&
                                    debugText.state == DebugTextTranscript.State.ACTIVE
                                ) {
                                    rememberedUntilMs = prior
                                    peerMemoryNote = if (prior == null)
                                        "Not remembered on this phone. No automatic pairing."
                                    else "Previously saved public identity matched only " +
                                        "AFTER fresh QR/TLS approval. No trust was assumed."
                                }
                                peerPinForMemory.fill(0)
                                ownPinForMemory.fill(0)
                            }
                            qaLocalApprovalWritten = true
                            qaPeerApprovalObserved = true
                            qaLocalConfirmationWritten = true
                            qaPeerConfirmationObserved = true
                            approvalSending = false
                            transcript = transcript.ready()
                            debugText = debugText.ready()
                            privacyHidden = false
                            peerTalking = false
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
                            val beforeHistory = debugText
                            val written = event.phase == LanDemoSession.Phase.TEXT_WRITTEN
                            debugText = debugText.record(
                                if (written)
                                    DebugTextTranscript.Direction.WRITTEN_TO_TLS
                                else DebugTextTranscript.Direction.RECEIVED_HERE,
                                messageId, text,
                                observedAtElapsedMs = SystemClock.elapsedRealtime(),
                                disappearAfterMs = disappearAfterMs
                            )
                            // Observe the ACTUAL TLS transport callback, never
                            // a queued draft/handshake/peer ACK. Do not save when
                            // disabled or when short local disappearing is on.
                            val pinForHistory = activeHistoryPin
                            if (beforeHistory !== debugText &&
                                pinForHistory != null &&
                                historyDurationMs != LocalHistoryPolicy.SESSION_ONLY &&
                                disappearAfterMs == DebugLocalDisappearingPolicy.OFF
                            ) {
                                val saved = localHistoryStore.save(
                                    pinForHistory,
                                    if (written) LocalHistoryPolicy.Direction.WRITTEN_HERE
                                    else LocalHistoryPolicy.Direction.RECEIVED_HERE,
                                    messageId, text, historyDurationMs,
                                    System.currentTimeMillis()
                                )
                                if (!saved) {
                                    // Never claim encryption/persistence succeeded.
                                    historyNotice = "This message could not be saved locally."
                                }
                            }
                        }
                        LanDemoSession.Phase.VOICE_FLOOR_GRANTED -> {
                            if (stage == "demo") {
                                val mode = pendingFloorMode
                                pendingFloorMode = null
                                val active = probe
                                if (active != null && !privacyHidden && !peerTalking) {
                                    when (mode) {
                                        "hold" -> beginLiveCaptureWithFloor(active)
                                        "tap" -> beginTapCaptureWithFloor(active)
                                        else -> active.cancelUnstartedVoiceFloor()
                                    }
                                } else active?.cancelUnstartedVoiceFloor()
                            }
                        }
                        LanDemoSession.Phase.VOICE_FLOOR_DENIED -> {
                            if (stage == "demo" && pendingFloorMode != null) {
                                pendingFloorMode = null
                                voiceNote = "Other phone has the channel. Try again after it finishes."
                            }
                        }
                        LanDemoSession.Phase.VOICE_STARTED -> {
                            // Only an approved, decoded START from the real peer
                            // marks this radio as receiving. It may be muted or
                            // inaudible on this handset.
                            if (stage == "demo") peerTalking = true
                        }
                        LanDemoSession.Phase.VOICE_FINISHED -> {
                            if (stage == "demo") peerTalking = false
                        }
                        LanDemoSession.Phase.VOICE_RECEIVED -> {
                            // Network completion is not proof of audible sound.
                            // Clear even if the display skipped a START event.
                            if (stage == "demo") peerTalking = false
                        }
                        LanDemoSession.Phase.VOICE_WRITTEN -> {
                            if (stage == "demo") voiceNote =
                                "Voice frames written to encrypted link (peer playback not confirmed)."
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
            // Same session, same pinned TLS and bilateral approval gates;
            // the receiver copies audio immediately and wipes the source.
            connection.setVoicePlayback { pcm ->
                // Hide conversation is local display/audio privacy, not a
                // remote transport block. Do not play incoming sound here.
                if (privacyHidden) {
                    handler.post {
                        if (DebugSessionEventAdmission.current(thisAttempt, attemptVersion) &&
                            stage == "demo"
                        ) voiceNote = "Conversation hidden: received voice was not played."
                    }
                } else {
                val accepted = voiceAudio.playReceived(pcm) { started ->
                    handler.post {
                        if (DebugSessionEventAdmission.current(thisAttempt, attemptVersion) &&
                            stage == "demo"
                        ) voiceNote = if (started)
                            "The speaker started playback on this phone (sound not verified)."
                        else "Voice arrived, but the phone could not start speaker playback."
                    }
                }
                if (!accepted) handler.post {
                    if (DebugSessionEventAdmission.current(thisAttempt, attemptVersion) &&
                        stage == "demo"
                    ) voiceNote =
                        "Voice arrived while your microphone/speaker was busy. Ask to repeat."
                }
                }
            }
            connection.setLiveVoicePlayback { kind, pcm ->
                // TLS/QR/floor validation has already happened. This callback
                // observes only LOCAL speaker acceptance, never peer receipt.
                // A new START creates one observation, even if muted here.
                val turn = if (kind == DebugLanVoiceWireCodec.Kind.START) {
                    DebugVoicePlayoutTurn().also { currentPlayoutTurn.set(it) }
                } else currentPlayoutTurn.get()
                if (privacyHidden) {
                    if (kind == DebugLanVoiceWireCodec.Kind.START) handler.post {
                        if (DebugSessionEventAdmission.current(thisAttempt, attemptVersion) &&
                            stage == "demo"
                        ) voiceNote = "Conversation hidden: live voice will not play here."
                    }
                } else {
                    val accepted = voiceAudio.acceptLiveFrame(kind, pcm) { started ->
                        val observation = turn?.speakerResult(started)
                        if (observation != null &&
                            observation != DebugVoicePlayoutTurn.Observation.NONE
                        ) handler.post {
                            if (DebugSessionEventAdmission.current(thisAttempt, attemptVersion) &&
                                stage == "demo" && currentPlayoutTurn.get() === turn
                            ) {
                                when (observation) {
                                    DebugVoicePlayoutTurn.Observation.SPEAKER_STARTED -> {
                                        if (qaVoiceSpeakerStarts < 10_000) qaVoiceSpeakerStarts += 1
                                        // Never overwrite a later queued
                                        // speaker-drop warning with a delayed
                                        // AudioTrack-start callback.
                                        if (turn?.hasPlaybackProblem() != true)
                                            voiceNote = "Live speaker started on this phone (audibility not verified)."
                                    }
                                    DebugVoicePlayoutTurn.Observation.LOCAL_PLAYBACK_FAILED -> {
                                        if (qaVoicePlaybackProblemTurns < 10_000)
                                            qaVoicePlaybackProblemTurns += 1
                                        voiceNote = "Local speaker could not play this live voice turn. Ask to repeat."
                                    }
                                    DebugVoicePlayoutTurn.Observation.NONE -> Unit
                                }
                            }
                        }
                    }
                    // Both false START and false DATA mean local playback
                    // was rejected. A valid TLS END does not certify sound.
                    val reportProblem = !accepted &&
                        kind != DebugLanVoiceWireCodec.Kind.END &&
                        turn?.rejectedFrame() ==
                            DebugVoicePlayoutTurn.Observation.LOCAL_PLAYBACK_FAILED
                    if (reportProblem) handler.post {
                        if (DebugSessionEventAdmission.current(thisAttempt, attemptVersion) &&
                            stage == "demo" && currentPlayoutTurn.get() === turn
                        ) {
                            if (qaVoicePlaybackProblemTurns < 10_000)
                                qaVoicePlaybackProblemTurns += 1
                            voiceNote = "Live voice arrived, but this phone dropped local speaker audio. Ask to repeat."
                        }
                    }
                }
            }
            // The networking close path stops local hardware directly,
            // instead of waiting for an ENDED event to reach the UI queue.
            // The ownership check prevents an old TLS attempt from muting
            // audio from a newer, independently approved attempt.
            connection.setLocalAudioStopOnClose {
                voiceSessionOwner.stopIfCurrent(connection) {
                    voiceAudio.cancelAll()
                }
            }
            voiceSessionOwner.activate(connection)
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
            tapSendGate?.cancel()
            tapSendGate = null
            voiceSessionOwner.revoke()
            latestProbe?.close()
            voiceAudio.cancelAll()
            clearTemporaryPairing()
            transcript = transcript.finish()
            debugText = debugText.finish()
            activeHistoryPin?.fill(0)
            activeHistoryPin = null
            historyDurationMs = LocalHistoryPolicy.SESSION_ONLY
            savedHistory = emptyList()
            rememberedUntilMs = null
            peerMemoryBusy = false
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
                            "Never send secrets. After approval, short voice bursts are a debug-only option.",
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
                        disappearAfterMs = disappearAfterMs,
                        historyDurationMs = historyDurationMs,
                        historyEntries = savedHistory,
                        historyNotice = historyNotice,
                        onHistoryDurationChange = { changeLocalHistoryDuration(it) },
                        onHistoryRead = { readLocalHistory() },
                        onHistoryErase = { eraseLocalHistory() },
                        onDisappearAfterChange = { newDuration ->
                            if (DebugLocalDisappearingPolicy.supports(newDuration)) {
                                debugText = debugText.shortenVisibleExpiry(newDuration)
                                    .pruneExpired(SystemClock.elapsedRealtime())
                                disappearAfterMs = newDuration
                            }
                        },
                        onDraftChange = { textDraft = it },
                        onToggleHidden = {
                            privacyHidden = !privacyHidden
                            if (privacyHidden) {
                                // Hiding the conversation also stops local
                                // recording/playback. The peer can still send
                                // over TLS: hidden is not a network block.
                                val activeLiveStream = liveVoiceHandle != null
                                tapSendGate?.cancel()
                                tapSendGate = null
                                liveVoiceHandle?.cancel()
                                liveVoiceHandle = null
                                pendingFloorMode = null
                                // A LIVE writer must send its END before it
                                // sends RELEASE. Only an unstarted floor is
                                // released directly from the UI.
                                if (!activeLiveStream) probe?.cancelUnstartedVoiceFloor()
                                voiceAudio.cancelAll()
                                talking = false
                                voiceFinalizing = false
                                voiceNote = "Conversation hidden. Microphone and speaker stopped here."
                            }
                        },
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
                    if (!privacyHidden) {
                        DebugRememberPeerPanel(
                            rememberedUntilMs = rememberedUntilMs,
                            note = peerMemoryNote,
                            busy = peerMemoryBusy,
                            onRemember = { rememberCurrentPeer(it) },
                            onForget = { forgetCurrentPeer() },
                            onForgetAll = { forgetAllPeers() }
                        )
                    }
                    // F4-C: touch-and-hold voice capture, release to send via
                    // the same approved LAN TLS session. Accessible two-tap
                    // control remains available, with no new radio or account.
                    FieldFrame(accent = true) {
                        Text("WALKIE-TALKIE • DEVELOPMENT TEST",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        Text(
                            "Hold the button to transmit short audio frames over " +
                                "the approved Wi-Fi link as you speak. Release to stop. " +
                                "Three seconds maximum. Use tap controls for the " +
                                "older record-then-send mode.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        // F4-E: observation-only receiving state, not a
                        // negotiated floor. Simultaneous presses may collide.
                        Text(
                            when {
                                privacyHidden -> "VOICE MUTED ON THIS PHONE"
                                peerTalking -> "RECEIVING • OTHER PHONE IS TALKING"
                                pendingFloorMode != null -> "WAITING FOR RADIO CHANNEL • MIC OFF"
                                talking -> "TRANSMITTING • LIVE TEST AUDIO"
                                voiceFinalizing -> "STOPPING TRANSMISSION…"
                                else -> "READY TO TALK • TEST CONNECTION"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.semantics {
                                liveRegion = LiveRegionMode.Polite
                            }
                        )
                        // F4-D: real live PCM goes over the approved TLS
                        // session while held. A remote START temporarily
                        // disables a new hold, but is not floor arbitration.
                        DebugHoldToTalkControl(
                            enabled = !voiceFinalizing && !privacyHidden && !peerTalking &&
                                pendingFloorMode != "tap",
                            recording = talking,
                            onPressStart = { startLiveVoiceCapture() },
                            onRelease = { finishLiveVoiceCapture() },
                            onCancel = { cancelLiveVoiceCapture() },
                            onAccessibilityClick = { tapVoiceCapture() }
                        )
                        TextButton(
                            onClick = { showTapVoiceControls = !showTapVoiceControls },
                            enabled = !talking && !voiceFinalizing &&
                                pendingFloorMode == null,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            Text(if (showTapVoiceControls) "HIDE TAP CONTROLS"
                                else "USE TAP CONTROLS INSTEAD")
                        }
                        if (showTapVoiceControls) {
                            Button(
                                onClick = { tapVoiceCapture() },
                                // Keep Stop available if a tap-mode capture
                                // and remote START race each other.
                                enabled = !voiceFinalizing && !privacyHidden &&
                                    (!peerTalking || talking || pendingFloorMode == "tap") &&
                                    pendingFloorMode != "hold",
                                modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
                                shape = CutCornerShape(6.dp)
                            ) {
                                Text(when {
                                    pendingFloorMode == "tap" -> "CANCEL TALK REQUEST"
                                    talking -> "SEND VOICE BURST"
                                    else -> "START TALKING"
                                })
                            }
                        }
                        Text(voiceNote,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        Text(
                            "Audio starts only after an authenticated talk grant. " +
                                "Already-transmitted sound cannot be recalled. " +
                                "If both users press at once, the host arbitrates. " +
                                "Backgrounding or disconnecting stops local audio. " +
                                "No recording is saved. Use harmless test speech.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            val ending = probe
                            qaFinish = LocalQaReport.Finish.USER_ENDED
                            probe = null
                            attemptVersion += 1
                            ending?.endByUser()
                            liveVoiceHandle?.cancel()
                            liveVoiceHandle = null
                            voiceAudio.cancelAll()
                            talking = false
                            voiceFinalizing = false
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
                            "Local live speaker starts: $qaVoiceSpeakerStarts; " +
                                "voice turns with local playback problems: $qaVoicePlaybackProblemTurns. " +
                                "Neither count proves speech was audible.",
                            style = MaterialTheme.typography.bodySmall
                        )
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
                                testTextPeerAckEvents = qaPeerAppAckEvents,
                                voiceSpeakerStarts = qaVoiceSpeakerStarts,
                                voicePlaybackProblemTurns = qaVoicePlaybackProblemTurns
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
