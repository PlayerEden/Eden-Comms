package com.edenpractical.comms.wifidirect

import android.content.Context
import android.content.pm.PackageManager
import android.net.MacAddress
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.edenpractical.comms.bluetooth.DebugBluetoothTlsProbeInput
import com.edenpractical.comms.bluetooth.DebugBluetoothApprovedTextChannel

/**
 * M3s-C1 DEBUG-ONLY targeted Android radio negotiation. It deliberately knows
 * nothing about QR identities, TLS, user messages or voice. F3-A adds one
 * opt-in TCP handshake-only test to Android's OS-reported group owner address.
 * An address or successful TCP handshake is not peer identity or consent.
 *
 * The selected opaque Android device is used just once to produce an in-RAM
 * nonpersistent P2P config. A group formed flag is NOT a verified peer.
 * Android group removal and cancellation are best-effort, not guaranteed.
 */
internal class DebugWifiDirectTargetLinkController(
    private val context: Context,
    private val readyAndForeground: () -> Boolean,
    private val onReport: (String) -> Unit,
    private val onFinished: () -> Unit,
    private val onVerifiedStatus: (DebugWifiDirectApprovedTcpSession.Phase, String) -> Unit =
        { _, _ -> },
    private val onVerifiedApproval: (Boolean) -> Unit = {},
    private val onVerifiedText: (DebugBluetoothApprovedTextChannel.Event) -> Unit = {},
    // Optional UI-only record after confirmed same-session TLS, never a pin allowlist.
    private val onApprovedIdentity: (ByteArray, ByteArray) -> Unit = { _, _ -> }
) {
    private val gate = DebugWifiDirectTargetLinkGate()
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var current: DebugWifiDirectTargetLinkGate.Token? = null
    private var manager: WifiP2pManager? = null
    private var channel: WifiP2pManager.Channel? = null
    private var deadline: Runnable? = null
    private var monitor: Runnable? = null
    private var cleanupDeadline: Runnable? = null
    private var cleaned = false
    private var inspectedInitialGroup = false
    private var connectionInfoOutstanding = false
    private var tcpProbe: DebugWifiDirectTcpProbe? = null
    private var tcpAttempted = false
    private var verifiedStarted = false
    private var verifiedSession: DebugWifiDirectApprovedTcpSession? = null
    private var pendingVerifiedRequest: DebugBluetoothTlsProbeInput? = null

    fun busy(): Boolean = current != null ||
        gate.snapshot().phase == DebugWifiDirectTargetLinkGate.Phase.CLEANING

    fun startSelected(target: WifiP2pDevice, verifiedQrPrepared: Boolean = false): Boolean {
        val token = gate.start(
            confirmed = true,
            foreground = readyAndForeground(),
            ready = readyAndForeground(),
            api = Build.VERSION.SDK_INT
        ) ?: return false
        current = token
        cleaned = false
        inspectedInitialGroup = false
        tcpAttempted = false
        verifiedStarted = false
        onReport("Checking for a pre-existing Wi-Fi Direct group. " +
            "The selected device is NOT verified.")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            !context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)
        ) {
            stop(DebugWifiDirectTargetLinkGate.End.PLATFORM_FAILURE)
            return true
        }
        val m = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        if (m == null) {
            stop(DebugWifiDirectTargetLinkGate.End.PLATFORM_FAILURE)
            return true
        }
        manager = m
        try {
            channel = m.initialize(context, Looper.getMainLooper(),
                WifiP2pManager.ChannelListener {
                    if (current === token) stop(DebugWifiDirectTargetLinkGate.End.CHANNEL_LOST)
                })
        } catch (_: RuntimeException) {
            stop(DebugWifiDirectTargetLinkGate.End.CHANNEL_LOST)
            return true
        }
        val c = channel
        if (c == null) {
            stop(DebugWifiDirectTargetLinkGate.End.CHANNEL_LOST)
            return true
        }
        deadline = Runnable {
            if (current === token)
                stop(DebugWifiDirectTargetLinkGate.End.TIMEOUT)
        }.also {
            handler.postDelayed(it,
                if (verifiedQrPrepared) 120_000L
                else DebugWifiDirectTargetLinkGate.MAX_LINK_MS
            )
        }

        // Only Android 10+ can positively report current P2P radio state in
        // this flow. Never infer positive state from a neighboring broadcast.
        try {
            m.requestP2pState(c) { state ->
                if (current !== token || !gate.active(token)) return@requestP2pState
                if (!readyAndForeground() ||
                    state != WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                    stop(DebugWifiDirectTargetLinkGate.End.RADIO_LOST)
                } else {
                    checkExistingGroupThenConnect(token, target)
                }
            }
        } catch (_: RuntimeException) {
            stop(DebugWifiDirectTargetLinkGate.End.RADIO_LOST)
        }

        fun scheduleMonitor() {
            monitor = Runnable {
                if (current !== token || !gate.active(token)) return@Runnable
                if (!readyAndForeground()) {
                    stop(DebugWifiDirectTargetLinkGate.End.PERMISSION_LOST)
                } else {
                    val phase = gate.snapshot().phase
                    if (phase in setOf(
                            DebugWifiDirectTargetLinkGate.Phase.REQUESTING,
                            DebugWifiDirectTargetLinkGate.Phase.WAITING,
                            DebugWifiDirectTargetLinkGate.Phase.GROUP_OBSERVED
                        )
                    ) inspectConnectionInfo(token)
                    scheduleMonitor()
                }
            }.also { handler.postDelayed(it, 1_000L) }
        }
        scheduleMonitor()
        return true
    }

    /** Existing groups are never replaced or knowingly removed by this test. */
    private fun checkExistingGroupThenConnect(
        token: DebugWifiDirectTargetLinkGate.Token,
        target: WifiP2pDevice
    ) {
        val m = manager ?: return
        val c = channel ?: return
        if (current !== token || gate.snapshot().phase !=
            DebugWifiDirectTargetLinkGate.Phase.CHECKING_EXISTING
        ) return
        try {
            m.requestGroupInfo(c) { group ->
                if (current !== token || !gate.active(token) || inspectedInitialGroup) return@requestGroupInfo
                inspectedInitialGroup = true
                if (!readyAndForeground()) {
                    stop(DebugWifiDirectTargetLinkGate.End.PERMISSION_LOST)
                    return@requestGroupInfo
                }
                if (!gate.checkedExisting(token, group != null)) {
                    finishWithoutSubmission(
                        "Another Wi-Fi Direct group was already present. It was left untouched."
                    )
                } else {
                    sendConnectRequest(token, target)
                }
            }
        } catch (_: RuntimeException) {
            stop(DebugWifiDirectTargetLinkGate.End.PLATFORM_FAILURE)
        }
    }

    private fun sendConnectRequest(
        token: DebugWifiDirectTargetLinkGate.Token,
        target: WifiP2pDevice
    ) {
        if (current !== token || !readyAndForeground()) {
            stop(DebugWifiDirectTargetLinkGate.End.PERMISSION_LOST)
            return
        }
        val m = manager ?: return
        val c = channel ?: return
        if (!gate.submit(token)) return

        // This is the *only* device-identifier read in M3s-C1, used in a
        // short-lived platform configuration; never in UI, logs, storage, QR,
        // clipboard or an application transport.
        val config = try {
            WifiP2pConfig.Builder()
                .setDeviceAddress(MacAddress.fromString(target.deviceAddress))
                .enablePersistentMode(false)
                .build()
        } catch (_: RuntimeException) {
            stop(DebugWifiDirectTargetLinkGate.End.PLATFORM_FAILURE)
            return
        }
        onReport("Android radio negotiation requested for the selected UNVERIFIED device. " +
            "No QR/TLS identity proof or chat is involved.")
        try {
            m.connect(c, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    if (current !== token || !gate.active(token)) return
                    if (gate.requestAccepted(token)) {
                        onReport("Android accepted the connection REQUEST. " +
                            "This does not mean any group formed or peer was authenticated.")
                    }
                    inspectConnectionInfo(token)
                }
                override fun onFailure(reason: Int) {
                    if (current === token) stop(DebugWifiDirectTargetLinkGate.End.PLATFORM_FAILURE)
                }
            })
        } catch (_: RuntimeException) {
            stop(DebugWifiDirectTargetLinkGate.End.PLATFORM_FAILURE)
        }
    }

    private fun inspectConnectionInfo(token: DebugWifiDirectTargetLinkGate.Token) {
        if (connectionInfoOutstanding || current !== token || !gate.active(token)) return
        val m = manager ?: return
        val c = channel ?: return
        connectionInfoOutstanding = true
        try {
            m.requestConnectionInfo(c) { info ->
                connectionInfoOutstanding = false
                if (current !== token || !gate.active(token)) return@requestConnectionInfo
                if (!readyAndForeground()) {
                    stop(DebugWifiDirectTargetLinkGate.End.PERMISSION_LOST)
                    return@requestConnectionInfo
                }
                if (gate.observedGroup(token, info.groupFormed)) {
                    onReport("Android reports a Wi-Fi Direct group FORMED. " +
                        "Device identity remains UNVERIFIED. No sockets or messages " +
                        "were created by group formation; optional TCP test is separate.")
                } else if (gate.snapshot().phase ==
                    DebugWifiDirectTargetLinkGate.Phase.GROUP_OBSERVED &&
                    !info.groupFormed
                ) {
                    stop(DebugWifiDirectTargetLinkGate.End.RADIO_LOST)
                }
            }
        } catch (_: RuntimeException) {
            connectionInfoOutstanding = false
            stop(DebugWifiDirectTargetLinkGate.End.PLATFORM_FAILURE)
        }
    }

    /** F3-A: user requests one TCP handshake to Android's group owner.
     * No QR/TLS identity or content transfers; a positive result is not trust.
     */
    fun canAttemptTcp(): Boolean = current != null && !tcpAttempted &&
        gate.snapshot().phase == DebugWifiDirectTargetLinkGate.Phase.GROUP_OBSERVED &&
        readyAndForeground()

    fun requestTcpJoinReachability(): Boolean {
        val token = current ?: return false
        if (!canAttemptTcp() || tcpProbe != null) return false
        val m = manager ?: return false
        val c = channel ?: return false
        tcpAttempted = true
        onReport("Checking the joining phone's actual P2P interface. No user data.")
        try {
            m.requestConnectionInfo(c) { info ->
                if (current !== token || !readyAndForeground() ||
                    gate.snapshot().phase != DebugWifiDirectTargetLinkGate.Phase.GROUP_OBSERVED
                ) return@requestConnectionInfo
                val ownerAddress = info.groupOwnerAddress
                if (!info.groupFormed || info.isGroupOwner || ownerAddress == null) {
                    onReport("Not a confirmed joining phone with an OS group owner; " +
                        "no TCP connection was attempted.")
                    return@requestConnectionInfo
                }
                try {
                    m.requestGroupInfo(c) { group ->
                        if (current !== token || !readyAndForeground() ||
                            gate.snapshot().phase !=
                                DebugWifiDirectTargetLinkGate.Phase.GROUP_OBSERVED
                        ) return@requestGroupInfo
                        if (group == null || group.isGroupOwner) {
                            onReport("No joining P2P group interface; no TCP connection.")
                            return@requestGroupInfo
                        }
                        // The specific OS P2P interface must own a private
                        // IPv4 address within the group-owner subnet. This
                        // prevents an accidental default-Wi-Fi/cellular dial.
                        val local = DebugWifiDirectP2pInterface.resolve(
                            group.getInterface(), isHost = false,
                            groupOwnerAddress = ownerAddress
                        )
                        if (local == null) {
                            onReport("Android's joining P2P interface address is " +
                                "unavailable or incompatible. No socket was opened.")
                            return@requestGroupInfo
                        }
                        val probe = DebugWifiDirectTcpProbe()
                        tcpProbe = probe
                        if (!probe.start(
                                DebugWifiDirectTcpProbe.Role.JOIN, ownerAddress,
                                onCompleted = { reached ->
                                    handler.post {
                                        if (current !== token || tcpProbe !== probe ||
                                            !readyAndForeground()
                                        ) return@post
                                        tcpProbe = null
                                        onReport(if (reached)
                                            "TCP handshake reached the P2P group owner. " +
                                                "Peer UNVERIFIED; no data sent."
                                        else "P2P-bound TCP failed or timed out. No data sent.")
                                    }
                                },
                                localP2pAddress = local
                            )
                        ) {
                            probe.close()
                            tcpProbe = null
                            onReport("P2P interface binding rejected. No TCP connection.")
                        } else onReport("Trying one TCP handshake bound to the OS " +
                            "P2P interface. No text, QR, voice or identity is transmitted.")
                    }
                } catch (_: RuntimeException) {
                    onReport("Could not inspect P2P group interface. No TCP connection.")
                }
            }
        } catch (_: RuntimeException) {
            onReport("Unable to inspect Wi-Fi Direct endpoint. No TCP handshake.")
        }
        return true
    }

    /**
     * Real OS group -> OS P2P interface -> pinned TLS, QR and both approvals
     * -> encrypted TEXT/ACK. Never treats a selected radio or TCP probe as
     * identity. QR request is ephemeral and closed on EVERY abort path.
     */
    fun canStartVerifiedText(): Boolean =
        current != null && !verifiedStarted && verifiedSession == null &&
        pendingVerifiedRequest == null &&
        gate.snapshot().phase == DebugWifiDirectTargetLinkGate.Phase.GROUP_OBSERVED &&
        readyAndForeground() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun startVerifiedText(request: DebugBluetoothTlsProbeInput): Boolean {
        val token = current
        if (token == null || request.hostRole || !canStartVerifiedText()) {
            request.close()
            return false
        }
        val m = manager ?: run { request.close(); return false }
        val ch = channel ?: run { request.close(); return false }
        verifiedStarted = true
        pendingVerifiedRequest = request
        onReport("Checking Android's exact P2P route before mutual TLS and QR proof.")
        try {
            m.requestConnectionInfo(ch) { info ->
                if (current !== token || !gate.active(token) || !readyAndForeground() ||
                    !info.groupFormed || info.isGroupOwner
                ) {
                    endPendingVerified(token, request, "No active joining P2P group or permission.")
                    return@requestConnectionInfo
                }
                val owner = info.groupOwnerAddress
                try {
                    m.requestGroupInfo(ch) { group ->
                        if (current !== token || !gate.active(token) ||
                            !readyAndForeground() ||
                            gate.snapshot().phase !=
                                DebugWifiDirectTargetLinkGate.Phase.GROUP_OBSERVED ||
                            group == null || group.isGroupOwner ||
                            pendingVerifiedRequest !== request
                        ) {
                            endPendingVerified(token, request, "Wi-Fi Direct group changed; cannot verify peer.")
                            return@requestGroupInfo
                        }
                        val local = DebugWifiDirectP2pInterface.resolve(
                            group.getInterface(), isHost = false,
                            groupOwnerAddress = owner
                        ) ?: run {
                            endPendingVerified(token, request, "Exact Android P2P IPv4 route unavailable.")
                            return@requestGroupInfo
                        }
                        var owned: DebugWifiDirectApprovedTcpSession? = null
                        val session = DebugWifiDirectApprovedTcpSession(
                            handler, canContinue = {
                                current === token && readyAndForeground()
                            },
                            onStatus = { state, message ->
                                if (current === token && verifiedSession === owned) {
                                    onVerifiedStatus(state, message)
                                    if (state == DebugWifiDirectApprovedTcpSession.Phase.CHAT) {
                                        deadline?.let(handler::removeCallbacks)
                                        deadline = Runnable {
                                            if (current === token) stop(
                                                DebugWifiDirectTargetLinkGate.End.TIMEOUT
                                            )
                                        }.also {
                                            handler.postDelayed(
                                                it, DebugWifiDirectApprovedTcpSession.CHAT_DEADLINE_MS
                                            )
                                        }
                                    }
                                }
                            },
                            onApprovalReady = { if (current === token && verifiedSession === owned)
                                onVerifiedApproval(it)
                            },
                            onText = { if (current === token && verifiedSession === owned)
                                onVerifiedText(it)
                            },
                            onApprovedIdentity = { self, peer ->
                                if (current === token && verifiedSession === owned &&
                                    readyAndForeground()
                                ) onApprovedIdentity(self, peer)
                            }
                        )
                        owned = session
                        verifiedSession = session
                        pendingVerifiedRequest = null
                        if (!session.join(local, owner, request)) {
                            verifiedSession = null
                            onVerifiedStatus(
                                DebugWifiDirectApprovedTcpSession.Phase.STOPPED,
                                "Verified Wi-Fi Direct TCP/TLS cannot start."
                            )
                        }
                    }
                } catch (_: RuntimeException) {
                    endPendingVerified(token, request, "Cannot inspect exact P2P interface for text.")
                }
            }
        } catch (_: RuntimeException) {
            endPendingVerified(token, request, "Android P2P endpoint unavailable for TLS.")
        }
        return true
    }

    /**
     * Android may deliver requestConnectionInfo/requestGroupInfo after STOP,
     * a new explicit radio attempt, or promotion into a real TLS session.
     * Only the SAME token AND SAME still-pending one-shot QR request may
     * be closed or change the visible verified-chat status.
     */
    private fun endPendingVerified(
        token: DebugWifiDirectTargetLinkGate.Token,
        request: DebugBluetoothTlsProbeInput,
        message: String
    ) {
        if (current !== token ||
            pendingVerifiedRequest !== request ||
            verifiedSession != null ||
            gate.snapshot().phase != DebugWifiDirectTargetLinkGate.Phase.GROUP_OBSERVED
        ) return
        pendingVerifiedRequest = null
        request.close()
        onVerifiedStatus(DebugWifiDirectApprovedTcpSession.Phase.STOPPED, message)
    }

    fun approveVerified(): Boolean = verifiedSession?.approve() ?: false
    fun declineVerified(): Boolean = verifiedSession?.decline() ?: false
    fun offerVerifiedText(text: String): Boolean =
        verifiedSession?.offerText(text) ?: false

    fun requestVerifiedVoiceFloor(): com.edenpractical.comms.transport.DebugLanVoiceFloor.Ask =
        verifiedSession?.requestVoiceFloor()
            ?: com.edenpractical.comms.transport.DebugLanVoiceFloor.Ask.BUSY

    fun cancelVerifiedVoiceFloor() {
        verifiedSession?.cancelUnstartedVoiceFloor()
    }

    fun beginVerifiedLiveVoice(): Boolean =
        verifiedSession?.beginLiveVoice() == true

    fun offerVerifiedLiveVoiceFrame(pcm: ByteArray): Boolean =
        verifiedSession?.offerLiveVoiceFrame(pcm) == true

    fun finishVerifiedLiveVoice(): Boolean =
        verifiedSession?.finishLiveVoice() == true

    /** Audio has NO alternate P2P socket or bypass around confirmed TLS. */
    fun offerVerifiedVoiceClip(pcm: ByteArray): Boolean =
        verifiedSession?.offerVoiceClip(pcm) ?: false

    /**
     * The controller owns cancellation; it is safe to call from Stop/exit.
     * A late connect success/connection-info callback is always ignored.
     */
    fun stop(reason: DebugWifiDirectTargetLinkGate.End) {
        val token = current ?: return
        val submitted = gate.snapshot().submitted
        if (gate.active(token)) gate.stop(token, reason)
        current = null
        verifiedSession?.close()
        verifiedSession = null
        pendingVerifiedRequest?.close()
        pendingVerifiedRequest = null
        tcpProbe?.close()
        tcpProbe = null
        connectionInfoOutstanding = false
        deadline?.let(handler::removeCallbacks)
        monitor?.let(handler::removeCallbacks)
        deadline = null
        monitor = null

        val m = manager
        val c = channel
        manager = null
        channel = null
        if (!submitted || m == null || c == null) {
            if (submitted) gate.cleanupCompleted(token, false)
            onReport(if (submitted)
                "Android link cleanup UNCONFIRMED. Inspect system Wi-Fi Direct settings."
            else
                "Link request never started. Existing Wi-Fi Direct groups were not modified.")
            closeChannel(c)
            onFinished()
            return
        }

        onReport("Stopping unverified Wi-Fi Direct link; cleanup requested.")
        var settled = false
        fun finish(confirmedAbsent: Boolean) {
            if (settled) return
            settled = true
            cleanupDeadline?.let(handler::removeCallbacks)
            cleanupDeadline = null
            cleaned = confirmedAbsent
            gate.cleanupCompleted(token, confirmedAbsent)
            onReport(if (confirmedAbsent)
                "Android currently reports no Wi-Fi Direct group. " +
                    "Late OS callbacks are still possible; no identity was verified."
            else
                "Android link cleanup UNCONFIRMED. Inspect system Wi-Fi Direct settings.")
            closeChannel(c)
            onFinished()
        }
        cleanupDeadline = Runnable { finish(false) }.also {
            handler.postDelayed(it, DebugWifiDirectTargetLinkGate.MAX_CLEANUP_MS)
        }
        fun verifyAbsent() {
            if (settled) return
            try {
                m.requestGroupInfo(c) { group ->
                    if (!settled) finish(group == null)
                }
            } catch (_: RuntimeException) { finish(false) }
        }
        fun removeCreatedGroup() {
            if (settled) return
            try {
                m.removeGroup(c, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() { verifyAbsent() }
                    override fun onFailure(reason: Int) { verifyAbsent() }
                })
            } catch (_: RuntimeException) { verifyAbsent() }
        }
        try {
            m.cancelConnect(c, object : WifiP2pManager.ActionListener {
                override fun onSuccess() { removeCreatedGroup() }
                override fun onFailure(reason: Int) { removeCreatedGroup() }
            })
        } catch (_: RuntimeException) { removeCreatedGroup() }
    }

    private fun finishWithoutSubmission(message: String) {
        val c = channel
        manager = null
        channel = null
        current = null
        verifiedSession?.close()
        verifiedSession = null
        pendingVerifiedRequest?.close()
        pendingVerifiedRequest = null
        tcpProbe?.close()
        tcpProbe = null
        deadline?.let(handler::removeCallbacks)
        monitor?.let(handler::removeCallbacks)
        deadline = null
        monitor = null
        onReport(message)
        closeChannel(c)
        onFinished()
    }

    private fun closeChannel(c: WifiP2pManager.Channel?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            try { c?.close() } catch (_: RuntimeException) { }
        }
    }
}
