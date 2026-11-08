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
    private val onFinished: () -> Unit
) {
    private val gate = DebugWifiDirectTargetLinkGate()
    private val handler = Handler(Looper.getMainLooper())
    private var current: DebugWifiDirectTargetLinkGate.Token? = null
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

    fun busy(): Boolean = current != null ||
        gate.snapshot().phase == DebugWifiDirectTargetLinkGate.Phase.CLEANING

    fun startSelected(target: WifiP2pDevice): Boolean {
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
        }.also { handler.postDelayed(it, DebugWifiDirectTargetLinkGate.MAX_LINK_MS) }

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
        onReport("Requesting Android's unverified group-owner endpoint for a " +
            "single TCP handshake. No messages.")
        try {
            m.requestConnectionInfo(c) { info ->
                if (current !== token || !readyAndForeground() ||
                    gate.snapshot().phase != DebugWifiDirectTargetLinkGate.Phase.GROUP_OBSERVED
                ) return@requestConnectionInfo
                val address = info.groupOwnerAddress
                if (!info.groupFormed || info.isGroupOwner || address == null) {
                    onReport("This phone is not a confirmed P2P joiner with an " +
                        "OS-reported owner endpoint. No TCP connection attempted.")
                    return@requestConnectionInfo
                }
                val probe = DebugWifiDirectTcpProbe()
                tcpProbe = probe
                if (!probe.start(DebugWifiDirectTcpProbe.Role.JOIN, address,
                        onCompleted = { reached ->
                            handler.post {
                                if (current !== token || tcpProbe !== probe) return@post
                                tcpProbe = null
                                onReport(if (reached)
                                    "TCP handshake completed with a private group endpoint. " +
                                        "The remote app/peer is UNVERIFIED; no data sent."
                                else "TCP handshake failed or timed out. No data sent.")
                            }
                        }
                    )
                ) {
                    probe.close()
                    tcpProbe = null
                    onReport("The unverified group endpoint failed private-IPv4 " +
                        "validation; no TCP connection.")
                } else onReport("Attempting one bounded TCP handshake. " +
                    "No user text, voice or identity is transmitted.")
            }
        } catch (_: RuntimeException) {
            onReport("Unable to inspect Wi-Fi Direct endpoint. No TCP handshake.")
        }
        return true
    }

    /**
     * The controller owns cancellation; it is safe to call from Stop/exit.
     * A late connect success/connection-info callback is always ignored.
     */
    fun stop(reason: DebugWifiDirectTargetLinkGate.End) {
        val token = current ?: return
        val submitted = gate.snapshot().submitted
        if (gate.active(token)) gate.stop(token, reason)
        current = null
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
