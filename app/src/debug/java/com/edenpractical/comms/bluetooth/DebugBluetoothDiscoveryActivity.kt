package com.edenpractical.comms.bluetooth

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
 * Selecting one stops scanning but NEVER opens a connection or grants trust.
 * No physical QA has occurred.
 */
class DebugBluetoothDiscoveryActivity : ComponentActivity() {
    private val discoveryState = NearbyDiscoverySession()
    private val candidateLedger = DebugBluetoothCandidateLedger<BluetoothDevice>()
    private var candidateToken: DebugBluetoothCandidateLedger.Token? = null
    private var candidateRows by mutableStateOf(emptyList<DebugBluetoothCandidateLedger.Row>())
    private var selectedOrdinal by mutableStateOf<Int?>(null)
    private var attempt: NearbyDiscoverySession.Attempt? = null
    private var adapter: BluetoothAdapter? = null
    private var receiverRegistered = false
    private var radioRunning = false
    private var foreground = false
    private var busy by mutableStateOf(false)
    private var observations by mutableIntStateOf(0)
    private var status by mutableStateOf("Search is off. No Bluetooth device is trusted.")
    private val handler = Handler(Looper.getMainLooper())
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
                        val handle = try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(
                                    BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java
                                )
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                            }
                        } catch (_: RuntimeException) { null }
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
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("Bluetooth discovery — DEVELOPMENT TEST",
                            style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Search for nearby Bluetooth Classic radio activity. " +
                                "This is NOT Eden Comms peer discovery, pairing, " +
                                "encryption or message delivery."
                        )
                        Text(
                            "Only Android 12+ is included in this experiment. " +
                                "You explicitly approve Nearby devices access. " +
                                "Nothing is scanned in the background."
                        )
                        Text(
                            "Radio observations: $observations (unverified events). " +
                                "Up to 12 unnamed signal handles are held temporarily " +
                                "in memory; no names, addresses or history are saved."
                        )
                        Text(status, style = MaterialTheme.typography.bodyLarge)
                        Button(
                            onClick = { beginInquiry() },
                            enabled = !busy && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
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
                        if (selectedOrdinal != null) {
                            Text(
                                "Signal $selectedOrdinal is staged locally ONLY. Its owner " +
                                    "and identity have NOT been verified; no connection, " +
                                    "pairing or messages are possible."
                            )
                            OutlinedButton(
                                onClick = { clearCandidateMemory() },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            ) { Text("FORGET SELECTED SIGNAL") }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Bluetooth results can include unrelated devices. " +
                                "Finding one proves nothing about its identity. " +
                                "Do not share sensitive data or rely on this test " +
                                "for communication."
                        )
                        Text(
                            "Close or background this screen to end the scan. " +
                                "Android may retain its own Bluetooth state; " +
                                "Eden Comms does not save scan results."
                        )
                    }
                }
            }
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
        attempt = null
        busy = false
        status = "Search and local signal choices cleared when this screen was left."
        super.onStop()
    }

    override fun onDestroy() {
        stopHardware()
        clearCandidateMemory()
        super.onDestroy()
    }

    private fun hasScanPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED

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
            "Identity verification and Bluetooth connection are not implemented."
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
