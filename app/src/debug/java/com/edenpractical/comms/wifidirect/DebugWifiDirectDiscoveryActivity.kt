package com.edenpractical.comms.wifidirect

import com.edenpractical.comms.audio.DebugNearbyVoiceOwner
import com.edenpractical.comms.audio.DebugNearbyVoicePanel
import com.edenpractical.comms.ui.DebugVerifiedConversationPanel
import com.edenpractical.comms.bluetooth.DebugBluetoothApprovedTextChannel

import com.edenpractical.comms.privacy.DebugVerifiedNearbyPrivacy
import com.edenpractical.comms.privacy.DebugVerifiedNearbyPrivacyPanel

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pDevice
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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

/**
 * M3q debug-only bounded foreground P2P discovery. No requestPeers/peer
 * identifiers, group creation, sockets, QR trust, user text or voice.
 *
 * Android broadcasts are hints, NOT authenticated session or permission facts.
 * Positive radio state on API29+ comes only from requestP2pState callbacks.
 * A successful discoverPeers callback means the OS accepted a request, NOT
 * that a peer was detected or that a connection was established.
 */
class DebugWifiDirectDiscoveryActivity : ComponentActivity() {
    private val gate = DebugWifiDirectDiscoveryGate()
    private val preparation = DebugWifiDirectQrCoordinator()
    private val verifiedChat = DebugWifiDirectChatUiState()
    private val localPrivacy by lazy { DebugVerifiedNearbyPrivacy(applicationContext) }
    private val localVoice by lazy { DebugNearbyVoiceOwner(applicationContext) }
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var foreground = false
    private var registered = false
    private var manager: WifiP2pManager? = null
    private var channel: WifiP2pManager.Channel? = null
    private var token: DebugWifiDirectDiscoveryGate.Token? = null
    private var discoveryRequested = false
    private var timeoutTask: Runnable? = null
    private var monitorTask: Runnable? = null
    private val candidateLedger = DebugWifiDirectCandidateLedger<WifiP2pDevice>()
    private var candidateToken: DebugWifiDirectCandidateLedger.Token? = null
    private var candidateReader: DebugWifiDirectCandidateReader? = null
    private var candidateRows by mutableStateOf<List<DebugWifiDirectCandidateLedger.Row>>(
        emptyList()
    )
    private var candidateNotice by mutableStateOf(
        "Candidates appear only while this explicit search is running."
    )
    private var targetController: DebugWifiDirectTargetLinkController? = null
    private var targetNotice by mutableStateOf(
        "No unverified Wi-Fi Direct link test has been started."
    )

    private var phase by mutableStateOf(DebugWifiDirectDiscoveryGate.Phase.IDLE)
    private var eventCount by mutableIntStateOf(0)
    private var radioState by mutableStateOf<Boolean?>(null)
    private var explanation by mutableStateOf(
        "No Wi-Fi Direct search has started. Nearby devices are unverified."
    )

    private fun permissionName(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.NEARBY_WIFI_DEVICES else Manifest.permission.ACCESS_FINE_LOCATION

    private fun prerequisites(): Boolean = try {
        val location = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            (getSystemService(Context.LOCATION_SERVICE) as? LocationManager)?.isLocationEnabled
        } else {
            Settings.Secure.getInt(
                contentResolver, Settings.Secure.LOCATION_MODE,
                Settings.Secure.LOCATION_MODE_OFF
            ) != Settings.Secure.LOCATION_MODE_OFF
        }
        packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT) &&
            getSystemService(Context.WIFI_P2P_SERVICE) is WifiP2pManager &&
            (getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.isWifiEnabled == true &&
            location == true &&
            ContextCompat.checkSelfPermission(this, permissionName()) ==
                PackageManager.PERMISSION_GRANTED
    } catch (_: RuntimeException) {
        false
    }

    private fun updateView() {
        phase = gate.snapshot().phase
        eventCount = gate.snapshot().events
    }

    private fun finishAttempt(
        current: DebugWifiDirectDiscoveryGate.Token,
        reason: DebugWifiDirectDiscoveryGate.End,
        notice: String
    ) {
        if (token !== current) return
        gate.stop(current, reason)
        updateView()
        token = null // Reject all stale Android callbacks before touching radio.
        candidateReader?.close()
        candidateReader = null
        candidateToken = null
        candidateLedger.clear()
        candidateRows = emptyList()
        candidateNotice = "All temporary device candidates discarded."
        timeoutTask?.let(handler::removeCallbacks)
        monitorTask?.let(handler::removeCallbacks)
        timeoutTask = null
        monitorTask = null
        val activeChannel = channel
        val activeManager = manager
        channel = null
        manager = null
        val wasRequested = discoveryRequested
        discoveryRequested = false
        if (activeChannel != null) {
            if (wasRequested) {
                try {
                    activeManager?.stopPeerDiscovery(
                        activeChannel, object : WifiP2pManager.ActionListener {
                            override fun onSuccess() = Unit
                            override fun onFailure(reason: Int) = Unit
                        }
                    )
                } catch (_: RuntimeException) {
                    // Best-effort cancellation; channel closure is mandatory.
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                try { activeChannel.close() } catch (_: RuntimeException) { }
            }
        }
        explanation = notice
    }

    private fun stopByUser() {
        val active = token ?: return
        finishAttempt(active, DebugWifiDirectDiscoveryGate.End.STOP_BUTTON,
            "Stopped by you. Any late callbacks are ignored.")
    }

    private fun onRadioReported(
        active: DebugWifiDirectDiscoveryGate.Token,
        enabled: Boolean
    ) {
        if (token !== active || !gate.active(active)) return
        radioState = enabled
        if (!enabled) {
            finishAttempt(active, DebugWifiDirectDiscoveryGate.End.RADIO_LOST,
                "Android reports Wi-Fi Direct disabled; search stopped.")
            return
        }
        if (gate.observedRadio(active, true)) {
            sendDiscoveryRequest(active)
        }
    }

    private fun sendDiscoveryRequest(active: DebugWifiDirectDiscoveryGate.Token) {
        if (token !== active || !foreground || !prerequisites()) {
            if (token === active) finishAttempt(
                active, DebugWifiDirectDiscoveryGate.End.PERMISSION_LOST,
                "Wi-Fi, Location Mode or permission changed; no discovery started."
            )
            return
        }
        val m = manager ?: return
        val c = channel ?: return
        if (!gate.requestSent(active)) return
        discoveryRequested = true
        updateView()
        explanation = "Android discovery request sent. No device is trusted or connected."
        try {
            m.discoverPeers(c, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    if (token !== active || !gate.active(active)) return
                    if (!foreground || !prerequisites()) {
                        finishAttempt(active, DebugWifiDirectDiscoveryGate.End.PERMISSION_LOST,
                            "Readiness changed during discovery; search stopped.")
                        return
                    }
                    if (gate.discoveryAccepted(active)) {
                        updateView()
                        explanation = "Android accepted discovery. This does NOT prove a peer exists."
                        requestCandidateSnapshot(active)
                    }
                }
                override fun onFailure(reason: Int) {
                    if (token !== active) return
                    finishAttempt(active, DebugWifiDirectDiscoveryGate.End.PLATFORM_FAILURE,
                        "Android declined the discovery request (code $reason).")
                }
            })
        } catch (_: RuntimeException) {
            finishAttempt(active, DebugWifiDirectDiscoveryGate.End.PLATFORM_FAILURE,
                "Android Wi-Fi Direct discovery unavailable; test closed.")
        }
    }

    /**
     * M3r: a peer-list read is permitted ONLY after the active, user-started
     * search reached SCANNING and all Android prerequisites still hold.
     * The Android device objects never leave private transient memory.
     */
    private fun requestCandidateSnapshot(active: DebugWifiDirectDiscoveryGate.Token) {
        if (token !== active || !foreground || !prerequisites() ||
            gate.snapshot().phase != DebugWifiDirectDiscoveryGate.Phase.SCANNING
        ) return
        val currentReader = candidateReader ?: return
        val currentToken = candidateToken ?: return
        val activeManager = manager ?: return
        val activeChannel = channel ?: return
        currentReader.request(
            manager = activeManager,
            channel = activeChannel,
            authorized = {
                token === active && foreground && prerequisites() &&
                    gate.active(active) &&
                    gate.snapshot().phase == DebugWifiDirectDiscoveryGate.Phase.SCANNING &&
                    candidateReader === currentReader &&
                    candidateToken === currentToken
            },
            onSnapshot = { snapshot ->
                if (candidateLedger.observe(currentToken, snapshot)) {
                    candidateRows = candidateLedger.rows(currentToken)
                    candidateNotice = "These are nearby Wi-Fi Direct devices, NOT verified " +
                        "Eden Comms contacts. Pick one only for a local preview."
                } else {
                    candidateRows = candidateLedger.rows(currentToken)
                }
            }
        )
    }

    private fun selectCandidate(ordinal: Int) {
        val active = token ?: return
        val currentToken = candidateToken ?: return
        if (!foreground || !prerequisites() || !gate.active(active) ||
            gate.snapshot().phase != DebugWifiDirectDiscoveryGate.Phase.SCANNING
        ) return
        if (candidateLedger.choose(currentToken, ordinal)) {
            candidateRows = candidateLedger.rows(currentToken)
            candidateNotice = "Unverified device $ordinal selected for this on-screen " +
                "preview only. No connection or verification is available."
        }
    }

    /**
     * Two distinct human actions required: first selecting a numbered
     * UNVERIFIED candidate, then explicitly requesting a radio link.
     * The M3r ledger erases all candidate handles BEFORE the one-time
     * device object is passed into the separate M3s-C1 Android controller.
     */
    private fun requestUnverifiedLink() {
        val active = token ?: return
        val selectedToken = candidateToken ?: return
        if (targetController != null ||
            !preparation.readyForVerifiedTransport(hostRole = false) ||
            !foreground || !registered || !prerequisites() ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !gate.active(active) ||
            gate.snapshot().phase != DebugWifiDirectDiscoveryGate.Phase.SCANNING ||
            candidateLedger.selectedOrdinal(selectedToken) == null
        ) return
        candidateLedger.consumeForOneShotLink(selectedToken) { selectedDevice ->
            finishAttempt(
                active, DebugWifiDirectDiscoveryGate.End.STOP_BUTTON,
                "Discovery stopped for one explicitly requested UNVERIFIED link test."
            )
            var ownedController: DebugWifiDirectTargetLinkController? = null
            val controller = DebugWifiDirectTargetLinkController(
                context = this,
                readyAndForeground = {
                    foreground && registered && prerequisites()
                },
                onReport = { message -> targetNotice = message },
                onFinished = {
                    targetController = null
                    verifiedChat.clear()
                    localPrivacy.close()
                    localVoice.disable()
                    preparation.clear()
                    targetNotice += " Fresh QR and discovery required for another session."
                },
                onVerifiedStatus = { phase, message ->
                    verifiedChat.status(phase, message)
                    if (phase == DebugWifiDirectApprovedTcpSession.Phase.STOPPED) {
                        localPrivacy.close()
                        localVoice.disable()
                    }
                },
                onVerifiedApproval = { verifiedChat.approval(it) },
                onVerifiedText = { event ->
                    verifiedChat.event(event)
                    localPrivacy.receiveEncryptedEvent(event)
                    when (event) {
                        DebugBluetoothApprovedTextChannel.Event.Ready ->
                            localVoice.activate(
                                canContinue = {
                                    foreground && targetController === ownedController &&
                                        verifiedChat.phase ==
                                            DebugWifiDirectApprovedTcpSession.Phase.CHAT &&
                                        prerequisites()
                                },
                                requestFloor = {
                                    ownedController?.requestVerifiedVoiceFloor()
                                        ?: com.edenpractical.comms.transport.DebugLanVoiceFloor.Ask.BUSY
                                },
                                cancelFloor = {
                                    ownedController?.cancelVerifiedVoiceFloor()
                                },
                                beginStream = {
                                    ownedController?.beginVerifiedLiveVoice() == true
                                },
                                offerFrame = { pcm ->
                                    ownedController?.offerVerifiedLiveVoiceFrame(pcm) == true
                                },
                                finishStream = {
                                    ownedController?.finishVerifiedLiveVoice() == true
                                }
                            )
                        is DebugBluetoothApprovedTextChannel.Event.VoiceReceived ->
                            Unit // Live streaming frame events are played instead
                        is DebugBluetoothApprovedTextChannel.Event.LiveVoiceFrame ->
                            localVoice.receiveLiveFrame(event.kind, event.pcm)
                        DebugBluetoothApprovedTextChannel.Event.FloorGranted ->
                            localVoice.floorGranted()
                        DebugBluetoothApprovedTextChannel.Event.FloorDenied ->
                            localVoice.floorDenied()
                        is DebugBluetoothApprovedTextChannel.Event.VoiceWritten ->
                            localVoice.voiceWritten(event.frames)
                        else -> Unit
                    }
                },
                onApprovedIdentity = { self, peer ->
                    localPrivacy.openVerified(self, peer)
                }
            )
            ownedController = controller
            targetController = controller
            if (!controller.startSelected(selectedDevice, verifiedQrPrepared = true)) {
                targetController = null
                targetNotice = "Link test could not start; nothing was connected."
            }
        }
    }

    private fun requestVerifiedP2pText() {
        val controller = targetController ?: return
        if (!foreground || !preparation.readyForVerifiedTransport(false) ||
            !controller.canStartVerifiedText()
        ) return
        val request = preparation.newVerifiedTransportRequest(false)
            ?: run {
                targetNotice = "Three fresh QR codes required before authenticated text."
                return
            }
        if (!controller.startVerifiedText(request)) {
            targetNotice = "Encrypted P2P text couldn't start. Use fresh QR to retry."
        }
    }

    private fun sendVerifiedText() {
        if (verifiedChat.phase != DebugWifiDirectApprovedTcpSession.Phase.CHAT) return
        val draft = verifiedChat.draft
        if (draft.isBlank()) return
        if (targetController?.offerVerifiedText(draft) == true)
            verifiedChat.draft = ""
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!foreground) return
            val active = token ?: return
            if (!gate.active(active)) return
            when (intent?.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(
                        WifiP2pManager.EXTRA_WIFI_STATE,
                        WifiP2pManager.WIFI_P2P_STATE_DISABLED
                    )
                    if (state != WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                        onRadioReported(active, false)
                    } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        // API26-28 has no requestP2pState; rely on foreground
                        // platform state broadcasts only for this diagnostic.
                        onRadioReported(active, true)
                    }
                }
                WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(
                        WifiP2pManager.EXTRA_DISCOVERY_STATE,
                        WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED
                    )
                    if (state == WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED &&
                        gate.snapshot().phase == DebugWifiDirectDiscoveryGate.Phase.SCANNING
                    ) {
                        finishAttempt(active, DebugWifiDirectDiscoveryGate.End.RADIO_LOST,
                            "Android stopped Wi-Fi Direct discovery.")
                    }
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    if (!prerequisites()) {
                        finishAttempt(active, DebugWifiDirectDiscoveryGate.End.PERMISSION_LOST,
                            "Permission or radio readiness lost; search stopped.")
                    } else if (gate.peerListChanged(active)) {
                        updateView() // Number of change EVENTS, never peer count.
                        requestCandidateSnapshot(active)
                    } else if (!gate.active(active)) {
                        finishAttempt(active, DebugWifiDirectDiscoveryGate.End.EVENT_LIMIT,
                            "Event safety limit reached; search stopped.")
                    }
                    // Do not read peer-list extras or perform device-list lookups.
                }
            }
        }
    }

    private fun startUserSearch() {
        if (!foreground || !registered || token != null || targetController != null) return
        if (gate.snapshot().phase == DebugWifiDirectDiscoveryGate.Phase.STOPPED) {
            gate.reset()
        }
        if (!prerequisites()) {
            explanation = "Permission, Location Mode, Wi-Fi or P2P support missing. " +
                "Return to permission preflight and recheck."
            return
        }
        val active = gate.begin(foreground, prerequisites()) ?: return
        token = active
        candidateLedger.clear()
        candidateToken = candidateLedger.begin()
        candidateReader = DebugWifiDirectCandidateReader()
        candidateRows = emptyList()
        candidateNotice = "Waiting for an Android peer-list snapshot; nothing is trusted."
        if (candidateToken == null) {
            finishAttempt(active, DebugWifiDirectDiscoveryGate.End.PLATFORM_FAILURE,
                "Could not allocate a fresh candidate session; no scan started.")
            return
        }
        radioState = null
        updateView()
        explanation = "Checking Android Wi-Fi Direct radio state; no search yet."
        val m = getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        if (m == null) {
            finishAttempt(active, DebugWifiDirectDiscoveryGate.End.RADIO_LOST,
                "Wi-Fi Direct platform service unavailable.")
            return
        }
        manager = m
        try {
            channel = m.initialize(this, Looper.getMainLooper(),
                WifiP2pManager.ChannelListener {
                    if (token === active) finishAttempt(
                        active, DebugWifiDirectDiscoveryGate.End.CHANNEL_LOST,
                        "Wi-Fi Direct platform channel disconnected."
                    )
                })
        } catch (_: RuntimeException) {
            finishAttempt(active, DebugWifiDirectDiscoveryGate.End.CHANNEL_LOST,
                "Wi-Fi Direct platform channel could not start.")
            return
        }
        val c = channel
        if (c == null) {
            finishAttempt(active, DebugWifiDirectDiscoveryGate.End.CHANNEL_LOST,
                "Wi-Fi Direct platform returned no channel.")
            return
        }

        timeoutTask = Runnable {
            finishAttempt(active, DebugWifiDirectDiscoveryGate.End.TIMEOUT,
                "30-second search limit reached; discovery closed.")
        }.also { handler.postDelayed(it, DebugWifiDirectDiscoveryGate.MAX_MS) }

        fun scheduleMonitor() {
            monitorTask = Runnable {
                if (token !== active || !gate.active(active)) return@Runnable
                if (!foreground || !prerequisites()) {
                    finishAttempt(active, DebugWifiDirectDiscoveryGate.End.PERMISSION_LOST,
                        "Screen or permission/radio state changed; search stopped.")
                } else {
                    scheduleMonitor()
                }
            }.also { handler.postDelayed(it, 1_000L) }
        }
        scheduleMonitor()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                m.requestP2pState(c) { enabledState ->
                    if (token === active && gate.active(active)) {
                        onRadioReported(
                            active, enabledState == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                        )
                    }
                }
            } catch (_: RuntimeException) {
                finishAttempt(active, DebugWifiDirectDiscoveryGate.End.RADIO_LOST,
                    "Cannot confirm the Android Wi-Fi Direct radio state.")
            }
        } else {
            explanation = "Waiting for Android Wi-Fi Direct enabled-state broadcast; " +
                "no search starts until it is observed."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) {
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        preparation.loadExistingIdentity()
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            if (verifiedChat.phase == DebugWifiDirectApprovedTcpSession.Phase.CHAT)
                                "Wi-Fi Direct conversation" else "Join a Wi-Fi Direct connection",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text("DEBUG ONLY • No router required. " +
                            "A radio candidate is NOT identity or consent.")
                        if (verifiedChat.phase == DebugWifiDirectApprovedTcpSession.Phase.CHAT) {
                            DebugVerifiedConversationPanel(
                                method = "Wi-Fi Direct",
                                onDisconnect = {
                                    localVoice.disable()
                                    targetController?.stop(
                                        DebugWifiDirectTargetLinkGate.End.USER_STOP
                                    )
                                    stopByUser()
                                },
                                textContent = {
                                    DebugWifiDirectTextPanel(
                                        lines = verifiedChat.lines,
                                        draft = verifiedChat.draft,
                                        onDraft = { verifiedChat.draft = it },
                                        onSend = { sendVerifiedText() }
                                    )
                                },
                                talkContent = { DebugNearbyVoicePanel(localVoice) },
                                privacyContent = { DebugVerifiedNearbyPrivacyPanel(localPrivacy) }
                            )
                        } else {
                        if (verifiedChat.phase !in setOf(
                                DebugWifiDirectApprovedTcpSession.Phase.CONNECTING,
                                DebugWifiDirectApprovedTcpSession.Phase.AWAITING_APPROVAL,
                                DebugWifiDirectApprovedTcpSession.Phase.CHAT
                            )) {
                            DebugWifiDirectInlineQrPanel(preparation, hostRole = false)
                        } else Text("Fresh QR proof locked during the P2P connection.")
                        Text(explanation)
                        Text("Platform P2P radio observed: " + when (radioState) {
                            true -> "Enabled"
                            false -> "Disabled"
                            null -> "Not confirmed"
                        })
                        Text("Discovery status: $phase")
                        Text("Anonymous peer-list change events: $eventCount (NOT devices)")
                        Text(candidateNotice)
                        Text("Temporary unverified devices: ${candidateRows.size} (max 8)")
                        for (candidate in candidateRows) {
                            OutlinedButton(
                                onClick = { selectCandidate(candidate.ordinal) },
                                enabled = token != null && foreground &&
                                    phase == DebugWifiDirectDiscoveryGate.Phase.SCANNING,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            ) {
                                Text(
                                    candidate.label +
                                        if (candidate.selected) " • LOCAL PREVIEW SELECTED"
                                        else " • SELECT FOR PREVIEW"
                                )
                            }
                        }
                        Text("OPTIONAL DEVELOPMENT LINK • A selected nearby radio could " +
                            "be the wrong phone. QR preparation does NOT bind this " +
                            "candidate to an Eden Comms identity. Android may show " +
                            "a system Wi-Fi Direct invitation. No messages are sent.")
                        Button(
                            onClick = { requestUnverifiedLink() },
                            enabled = foreground && token != null &&
                                preparation.readyForVerifiedTransport(false) &&
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                phase == DebugWifiDirectDiscoveryGate.Phase.SCANNING &&
                                candidateToken?.let {
                                    candidateLedger.selectedOrdinal(it)
                                } != null &&
                                targetController == null,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) { Text("JOIN SELECTED WI-FI DIRECT GROUP") }
                        Text(targetNotice)
                        Text(verifiedChat.note)
                        if (verifiedChat.approvalPending) {
                            Text("Actual TLS/QR/exporter proofs passed. " +
                                "Approve only when beside the intended person.")
                            Button(
                                onClick = {
                                    if (targetController?.approveVerified() == true)
                                        verifiedChat.approval(false)
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            ) { Text("APPROVE THIS P2P SESSION") }
                            OutlinedButton(
                                onClick = {
                                    if (targetController?.declineVerified() == true)
                                        verifiedChat.approval(false)
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            ) { Text("DECLINE AND DISCONNECT") }
                        }
                         Button(
                            onClick = { requestVerifiedP2pText() },
                            enabled = foreground &&
                                preparation.readyForVerifiedTransport(false) &&
                                targetController?.canStartVerifiedText() == true,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) { Text("START AUTHENTICATED TEST SESSION") }
                        OutlinedButton(
                            onClick = { targetController?.requestTcpJoinReachability() },
                            enabled = foreground &&
                                targetController?.canAttemptTcp() == true,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) { Text("TEST ONE TCP CONNECTION — NO DATA OR TRUST") }
                        OutlinedButton(
                            onClick = {
                                targetController?.stop(
                                    DebugWifiDirectTargetLinkGate.End.USER_STOP
                                )
                            },
                            enabled = targetController != null,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) { Text("STOP LINK AND REQUEST GROUP REMOVAL") }
                        Button(
                            onClick = { startUserSearch() },
                            enabled = foreground && registered && token == null &&
                                preparation.readyForVerifiedTransport(false) &&
                                targetController == null,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) { Text("START 30-SECOND SEARCH") }
                        OutlinedButton(
                            onClick = { stopByUser() },
                            enabled = token != null,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) { Text("STOP SEARCH") }
                        Text("Android radio candidates are temporary, untrusted " +
                            "and never remembered. Debug text and live push-to-talk " +
                            "require fresh in-person QR, pinned TLS and both people's " +
                            "approvals. No background connection or production security claim.")
                        } // Hide anonymous radio diagnostics while chatting.
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        foreground = true
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        }
        try {
            // Wi-Fi P2P state is emitted by Android privileged system services.
            // Receiver data never grants trust or authenticates a peer.
            ContextCompat.registerReceiver(
                this, receiver, filter, ContextCompat.RECEIVER_EXPORTED
            )
            registered = true
        } catch (_: RuntimeException) {
            explanation = "Cannot monitor Android P2P state. Search unavailable."
            registered = false
        }
    }

    override fun onStop() {
        foreground = false
        targetController?.stop(DebugWifiDirectTargetLinkGate.End.BACKGROUND)
        verifiedChat.clear()
        localPrivacy.close()
        localVoice.disable()
        preparation.clear()
        token?.let {
            finishAttempt(it, DebugWifiDirectDiscoveryGate.End.BACKGROUND,
                "Left screen; the foreground-only search was stopped.")
        }
        if (registered) {
            try { unregisterReceiver(receiver) } catch (_: RuntimeException) { }
            registered = false
        }
        super.onStop()
    }

    override fun onDestroy() {
        localVoice.disable()
        localPrivacy.dispose()
        super.onDestroy()
    }
}
