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
 * names, MAC addresses, SDP, RFCOMM sockets, discovery result lists, messages,
 * telemetry, automatic reconnection or trusted-peer enrollment.
 *
 * This activity and the scan permission live in src/debug only, so a release
 * APK cannot launch the activity or request BLUETOOTH_SCAN.
 *
 * Explicit permission + start required; every exit best-effort cancels the OS
 * inquiry and unregisters broadcasts. Results are untrusted observation COUNTS.
 * Not a Bluetooth Eden Comms connection, and no physical QA has occurred.
 */
class DebugBluetoothDiscoveryActivity : ComponentActivity() {
    private val lifecycle = NearbyDiscoverySession()
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
        lifecycle.tick(token, SystemClock.elapsedRealtime())
        stopHardware()
        attempt = null
        busy = false
        status = "Search time limit reached. No connection or pairing was made."
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val token = attempt ?: return
            if (!foreground || !radioRunning || !hasScanPermission()) {
                if (radioRunning && !hasScanPermission()) {
                    lifecycle.permissionRevoked(token)
                    stopHardware()
                    attempt = null
                    busy = false
                    status = "Nearby devices permission was removed. Search stopped."
                }
                return
            }
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    // Never obtain intent's EXTRA_DEVICE, device name, address,
                    // bond state or advertised service data. This is a count only.
                    val accepted = lifecycle.candidateObserved(
                        token, foreground = true, elapsedMs = SystemClock.elapsedRealtime()
                    )
                    observations = lifecycle.snapshot().observations
                    if (!accepted) {
                        stopHardware()
                        attempt = null
                        busy = false
                        status = "Search ended at its safety or observation limit."
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    lifecycle.complete(token)
                    stopHardware()
                    attempt = null
                    busy = false
                    status = "Bluetooth inquiry finished. No devices were trusted or connected."
                }
            }
        }
    }

    private val scanPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { approved ->
        val token = attempt ?: return@registerForActivityResult
        val granted = approved && hasScanPermission()
        if (!lifecycle.platformPermissionResult(token, foreground, granted)) {
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
                                "No device names, addresses, or histories are kept."
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
                        ) { Text("STOP SEARCH") }
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
        if (token != null) lifecycle.onBackground(token)
        stopHardware()
        attempt = null
        busy = false
        if (token != null) status = "Search stopped when this screen was left."
        super.onStop()
    }

    override fun onDestroy() {
        stopHardware()
        super.onDestroy()
    }

    private fun hasScanPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED

    private fun beginInquiry() {
        if (!foreground || busy || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (lifecycle.snapshot().phase == NearbyDiscoverySession.Phase.STOPPED) {
            lifecycle.resetAfterStop()
        }
        val token = lifecycle.begin(ConnectionMethod.BLUETOOTH, foreground)
            ?: return
        attempt = token
        busy = true
        observations = 0
        status = "Confirming your permission to start nearby Bluetooth inquiry."
        if (!lifecycle.personConsented(token, foreground)) {
            stopByUser()
            return
        }
        if (hasScanPermission()) {
            if (lifecycle.platformPermissionResult(token, foreground, granted = true)) {
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
            lifecycle.permissionRevoked(token)
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
                lifecycle.radioUnavailable(token)
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
            if (!lifecycle.radioScanStarted(
                    token, foreground, started, SystemClock.elapsedRealtime()
                )
            ) {
                stopHardware()
                attempt = null
                busy = false
                status = "Bluetooth inquiry could not start. Check the radio is enabled."
                return
            }
            radioRunning = true
            handler.postDelayed(timeout, NearbyDiscoverySession.MAX_SCAN_MS + 100L)
            status = "Searching nearby Bluetooth radio signals. Tap Stop at any time."
        } catch (_: SecurityException) {
            lifecycle.permissionRevoked(token)
            stopHardware()
            attempt = null
            busy = false
            status = "Bluetooth permission unavailable. No scan started."
        } catch (_: RuntimeException) {
            lifecycle.radioUnavailable(token)
            stopHardware()
            attempt = null
            busy = false
            status = "Bluetooth discovery unavailable on this device."
        }
    }

    private fun stopByUser() {
        val token = attempt ?: return
        lifecycle.cancel(token)
        stopHardware()
        attempt = null
        busy = false
        status = "You stopped the search. No connection was made."
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
