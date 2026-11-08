package com.edenpractical.comms.wifidirect

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.security.SecureRandom

/**
 * M3s debug only: this device may briefly host a local P2P group by an
 * explicit user tap, but NEVER initiates connection to M3r candidates.
 * No credentials, device IDs, IPs, passphrase, peer data or text are
 * exposed/saved. F3-A allows one explicit TCP handshake-only listener on
 * the OS-reported private group-owner interface, exchanging ZERO data.
 * OS removal is best effort, NOT a shutdown guarantee.
 */
class DebugWifiDirectGroupHostActivity : ComponentActivity() {
    private val gate = DebugWifiDirectGroupHostGate()
    private val handler = Handler(Looper.getMainLooper())
    private var foreground = false
    private var registered = false
    private var active: DebugWifiDirectGroupHostGate.Token? = null
    private var manager: WifiP2pManager? = null
    private var channel: WifiP2pManager.Channel? = null
    private var timeout: Runnable? = null
    private var poll: Runnable? = null
    private var cleanupDeadline: Runnable? = null
    private var tcpProbe: DebugWifiDirectTcpProbe? = null
    private var tcpAttempted = false
    private var tcpNotice by mutableStateOf("No TCP reachability test has been attempted.")
    private var phase by mutableStateOf(DebugWifiDirectGroupHostGate.Phase.IDLE)
    private var notice by mutableStateOf(
        "No group has been requested. This is a development radio test only."
    )

    private fun ready(): Boolean = try {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) false else {

        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.NEARBY_WIFI_DEVICES
        } else Manifest.permission.ACCESS_FINE_LOCATION
        val location = (getSystemService(Context.LOCATION_SERVICE) as? LocationManager)
            ?.isLocationEnabled
        packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT) &&
            (getSystemService(Context.WIFI_P2P_SERVICE) is WifiP2pManager) &&
            (getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.isWifiEnabled == true &&
            location == true &&
            ContextCompat.checkSelfPermission(this, permission) ==
                PackageManager.PERMISSION_GRANTED
        }
    } catch (_: RuntimeException) {
        false
    }

    private fun refreshPhase() { phase = gate.snapshot().phase }

    private fun closeChannel(channelToClose: WifiP2pManager.Channel?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            try { channelToClose?.close() } catch (_: RuntimeException) { }
        }
    }

    private fun abandonBeforeCreate(
        current: DebugWifiDirectGroupHostGate.Token,
        reason: DebugWifiDirectGroupHostGate.End,
        message: String
    ) {
        if (active !== current || gate.snapshot().creationSubmitted) return
        gate.stop(current, reason)
        tcpProbe?.close()
        tcpProbe = null
        active = null
        timeout?.let(handler::removeCallbacks)
        poll?.let(handler::removeCallbacks)
        timeout = null
        poll = null
        closeChannel(channel)
        channel = null
        manager = null
        notice = message
        refreshPhase()
    }

    private fun endHost(
        current: DebugWifiDirectGroupHostGate.Token,
        why: DebugWifiDirectGroupHostGate.End,
        message: String
    ) {
        if (active !== current || !gate.active(current)) return
        val submitted = gate.snapshot().creationSubmitted
        gate.stop(current, why)
        tcpProbe?.close()
        tcpProbe = null
        active = null // no stale callback may resume radio work
        timeout?.let(handler::removeCallbacks)
        poll?.let(handler::removeCallbacks)
        timeout = null
        poll = null
        notice = message
        refreshPhase()
        if (!submitted) {
            closeChannel(channel)
            channel = null
            manager = null
            return
        }
        val m = manager
        val c = channel
        manager = null
        channel = null
        if (m == null || c == null) {
            gate.cleaned(current, false)
            notice = "Could not verify Android group cleanup. Check system Wi-Fi Direct."
            refreshPhase()
            closeChannel(c)
            return
        }
        // Best effort; if Android never returns a callback, say UNCONFIRMED.
        var finished = false
        fun conclude(confirmed: Boolean) {
            if (finished) return
            finished = true
            cleanupDeadline?.let(handler::removeCallbacks)
            cleanupDeadline = null
            gate.cleaned(current, confirmed)
            notice = if (confirmed)
                "Android reported the temporary group absent. No user session existed."
            else
                "Android group removal NOT verified. Inspect Wi-Fi Direct system settings."
            refreshPhase()
            closeChannel(c)
        }
        cleanupDeadline = Runnable { conclude(false) }.also {
            handler.postDelayed(it, 4_000L)
        }
        try {
            m.removeGroup(c, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    if (finished) return
                    try {
                        m.requestGroupInfo(c) { group ->
                            // Null means Android currently reports no group. A
                            // late platform creation can still occur: no guarantee.
                            conclude(group == null)
                        }
                    } catch (_: RuntimeException) {
                        conclude(false)
                    }
                }
                override fun onFailure(reason: Int) { conclude(false) }
            })
        } catch (_: RuntimeException) {
            conclude(false)
        }
    }

    private fun newTemporaryConfig(): WifiP2pConfig {
        // API 29+: use an explicitly nonpersistent configuration with ephemeral
        // random network name and passphrase. Neither is displayed or logged.
        val bytes = ByteArray(14)
        SecureRandom().nextBytes(bytes)
        val hex = bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return WifiP2pConfig.Builder()
            .setNetworkName("DIRECT-${hex.take(2)}-TEMP")
            .setPassphrase(hex.drop(2).take(24))
            .enablePersistentMode(false)
            .build()
    }

    private fun inspectGroup(current: DebugWifiDirectGroupHostGate.Token) {
        if (active !== current || !foreground || !ready() || !gate.active(current)) {
            if (active === current) endHost(current,
                DebugWifiDirectGroupHostGate.End.PERMISSION_LOST,
                "Permission or radio state changed; ending temporary host.")
            return
        }
        val m = manager ?: return
        val c = channel ?: return
        try {
            m.requestGroupInfo(c) { group ->
                if (active !== current || !gate.active(current)) return@requestGroupInfo
                when (gate.snapshot().phase) {
                    DebugWifiDirectGroupHostGate.Phase.CHECKING_EXISTING -> {
                        if (!gate.observedExisting(current, group != null)) {
                            abandonBeforeCreate(current,
                                DebugWifiDirectGroupHostGate.End.EXISTING_GROUP,
                                "Android reports an existing P2P group. Not modifying it.")
                        } else {
                            refreshPhase()
                            submitTemporaryGroup(current)
                        }
                    }
                    DebugWifiDirectGroupHostGate.Phase.CREATING -> {
                        if (group?.isGroupOwner == true &&
                            gate.groupSeen(current, true)) {
                            refreshPhase()
                            notice = "Android reports a local temporary group owner. " +
                                "No other phone has been authenticated or messaged."
                        }
                    }
                    DebugWifiDirectGroupHostGate.Phase.HOST_OBSERVED -> {
                        if (group == null) endHost(current,
                            DebugWifiDirectGroupHostGate.End.RADIO_LOST,
                            "Android stopped reporting the temporary group.")
                    }
                    else -> Unit
                }
            }
        } catch (_: RuntimeException) {
            endHost(current, DebugWifiDirectGroupHostGate.End.PLATFORM_FAILURE,
                "Could not inspect Android group state.")
        }
    }

    private fun submitTemporaryGroup(current: DebugWifiDirectGroupHostGate.Token) {
        if (active !== current || !foreground || !ready()) {
            if (active === current) endHost(current,
                DebugWifiDirectGroupHostGate.End.PERMISSION_LOST,
                "Readiness lost before group creation.")
            return
        }
        val m = manager ?: return
        val c = channel ?: return
        if (!gate.submitCreation(current)) return
        refreshPhase()
        notice = "Android group creation requested. Not proof of group formation."
        try {
            m.createGroup(c, newTemporaryConfig(), object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    if (active !== current || !gate.platformAccepted(current)) return
                    notice = "Android accepted group creation. Checking actual state."
                    inspectGroup(current)
                }
                override fun onFailure(reason: Int) {
                    if (active !== current) return
                    endHost(current, DebugWifiDirectGroupHostGate.End.PLATFORM_FAILURE,
                        "Android declined group creation (code $reason).")
                }
            })
        } catch (_: RuntimeException) {
            endHost(current, DebugWifiDirectGroupHostGate.End.PLATFORM_FAILURE,
                "Android group creation unavailable.")
        }
    }

    private fun startHost() {
        if (!foreground || !registered || active != null) return
        if (gate.snapshot().phase == DebugWifiDirectGroupHostGate.Phase.STOPPED) gate.reset()
        if (!ready()) {
            notice = "Requires Android 10+, Wi-Fi, Location Mode and P2P permission. " +
                "Return to the device preflight."
            return
        }
        val current = gate.start(humanTap = true, foreground = foreground,
            ready = ready(), api = Build.VERSION.SDK_INT) ?: return
        active = current
        tcpAttempted = false
        tcpNotice = "No TCP reachability test has been attempted."
        refreshPhase()
        notice = "Checking for an existing P2P group. Existing groups are not touched."
        val m = getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        if (m == null) {
            abandonBeforeCreate(current, DebugWifiDirectGroupHostGate.End.PLATFORM_FAILURE,
                "Wi-Fi Direct platform service unavailable.")
            return
        }
        manager = m
        try {
            channel = m.initialize(this, Looper.getMainLooper(),
                WifiP2pManager.ChannelListener {
                    if (active === current) endHost(current,
                        DebugWifiDirectGroupHostGate.End.CHANNEL_LOST,
                        "Android P2P channel disconnected; cleanup status may be uncertain.")
                })
        } catch (_: RuntimeException) {
            abandonBeforeCreate(current, DebugWifiDirectGroupHostGate.End.CHANNEL_LOST,
                "Could not initialize Wi-Fi Direct group host.")
            return
        }
        if (channel == null) {
            abandonBeforeCreate(current, DebugWifiDirectGroupHostGate.End.CHANNEL_LOST,
                "Android did not supply a P2P channel.")
            return
        }
        timeout = Runnable {
            endHost(current, DebugWifiDirectGroupHostGate.End.TIMEOUT,
                "20-second group host limit reached. Requesting cleanup.")
        }.also { handler.postDelayed(it, 20_000L) }

        fun schedulePoll() {
            poll = Runnable {
                if (active !== current || !gate.active(current)) return@Runnable
                if (!ready() || !foreground) {
                    endHost(current, DebugWifiDirectGroupHostGate.End.PERMISSION_LOST,
                        "Foreground, permission or radio readiness lost.")
                } else {
                    if (gate.snapshot().phase != DebugWifiDirectGroupHostGate.Phase.CHECKING_EXISTING)
                        inspectGroup(current)
                    schedulePoll()
                }
            }.also { handler.postDelayed(it, 2_000L) }
        }
        schedulePoll()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                m.requestP2pState(channel!!) { state ->
                    if (active !== current || !gate.active(current)) return@requestP2pState
                    if (state != WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                        endHost(current, DebugWifiDirectGroupHostGate.End.RADIO_LOST,
                            "Android reports Wi-Fi Direct disabled.")
                    } else inspectGroup(current)
                }
            } catch (_: RuntimeException) {
                abandonBeforeCreate(current, DebugWifiDirectGroupHostGate.End.PLATFORM_FAILURE,
                    "Cannot confirm Wi-Fi Direct radio state.")
            }
        }
    }

    /** F3-A: second user action on formed group; zero application data. */
    private fun requestTcpHostReachability() {
        val token = active ?: return
        if (tcpAttempted || tcpProbe != null || !foreground || !ready() ||
            gate.snapshot().phase != DebugWifiDirectGroupHostGate.Phase.HOST_OBSERVED
        ) return
        val m = manager ?: return
        val c = channel ?: return
        tcpAttempted = true
        tcpNotice = "Checking the temporary Wi-Fi Direct group's own interface."
        try {
            m.requestConnectionInfo(c) { info ->
                if (active !== token || !foreground || !ready() ||
                    gate.snapshot().phase != DebugWifiDirectGroupHostGate.Phase.HOST_OBSERVED
                ) return@requestConnectionInfo
                if (!info.groupFormed || !info.isGroupOwner) {
                    tcpNotice = "Android did not confirm our group-owner role; no listener."
                    return@requestConnectionInfo
                }
                try {
                    m.requestGroupInfo(c) { group ->
                        if (active !== token || !foreground || !ready() ||
                            gate.snapshot().phase !=
                                DebugWifiDirectGroupHostGate.Phase.HOST_OBSERVED
                        ) return@requestGroupInfo
                        if (group?.isGroupOwner != true) {
                            tcpNotice = "No active owner P2P group interface. No listener."
                            return@requestGroupInfo
                        }
                        // Android can anonymize groupOwnerAddress on the HOST.
                        // Resolve ONLY the exact P2P interface supplied by this
                        // group. Never guess a default wlan/cellular address.
                        val local = DebugWifiDirectP2pInterface.resolve(
                            group.getInterface(), isHost = true,
                            groupOwnerAddress = info.groupOwnerAddress
                        )
                        if (local == null) {
                            tcpNotice = "Android's P2P interface address is unavailable " +
                                "or ambiguous. TCP was not opened."
                            return@requestGroupInfo
                        }
                        val probe = DebugWifiDirectTcpProbe()
                        tcpProbe = probe
                        if (!probe.start(
                                DebugWifiDirectTcpProbe.Role.HOST,
                                info.groupOwnerAddress,
                                onCompleted = { reached ->
                                    handler.post {
                                        if (active !== token || tcpProbe !== probe ||
                                            !foreground
                                        ) return@post
                                        tcpProbe = null
                                        tcpNotice = if (reached)
                                            "A TCP connection reached the P2P listener. " +
                                                "Peer UNVERIFIED. No data sent."
                                        else "TCP handshake timed out or failed. No data sent."
                                    }
                                },
                                onListening = {
                                    handler.post {
                                        if (active === token && tcpProbe === probe &&
                                            foreground
                                        ) tcpNotice = "P2P-bound listener active briefly. " +
                                            "Tap CONNECTIVITY TEST on joining phone now."
                                    }
                                },
                                localP2pAddress = local
                            )
                        ) {
                            probe.close()
                            tcpProbe = null
                            tcpNotice = "Could not bind to the OS P2P interface. No listener."
                        } else {
                            tcpNotice = "Opening 5-second listener on verified P2P interface."
                        }
                    }
                } catch (_: RuntimeException) {
                    tcpNotice = "Cannot inspect the active P2P group. No listener started."
                }
            }
        } catch (_: RuntimeException) {
            tcpNotice = "Cannot inspect Wi-Fi Direct endpoint. No listener started."
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!foreground) return
            val current = active ?: return
            if (!gate.active(current)) return
            when (intent?.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    if (intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE,
                            WifiP2pManager.WIFI_P2P_STATE_DISABLED) !=
                        WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                        endHost(current, DebugWifiDirectGroupHostGate.End.RADIO_LOST,
                            "Wi-Fi Direct radio disabled; requesting cleanup.")
                    }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    if (gate.snapshot().phase != DebugWifiDirectGroupHostGate.Phase.CHECKING_EXISTING)
                        inspectGroup(current)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) {
            finish()
            return
        }
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("Wi-Fi Direct — temporary local group",
                            style = MaterialTheme.typography.titleLarge)
                        Text("DEBUG ONLY • Android 10+. This phone may broadcast an " +
                            "ephemeral Wi-Fi Direct group for 20 seconds. " +
                            "Use only in a controlled test environment.")
                        Text("A local Wi-Fi group is NOT an Eden Comms connection, " +
                            "verified identity or private conversation.")
                        Text("State: $phase")
                        Text(notice)
                        Text(tcpNotice)
                        OutlinedButton(
                            onClick = { requestTcpHostReachability() },
                            enabled = foreground && active != null && !tcpAttempted &&
                                phase == DebugWifiDirectGroupHostGate.Phase.HOST_OBSERVED,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) { Text("RUN 5-SECOND TCP LISTENER TEST — NO DATA") }
                        Button(
                            onClick = { startHost() },
                            enabled = foreground && registered && active == null &&
                                phase != DebugWifiDirectGroupHostGate.Phase.CLEANING,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) { Text("START 20-SECOND GROUP HOST TEST") }
                        OutlinedButton(
                            onClick = {
                                active?.let {
                                    endHost(it, DebugWifiDirectGroupHostGate.End.USER_STOP,
                                        "Stopped by you. Requesting Android cleanup.")
                                }
                            },
                            enabled = active != null,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) { Text("STOP AND REMOVE TEST GROUP") }
                        Text("No peer identities, IP addresses or group passwords are shown " +
                            "or stored. The optional TCP reachability probe transfers zero " +
                            "application bytes; no text, microphone or trust is enabled. " +
                            "Group cleanup is best effort; inspect system Wi-Fi Direct " +
                            "settings if removal cannot be confirmed.")
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
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        }
        try {
            ContextCompat.registerReceiver(this, receiver, filter,
                ContextCompat.RECEIVER_EXPORTED)
            registered = true
        } catch (_: RuntimeException) {
            registered = false
            notice = "Cannot monitor system Wi-Fi Direct changes; host test unavailable."
        }
    }

    override fun onStop() {
        foreground = false
        active?.let {
            endHost(it, DebugWifiDirectGroupHostGate.End.BACKGROUND,
                "Screen left. Requesting temporary group cleanup.")
        }
        if (registered) {
            try { unregisterReceiver(receiver) } catch (_: RuntimeException) { }
            registered = false
        }
        super.onStop()
    }
}
