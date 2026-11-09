package com.edenpractical.comms.bluetooth

import com.edenpractical.comms.audio.DebugNearbyVoiceOwner
import com.edenpractical.comms.audio.DebugNearbyVoicePanel
import com.edenpractical.comms.ui.DebugVerifiedConversationPanel

import com.edenpractical.comms.privacy.DebugVerifiedNearbyPrivacy
import com.edenpractical.comms.privacy.DebugVerifiedNearbyPrivacyPanel

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.edenpractical.comms.model.ConnectionMethod
import com.edenpractical.comms.transport.NearbyDiscoverySession

/**
 * Debug-build-only Bluetooth CLASSIC inquiry. No paired-device access, device
 * names, MAC addresses, SDP, RFCOMM sockets, durable discovery data, messages,
 * telemetry, automatic reconnection or trusted-peer enrollment.
 *
 * This activity and the scan permission live in src/debug only, so a release
 * APK cannot launch the activity or request BLUETOOTH_SCAN.
 *
 * Explicit permission + start required; every exit best-effort cancels the OS
 * inquiry and unregisters broadcasts. This M3l debug experiment holds up to
 * 12 anonymous BluetoothDevice object handles in memory, as numbered signals.
 * Selecting one stops scanning and can now support a SEPARATE user-tapped,
 * permission-gated debug RFCOMM link. F2-Q adds actual encrypted test TEXT
 * only after full QR/TLS/exporter + bilateral CONFIRM. No saved peer or
 * public secure messaging; real Android radio QA is still pending.
 */
class DebugBluetoothDiscoveryActivity : ComponentActivity() {
    private val qrCoordinator = DebugBluetoothQrCoordinator()
    private val localPrivacy by lazy { DebugVerifiedNearbyPrivacy(applicationContext) }
    private val localVoice by lazy { DebugNearbyVoiceOwner(applicationContext) }
    private val discoveryState = NearbyDiscoverySession()
    private val candidateLedger = DebugBluetoothCandidateLedger<BluetoothDevice>()
    private var candidateToken: DebugBluetoothCandidateLedger.Token? = null
    private var candidateRows by mutableStateOf(emptyList<DebugBluetoothCandidateLedger.Row>())
    private var selectedOrdinal by mutableStateOf<Int?>(null)
    private var attempt: NearbyDiscoverySession.Attempt? = null
    private var adapter: BluetoothAdapter? = null
    private var receiverRegistered = false
    private var radioRunning = false
    @Volatile private var foreground = false
    private var busy by mutableStateOf(false)
    private var observations by mutableIntStateOf(0)
    private var showRawScanner by mutableStateOf(false)
    private var status by mutableStateOf("Search is off. No Bluetooth device is trusted.")
    private val handler = Handler(Looper.getMainLooper())

    private enum class PendingLink { NONE, HOST, JOIN }
    private var pendingLink = PendingLink.NONE
    private var discoverabilityPromptOpen = false
    private var connectPermissionPromptOpen = false
    private var approvalPending by mutableStateOf(false)
    private var linkPhase by mutableStateOf(DebugRfcommAttemptGate.Phase.IDLE)
    private var chatReady by mutableStateOf(false)
    private var chatDraft by mutableStateOf("")
    private var chatLines by mutableStateOf(emptyList<DebugBluetoothChatLine>())
    // Peer-app ACK may arrive before the sender's posted WRITTEN UI callback.
    // Keep bounded, content-free ids so the eventual bubble is accurate.
    private var ackedChatIds by mutableStateOf(emptySet<Long>())
    private var linkStatus by mutableStateOf(
        "No RFCOMM test link. Bluetooth is not verified Eden Comms messaging."
    )
    // F2-K: only the last sanitised stage emitted by the real TLS worker.
    // No peer IDs, certificate material, message content or history.
    private var verifiedProof by mutableStateOf<DebugBluetoothVerifiedStage?>(null)
    private val linkController: DebugRfcommLinkController by lazy {
        DebugRfcommLinkController(
            handler = handler,
            isForeground = { foreground },
            hasConnectPermission = { hasConnectPermission() },
            onState = { phase, description ->
                linkPhase = phase
                linkStatus = description
                if (phase == DebugRfcommAttemptGate.Phase.STOPPED) {
                    chatReady = false
                    chatDraft = ""
                    chatLines = emptyList()
                    ackedChatIds = emptySet()
                    localPrivacy.close()
                    localVoice.disable()
                    // No reusable QR or anonymous radio candidate on retry:
                    // restart the actual two-phone flow with fresh codes.
                    qrCoordinator.clear()
                    clearCandidateMemory()
                }
            },
            onApprovalReady = { available ->
                approvalPending = available
            },
            onVerificationStage = { step -> verifiedProof = step },
            onApprovedIdentity = { own, peer ->
                // Only actual post-confirmation RFCOMM TLS adoption.
                localPrivacy.openVerified(own, peer)
            },
            onTextEvent = { event ->
                localPrivacy.receiveEncryptedEvent(event)
                when (event) {
                    is DebugBluetoothApprovedTextChannel.Event.Ready -> {
                        chatReady = true
                        approvalPending = false
                        linkStatus = "Both confirmed. Encrypted Bluetooth test chat is active."
                        localVoice.activate(
                            canContinue = {
                                foreground && chatReady &&
                                    linkPhase == DebugRfcommAttemptGate.Phase.LINK_OPEN &&
                                    hasConnectPermission()
                            },
                            requestFloor = { linkController.requestVoiceFloor() },
                            cancelFloor = { linkController.cancelUnstartedVoiceFloor() },
                            beginStream = { linkController.beginLiveVoice() },
                            offerFrame = { pcm -> linkController.offerLiveVoiceFrame(pcm) },
                            finishStream = { linkController.finishLiveVoice() }
                        )
                    }
                    is DebugBluetoothApprovedTextChannel.Event.Written ->
                        chatLines = (chatLines + DebugBluetoothChatLine(
                            outgoing = true, id = event.id, text = event.text,
                            acknowledged = event.id in ackedChatIds
                        )).takeLast(24)
                    is DebugBluetoothApprovedTextChannel.Event.Received ->
                        chatLines = (chatLines + DebugBluetoothChatLine(
                            outgoing = false, id = event.id, text = event.text
                        )).takeLast(24)
                    is DebugBluetoothApprovedTextChannel.Event.Acknowledged -> {
                        ackedChatIds = (ackedChatIds + event.id).toList().takeLast(128).toSet()
                        chatLines = chatLines.map { line ->
                            if (line.outgoing && line.id == event.id)
                                line.copy(acknowledged = true) else line
                        }
                    }
                    // F4-U complete clips are retained only for JVM regression;
                    // live Android audio comes from validated 20ms frame events.
                    is DebugBluetoothApprovedTextChannel.Event.VoiceReceived -> Unit
                    is DebugBluetoothApprovedTextChannel.Event.LiveVoiceFrame ->
                        localVoice.receiveLiveFrame(event.kind, event.pcm)
                    DebugBluetoothApprovedTextChannel.Event.FloorGranted ->
                        localVoice.floorGranted()
                    DebugBluetoothApprovedTextChannel.Event.FloorDenied ->
                        localVoice.floorDenied()
                    is DebugBluetoothApprovedTextChannel.Event.VoiceWritten ->
                        localVoice.voiceWritten(event.frames)
                    is DebugBluetoothApprovedTextChannel.Event.Failed -> Unit
                }
            }
        )
    }

    private val linkPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        connectPermissionPromptOpen = false
        if (!foreground) {
            pendingLink = PendingLink.NONE
            linkStatus = "The app left the screen before connection permission was granted."
        } else when (pendingLink) {
            PendingLink.HOST -> {
                if (hasConnectPermission() && hasAdvertisePermission()) {
                    launchDiscoverablePrompt()
                } else {
                    pendingLink = PendingLink.NONE
                    linkStatus = "Bluetooth connection or visibility permission denied."
                }
            }
            PendingLink.JOIN -> {
                pendingLink = PendingLink.NONE
                if (hasConnectPermission()) startJoinLink()
                else linkStatus = "Bluetooth connection permission denied. No link started."
            }
            PendingLink.NONE -> Unit
        }
    }

    private val discoverabilityRequest = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        discoverabilityPromptOpen = false
        val mayStart = foreground && pendingLink == PendingLink.HOST &&
            hasConnectPermission() && hasAdvertisePermission() &&
            qrCoordinator.readyForDebugLink(hostRole = true) &&
            result.resultCode > 0
        pendingLink = PendingLink.NONE
        if (mayStart) startHostLink()
        else linkStatus = "Discoverability wasn't approved, or this screen was left. No host started."
    }

    private val linkPermissionMonitor = object : Runnable {
        override fun run() {
            if (linkPhase in setOf(
                    DebugRfcommAttemptGate.Phase.CONNECTING,
                    DebugRfcommAttemptGate.Phase.LINK_OPEN
                )
            ) {
                if (!foreground || !hasConnectPermission()) {
                    linkController.permissionLost()
                } else {
                    handler.postDelayed(this, 1_000L)
                }
            }
        }
    }

    private fun watchLinkPermission() {
        handler.removeCallbacks(linkPermissionMonitor)
        handler.postDelayed(linkPermissionMonitor, 1_000L)
    }

    private val timeout = Runnable {
        val token = attempt ?: return@Runnable
        discoveryState.tick(token, SystemClock.elapsedRealtime())
        finishCandidatesForReview()
        stopHardware()
        attempt = null
        busy = false
        status = "Search time limit reached. Review unverified signals or search again. No connection was made."
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val token = attempt ?: return
            if (!foreground || !radioRunning || !hasScanPermission()) {
                if (radioRunning && !hasScanPermission()) {
                    discoveryState.permissionRevoked(token)
                    clearCandidateMemory()
                    stopHardware()
                    attempt = null
                    busy = false
                    status = "Nearby devices permission was removed. Search stopped."
                }
                return
            }
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val accepted = discoveryState.candidateObserved(
                        token, foreground = true, elapsedMs = SystemClock.elapsedRealtime()
                    )
                    observations = discoveryState.snapshot().observations
                    if (accepted) {
                        // EXTRA_DEVICE is an untrusted opaque handle. Do NOT
                        // read the friendly name, MAC, class, RSSI or bond state.
                        val handle = discoveredDevice(intent)
                        val current = candidateToken
                        if (current != null && handle != null) {
                            candidateLedger.observe(current, handle)
                            candidateRows = candidateLedger.rows(current)
                        }
                    } else {
                        finishCandidatesForReview()
                        stopHardware()
                        attempt = null
                        busy = false
                        status = "Search ended at its safety or observation limit. " +
                            "Signals are unverified and not connected."
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    discoveryState.complete(token)
                    finishCandidatesForReview()
                    stopHardware()
                    attempt = null
                    busy = false
                    status = "Bluetooth inquiry finished. You may stage an unverified signal " +
                        "for a future verification step; nothing is connected."
                }
            }
        }
    }

    private val scanPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { approved ->
        val token = attempt ?: return@registerForActivityResult
        val granted = approved && hasScanPermission()
        if (!discoveryState.platformPermissionResult(token, foreground, granted)) {
            clearCandidateMemory()
            stopHardware()
            attempt = null
            busy = false
            status = if (!foreground) "Search cancelled when the app left the screen."
                else "Nearby devices permission not granted. Search did not start."
        } else {
            startInquiry(token)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) {
            finish()
            return
        }
        qrCoordinator.loadExistingIdentity()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            if (chatReady && linkPhase == DebugRfcommAttemptGate.Phase.LINK_OPEN)
                                "Bluetooth conversation" else "Connect with Bluetooth",
                            style = MaterialTheme.typography.titleLarge
                        )
                        if (chatReady &&
                            linkPhase == DebugRfcommAttemptGate.Phase.LINK_OPEN
                        ) {
                            DebugVerifiedConversationPanel(
                                method = "Bluetooth",
                                onDisconnect = {
                                    localVoice.disable()
                                    linkController.disconnect()
                                },
                                onLeaveTalk = { localVoice.release() },
                                textContent = {
                                    DebugBluetoothTextPanel(
                                        lines = chatLines,
                                        draft = chatDraft,
                                        onDraft = { chatDraft = it },
                                        onSend = { sendBluetoothText() }
                                    )
                                },
                                talkContent = { DebugNearbyVoicePanel(localVoice) },
                                privacyContent = { DebugVerifiedNearbyPrivacyPanel(localPrivacy) }
                            )
                        } else {
                        val guide = DebugBluetoothJourneyCue.current(
                            qrCoordinator.stage, linkPhase,
                            selectedOrdinal != null, busy, approvalPending
                        )
                        Text(
                            guide.phase,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            guide.instruction,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(guide.next, style = MaterialTheme.typography.bodyMedium)
                        // QR comes FIRST; raw radio search is relevant to the
                        // JOIN role only after two fresh codes are exchanged.
                        if (linkPhase in setOf(
                                DebugRfcommAttemptGate.Phase.CONNECTING,
                                DebugRfcommAttemptGate.Phase.LINK_OPEN
                            )
                        ) {
                            Text(
                                "QR preparation is frozen while this unverified " +
                                    "Bluetooth test link is active. Disconnect before changing codes."
                            )
                        } else {
                            DebugBluetoothQrPanel(qrCoordinator)
                        }
                        // Primary host action follows QR preparation; no
                        // disabled radio buttons on the initial screen.
                        if (qrCoordinator.stage ==
                            DebugBluetoothQrCoordinator.Stage.HOST_PREPARED &&
                            linkPhase !in setOf(
                                DebugRfcommAttemptGate.Phase.CONNECTING,
                                DebugRfcommAttemptGate.Phase.LINK_OPEN
                            )) {
                        Button(
                            onClick = { beginHostLink() },
                            enabled = foreground && !busy && pendingLink == PendingLink.NONE &&
                                qrCoordinator.stage == DebugBluetoothQrCoordinator.Stage.HOST_PREPARED &&
                                linkPhase !in setOf(
                                    DebugRfcommAttemptGate.Phase.CONNECTING,
                                    DebugRfcommAttemptGate.Phase.LINK_OPEN
                                ) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) { Text("HOST ONE-TIME BLUETOOTH TEST LINK") }
                        }

                        // Critical link status, consent and disconnect belong
                        // directly under the guide, not at the end of a long
                        // list of raw discovery diagnostics.
                        if (linkPhase != DebugRfcommAttemptGate.Phase.IDLE) {
                        Text(linkStatus, style = MaterialTheme.typography.bodyLarge)
                        verifiedProof?.let { step ->
                            Text(
                                step.display(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                         if (approvalPending &&
                            linkPhase == DebugRfcommAttemptGate.Phase.LINK_OPEN
                        ) {
                            Text(
                                "Both phones completed the encrypted QR + TLS " +
                                    "identity checks. APPROVE only if you are " +
                                    "physically with the person you intended. " +
                                    "Text stays off until both final confirmations."
                            )
                            Button(
                                onClick = {
                                    if (linkController.approve()) {
                                        approvalPending = false
                                        linkStatus = "You approved this one test link. " +
                                            "Awaiting the other person's approval " +
                                            "and two encrypted final confirmations."
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            ) { Text("APPROVE THIS TEST SESSION") }
                            OutlinedButton(
                                onClick = {
                                    if (linkController.decline()) {
                                        approvalPending = false
                                        linkStatus = "You declined this test. " +
                                            "Ending this Bluetooth session."
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            ) { Text("DECLINE AND CLOSE SESSION") }
                        }
                        OutlinedButton(
                            onClick = { linkController.disconnect() },
                            enabled = linkPhase in setOf(
                                DebugRfcommAttemptGate.Phase.CONNECTING,
                                DebugRfcommAttemptGate.Phase.LINK_OPEN
                            ),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) { Text("DISCONNECT BLUETOOTH TEST LINK") }
                        }
                        Text(
                            "Bluetooth Classic • two phones • experimental encrypted text " +
                                "and live floor-controlled talk only after fresh QR, " +
                                "TLS, both human approvals and final confirmations.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        val showRadioSearch =
                            qrCoordinator.stage == DebugBluetoothQrCoordinator.Stage.JOIN_PREPARED ||
                                busy || candidateRows.isNotEmpty() || selectedOrdinal != null ||
                                showRawScanner
                        if (qrCoordinator.stage !=
                            DebugBluetoothQrCoordinator.Stage.JOIN_PREPARED &&
                            linkPhase !in setOf(
                                DebugRfcommAttemptGate.Phase.CONNECTING,
                                DebugRfcommAttemptGate.Phase.LINK_OPEN
                            ) &&
                            !busy && candidateRows.isEmpty() && selectedOrdinal == null) {
                            OutlinedButton(
                                onClick = { showRawScanner = !showRawScanner },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            ) {
                                Text(if (showRawScanner) "HIDE RAW RADIO DIAGNOSTICS"
                                     else "ADVANCED • RAW RADIO SCAN")
                            }
                        }
                        if (showRadioSearch) {
                            Text(
                                "Radio observations: $observations (unverified events). " +
                                    "Up to 12 unnamed signals are kept temporarily, never as trusted peers."
                            )
                        Text(status, style = MaterialTheme.typography.bodyLarge)
                        Button(
                            onClick = { beginInquiry() },
                            enabled = !busy && pendingLink == PendingLink.NONE &&
                                linkPhase !in setOf(
                                    DebugRfcommAttemptGate.Phase.CONNECTING,
                                    DebugRfcommAttemptGate.Phase.LINK_OPEN
                                ) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) { Text("START 30-SECOND TEST SEARCH") }
                        OutlinedButton(
                            onClick = { stopByUser() },
                            enabled = busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) { Text("STOP SEARCH AND CLEAR RESULTS") }
                        if (candidateRows.isNotEmpty()) {
                            Text(
                                "Unverified nearby signals — these can include unrelated " +
                                    "Bluetooth devices. The numbers are local to this scan."
                            )
                            for (candidate in candidateRows) {
                                OutlinedButton(
                                    onClick = { selectCandidate(candidate.ordinal) },
                                    enabled = foreground && hasScanPermission(),
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                                ) {
                                    Text("STAGE ${candidate.label.uppercase()}")
                                }
                            }
                        }
                        if (!busy && candidateRows.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { clearCandidateMemory() },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            ) { Text("CLEAR ALL SIGNAL RESULTS") }
                        }
                        if (selectedOrdinal != null) {
                            Text(
                                "Signal $selectedOrdinal is staged locally ONLY. Its owner " +
                                    "and identity have NOT been verified. A separate debug " +
                                    "RFCOMM still requires mutual TLS, QR proofs and both approvals before text."
                            )
                            OutlinedButton(
                                onClick = { clearCandidateMemory() },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            ) { Text("FORGET SELECTED SIGNAL") }
                        }
                        if (qrCoordinator.stage ==
                            DebugBluetoothQrCoordinator.Stage.JOIN_PREPARED &&
                            selectedOrdinal != null &&
                            linkPhase !in setOf(
                                DebugRfcommAttemptGate.Phase.CONNECTING,
                                DebugRfcommAttemptGate.Phase.LINK_OPEN
                            )) {
                        Button(
                            onClick = { beginJoinLink() },
                            enabled = foreground && !busy && pendingLink == PendingLink.NONE &&
                                selectedOrdinal != null &&
                                qrCoordinator.stage == DebugBluetoothQrCoordinator.Stage.JOIN_PREPARED &&
                                linkPhase !in setOf(
                                    DebugRfcommAttemptGate.Phase.CONNECTING,
                                    DebugRfcommAttemptGate.Phase.LINK_OPEN
                                ) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) { Text("JOIN STAGED UNVERIFIED SIGNAL") }
                        }
                        } // JOIN-only radio search; otherwise keep QR flow uncluttered.
                        // Optional technical explanation: no need to parse
                        // laboratory terminology in order to host or join.
                        if (showRawScanner) {
                            Text(
                                "Bluetooth results can include unrelated devices. " +
                                    "Finding one proves nothing about its identity. " +
                                    "Do not share sensitive data or rely on this test " +
                                    "for communication."
                            )
                            Text(
                                "Close or background this screen to end the scan and any " +
                                    "RFCOMM test link. Android may retain its own Bluetooth state; " +
                                    "Eden Comms does not save discovery results."
                            )
                            Text(
                                "BLUETOOTH LINK — DEVELOPMENT ONLY",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "Host on one phone, then scan and stage its signal on the other. " +
                                    "The host asks Android to make it discoverable for up to 120 seconds. " +
                                    "Android controls that visibility even after this app disconnects."
                            )
                        }
                        if (showRawScanner) {
                        Text(
                            "Only after the pinned TLS + QR + exporter proof, BOTH " +
                                "independent human approvals, and both encrypted final confirmations " +
                                "may the same connection carry debug TEXT/ACK and live voice. " +
                                "Not reviewed secure messaging. Android controls discoverability."
                        )
                        }
                        } // End connection setup; hide raw diagnostics in verified chat.
                    }
                }
            }
        }
    }

    private fun sendBluetoothText() {
        if (!foreground || !chatReady ||
            linkPhase != DebugRfcommAttemptGate.Phase.LINK_OPEN
        ) return
        val draft = chatDraft
        if (draft.isBlank()) return
        if (linkController.offerText(draft)) {
            chatDraft = ""
            linkStatus = "Test text queued for encrypted Bluetooth sending."
        } else {
            linkStatus = "Couldn't queue text. Check length, link or outstanding messages."
        }
    }

    override fun onStart() {
        super.onStart()
        foreground = true
    }

    override fun onStop() {
        foreground = false
        val token = attempt
        if (token != null) discoveryState.onBackground(token)
        stopHardware()
        clearCandidateMemory()
        linkController.background()
        approvalPending = false
        chatReady = false
        chatDraft = ""
        chatLines = emptyList()
        ackedChatIds = emptySet()
        localPrivacy.close()
        localVoice.disable()
        handler.removeCallbacks(linkPermissionMonitor)
        if (!discoverabilityPromptOpen && !connectPermissionPromptOpen) {
            pendingLink = PendingLink.NONE
            qrCoordinator.clear()
        }
        attempt = null
        busy = false
        status = "Search and local signal choices cleared when this screen was left."
        super.onStop()
    }

    override fun onDestroy() {
        stopHardware()
        clearCandidateMemory()
        linkController.background()
        localPrivacy.dispose()
        localVoice.disable()
        qrCoordinator.clear()
        handler.removeCallbacks(linkPermissionMonitor)
        super.onDestroy()
    }

    private fun hasScanPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED

    private fun hasConnectPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

    private fun hasAdvertisePermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_ADVERTISE
            ) == PackageManager.PERMISSION_GRANTED

    private fun beginHostLink() {
        if (!foreground || busy || pendingLink != PendingLink.NONE ||
            linkPhase in setOf(
                DebugRfcommAttemptGate.Phase.CONNECTING,
                DebugRfcommAttemptGate.Phase.LINK_OPEN
            ) || Build.VERSION.SDK_INT < Build.VERSION_CODES.S
        ) return
        if (!qrCoordinator.readyForDebugLink(hostRole = true)) {
            linkStatus = "Complete fresh reciprocal Bluetooth QR preparation first."
            return
        }
        clearCandidateMemory()
        pendingLink = PendingLink.HOST
        linkStatus = "Bluetooth connect and discoverable permissions are required."
        if (hasConnectPermission() && hasAdvertisePermission()) {
            launchDiscoverablePrompt()
        } else {
            connectPermissionPromptOpen = true
            linkPermissionRequest.launch(
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
            )
        }
    }

    private fun beginJoinLink() {
        if (!foreground || busy || pendingLink != PendingLink.NONE ||
            candidateLedger.selectedHandle() == null ||
            linkPhase in setOf(
                DebugRfcommAttemptGate.Phase.CONNECTING,
                DebugRfcommAttemptGate.Phase.LINK_OPEN
            ) || Build.VERSION.SDK_INT < Build.VERSION_CODES.S
        ) return
        if (!qrCoordinator.readyForDebugLink(hostRole = false)) {
            linkStatus = "Complete fresh reciprocal Bluetooth QR preparation first."
            return
        }
        pendingLink = PendingLink.JOIN
        linkStatus = "Bluetooth connection permission required for an unverified socket link."
        if (hasConnectPermission()) {
            pendingLink = PendingLink.NONE
            startJoinLink()
        } else {
            connectPermissionPromptOpen = true
            linkPermissionRequest.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
        }
    }

    private fun launchDiscoverablePrompt() {
        if (!foreground || pendingLink != PendingLink.HOST ||
            !hasConnectPermission() || !hasAdvertisePermission()
        ) {
            pendingLink = PendingLink.NONE
            linkStatus = "Bluetooth permissions unavailable. Host not started."
            return
        }
        discoverabilityPromptOpen = true
        linkStatus = "Android asks you to make this phone discoverable for up to 120 seconds."
        try {
            discoverabilityRequest.launch(
                Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                    .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
            )
        } catch (_: RuntimeException) {
            discoverabilityPromptOpen = false
            pendingLink = PendingLink.NONE
            linkStatus = "Android discoverability request failed. No host started."
        }
    }

    @Suppress("MissingPermission")
    private fun startHostLink() {
        if (!foreground || !hasConnectPermission() ||
            !qrCoordinator.readyForDebugLink(hostRole = true)) return
        val hostAdapter = (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (hostAdapter == null || !hostAdapter.isEnabled) {
            linkStatus = "Bluetooth radio unavailable or disabled. No host started."
            return
        }
        val request = qrCoordinator.newTlsProbeInput(hostRole = true)
            ?: run {
                linkStatus = "QR preparation expired; start a fresh two-code exchange."
                return
            }
        if (linkController.host(hostAdapter, request)) {
            linkPhase = DebugRfcommAttemptGate.Phase.CONNECTING
            watchLinkPermission()
            linkStatus = "Listening for RFCOMM, then testing QR-bound encrypted control agreement."
        } else request.close()
    }

    @Suppress("MissingPermission")
    private fun startJoinLink() {
        if (!foreground || !hasConnectPermission() ||
            !qrCoordinator.readyForDebugLink(hostRole = false)) return
        val target = candidateLedger.selectedHandle() ?: return
        val joinAdapter = (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (joinAdapter == null || !joinAdapter.isEnabled) {
            linkStatus = "Bluetooth radio unavailable or disabled. No join attempted."
            return
        }
        // The user staged this unverified object during a previous scan.
        // It is NOT peer authentication. No app data may cross this socket.
        val request = qrCoordinator.newTlsProbeInput(hostRole = false)
            ?: run {
                linkStatus = "QR preparation expired; start a fresh two-code exchange."
                return
            }
        if (linkController.join(target, joinAdapter, request)) {
            clearCandidateMemory()
            linkPhase = DebugRfcommAttemptGate.Phase.CONNECTING
            watchLinkPermission()
            linkStatus = "Opening RFCOMM, then testing QR-bound encrypted control agreement."
        } else request.close()
    }

    private fun beginInquiry() {
        if (!foreground || busy || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        clearCandidateMemory() // new inquiry never inherits an old selection
        if (discoveryState.snapshot().phase == NearbyDiscoverySession.Phase.STOPPED) {
            discoveryState.resetAfterStop()
        }
        val token = discoveryState.begin(ConnectionMethod.BLUETOOTH, foreground)
            ?: return
        attempt = token
        busy = true
        observations = 0
        status = "Confirming your permission to start nearby Bluetooth inquiry."
        if (!discoveryState.personConsented(token, foreground)) {
            stopByUser()
            return
        }
        if (hasScanPermission()) {
            if (discoveryState.platformPermissionResult(token, foreground, granted = true)) {
                startInquiry(token)
            } else {
                stopByUser()
            }
        } else {
            scanPermission.launch(Manifest.permission.BLUETOOTH_SCAN)
        }
    }

    @Suppress("MissingPermission")
    private fun startInquiry(token: NearbyDiscoverySession.Attempt) {
        if (!foreground || !hasScanPermission()) {
            discoveryState.permissionRevoked(token)
            clearCandidateMemory()
            stopHardware()
            attempt = null
            busy = false
            status = "Permission or foreground access was lost. Search stopped."
            return
        }
        try {
            adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            val local = adapter
            if (local == null) {
                discoveryState.radioUnavailable(token)
                clearCandidateMemory()
                stopHardware()
                attempt = null
                busy = false
                status = "This phone has no available Bluetooth adapter."
                return
            }
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            // Bluetooth OS broadcasts may come from a privileged system app,
            // not the system UID. These events are never peer credentials.
            ContextCompat.registerReceiver(
                this, receiver, filter, ContextCompat.RECEIVER_EXPORTED
            )
            receiverRegistered = true
            val started = local.startDiscovery()
            // Mark actual hardware inquiry before validating lifecycle: if
            // validation rejects a state, stopHardware MUST still cancel radio.
            radioRunning = started
            if (!discoveryState.radioScanStarted(
                    token, foreground, started, SystemClock.elapsedRealtime()
                )
            ) {
                clearCandidateMemory()
                stopHardware()
                attempt = null
                busy = false
                status = "Bluetooth inquiry could not start. Check the radio is enabled."
                return
            }
            radioRunning = true
            candidateToken = candidateLedger.begin()
            handler.postDelayed(timeout, NearbyDiscoverySession.MAX_SCAN_MS + 100L)
            status = "Searching nearby Bluetooth radio signals. Tap Stop at any time."
        } catch (_: SecurityException) {
            discoveryState.permissionRevoked(token)
            clearCandidateMemory()
            stopHardware()
            attempt = null
            busy = false
            status = "Bluetooth permission unavailable. No scan started."
        } catch (_: RuntimeException) {
            discoveryState.radioUnavailable(token)
            clearCandidateMemory()
            stopHardware()
            attempt = null
            busy = false
            status = "Bluetooth discovery unavailable on this device."
        }
    }

    private fun stopByUser() {
        val token = attempt ?: return
        discoveryState.cancel(token)
        clearCandidateMemory()
        stopHardware()
        attempt = null
        busy = false
        status = "Search and results cleared. No connection was made."
    }

    @Suppress("DEPRECATION")
    private fun discoveredDevice(intent: Intent): BluetoothDevice? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(
                BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java
            )
        } else {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    } catch (_: RuntimeException) {
        // This broadcast is untrusted and may contain malformed extras.
        null
    }

    private fun finishCandidatesForReview() {
        val current = candidateToken ?: return
        candidateLedger.finish(current)
        candidateRows = candidateLedger.rows(current)
    }

    private fun clearCandidateMemory() {
        candidateLedger.clear()
        candidateToken = null
        candidateRows = emptyList()
        selectedOrdinal = null
    }

    private fun selectCandidate(ordinal: Int) {
        if (!foreground || !hasScanPermission()) {
            stopByUser()
            clearCandidateMemory()
            return
        }
        val token = candidateToken ?: return
        if (!candidateLedger.select(token, ordinal)) return
        // Selection is deliberately not peer verification. Stop inquiry now.
        attempt?.let { discoveryState.cancel(it) }
        stopHardware()
        attempt = null
        busy = false
        candidateToken = null
        candidateRows = emptyList()
        selectedOrdinal = candidateLedger.selectedOrdinal()
        status = "Unverified signal staged on this phone only. " +
            "Verified Bluetooth messaging is not implemented; a separate debug " +
            "socket connection may be attempted with your permission."
    }

    @Suppress("MissingPermission")
    private fun stopHardware() {
        handler.removeCallbacks(timeout)
        if (radioRunning && hasScanPermission()) {
            try {
                adapter?.cancelDiscovery()
            } catch (_: SecurityException) {
                // Permission may have been revoked concurrently.
            } catch (_: RuntimeException) {
                // Do not crash teardown if a device's Bluetooth stack fails.
            }
        }
        radioRunning = false
        if (receiverRegistered) {
            try {
                unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {
                // Receiver was already unregistered by platform teardown.
            }
            receiverRegistered = false
        }
        adapter = null
    }
}
