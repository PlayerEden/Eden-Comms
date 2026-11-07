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
    private val handler = Handler(Looper.getMainLooper())
    private var foreground = false
    private var registered = false
    private var manager: WifiP2pManager? = null
    private var channel: WifiP2pManager.Channel? = null
    private var token: DebugWifiDirectDiscoveryGate.Token? = null
    private var discoveryRequested = false
    private var timeoutTask: Runnable? = null
    private var monitorTask: Runnable? = null

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
        if (!foreground || !registered || token != null) return
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
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("Wi-Fi Direct — 30-second search test",
                            style = MaterialTheme.typography.titleLarge)
                        Text("DEVELOPMENT ONLY • Nearby radio observations are NOT " +
                            "Eden Comms identity, consent, a connection, or a conversation.")
                        Text(explanation)
                        Text("Platform P2P radio observed: " + when (radioState) {
                            true -> "Enabled"
                            false -> "Disabled"
                            null -> "Not confirmed"
                        })
                        Text("Discovery status: $phase")
                        Text("Anonymous peer-list change events: $eventCount (NOT devices)")
                        Button(
                            onClick = { startUserSearch() },
                            enabled = foreground && registered && token == null,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) { Text("START 30-SECOND SEARCH") }
                        OutlinedButton(
                            onClick = { stopByUser() },
                            enabled = token != null,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) { Text("STOP SEARCH") }
                        Text("No peer names, addresses or identifiers are read or saved. " +
                            "No group can form, no messages or audio can be sent. " +
                            "Leaving this screen stops the test.")
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
}
