package com.edenpractical.comms.wifidirect

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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

/**
 * Debug-only Wi-Fi Direct readiness check and explicit permission prompt.
 * No WifiP2pManager initialize/discover/connect, peers, QR, sockets or data.
 */
class DebugWifiDirectPreflightActivity : ComponentActivity() {
    private val permissionGate = DebugWifiDirectPermissionAttemptGate()
    private var foreground = false
    private var report by mutableStateOf<DebugWifiDirectReadinessPolicy.Report?>(null)
    private var requestOutstanding by mutableStateOf(false)
    private var permissionOutcome by mutableStateOf("No permission request started.")

    private fun requiredPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.NEARBY_WIFI_DEVICES
        else Manifest.permission.ACCESS_FINE_LOCATION

    private val permissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val result = permissionGate.complete(granted, foreground)
        requestOutstanding = permissionGate.outstanding
        permissionOutcome = when (result) {
            DebugWifiDirectPermissionAttemptGate.Outcome.GRANTED ->
                "Permission granted. No Wi-Fi Direct search or connection started."
            DebugWifiDirectPermissionAttemptGate.Outcome.DENIED ->
                "Permission declined. Nothing was scanned or connected."
            DebugWifiDirectPermissionAttemptGate.Outcome.IGNORED ->
                "Cancelled or stale permission result ignored."
        }
        refresh()
    }

    private fun readWifiEnabled(): Boolean? = try {
        (getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.isWifiEnabled
    } catch (_: SecurityException) { null }
      catch (_: RuntimeException) { null }

    private fun readLocationMode(): Boolean? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            (getSystemService(Context.LOCATION_SERVICE) as? LocationManager)?.isLocationEnabled
        } else {
            // API 26–27 checks global Location Mode; NO position is requested.
            Settings.Secure.getInt(
                contentResolver,
                Settings.Secure.LOCATION_MODE,
                Settings.Secure.LOCATION_MODE_OFF
            ) != Settings.Secure.LOCATION_MODE_OFF
        }
    } catch (_: SecurityException) { null }
      catch (_: RuntimeException) { null }

    private fun refresh() {
        val feature = packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)
        val service = getSystemService(Context.WIFI_P2P_SERVICE) is WifiP2pManager
        val granted = ContextCompat.checkSelfPermission(this, requiredPermission()) ==
            PackageManager.PERMISSION_GRANTED
        report = DebugWifiDirectReadinessPolicy.evaluate(
            DebugWifiDirectReadinessPolicy.Observation(
                apiLevel = Build.VERSION.SDK_INT,
                featureAdvertised = feature,
                managerPresent = service,
                wifiEnabled = readWifiEnabled(),
                locationModeEnabled = readLocationMode(),
                permissionGranted = granted
            )
        )
        requestOutstanding = permissionGate.outstanding
    }

    private fun askPermission() {
        val current = report ?: return
        if (!permissionGate.begin(
                foreground = foreground,
                featureAdvertised = current.observation.featureAdvertised,
                managerPresent = current.observation.managerPresent,
                permissionAlreadyGranted = current.observation.permissionGranted
            )
        ) {
            permissionOutcome = "No new request started; recheck this phone."
            return
        }
        requestOutstanding = true
        permissionOutcome = "Android permission dialog requested. No scanning is running."
        try {
            permissionRequest.launch(requiredPermission())
        } catch (_: RuntimeException) {
            permissionGate.cancel()
            requestOutstanding = permissionGate.outstanding
            permissionOutcome = "Android permission prompt unavailable. No operation started."
        }
    }

    private fun openSettings(action: String) {
        try {
            startActivity(Intent(action))
        } catch (_: ActivityNotFoundException) {
            permissionOutcome = "Android settings screen unavailable."
        } catch (_: SecurityException) {
            permissionOutcome = "Android blocked opening settings."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
            finish()
            return
        }
        refresh()
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("Wi-Fi Direct — permission & radio preflight",
                            style = MaterialTheme.typography.titleLarge)
                        Text("DEVELOPMENT ONLY • This preflight screen has no peer discovery, connections, QR, " +
                            "messages, voice or background Wi-Fi Direct activity.")
                        val observation = report?.observation
                        val state = report?.state
                        Text(when (state) {
                            DebugWifiDirectReadinessPolicy.State.NOT_SUPPORTED ->
                                "Wi-Fi Direct not advertised by this phone."
                            DebugWifiDirectReadinessPolicy.State.SERVICE_MISSING ->
                                "Wi-Fi Direct service unavailable."
                            DebugWifiDirectReadinessPolicy.State.WIFI_UNKNOWN ->
                                "Cannot confirm Wi-Fi radio status."
                            DebugWifiDirectReadinessPolicy.State.WIFI_OFF ->
                                "Wi-Fi radio is off."
                            DebugWifiDirectReadinessPolicy.State.LOCATION_UNKNOWN ->
                                "Cannot confirm Android Location Mode."
                            DebugWifiDirectReadinessPolicy.State.LOCATION_OFF ->
                                "Android Location Mode is off; future discovery requires it."
                            DebugWifiDirectReadinessPolicy.State.PERMISSION_MISSING ->
                                "Future Wi-Fi Direct discovery permission has not been granted."
                            DebugWifiDirectReadinessPolicy.State.PREREQUISITES_OBSERVED ->
                                "Prerequisites observed. This is NOT evidence of a working P2P link."
                            null -> "Not checked."
                        })
                        Text("Android API: ${Build.VERSION.SDK_INT}")
                        Text("Wi-Fi: ${observation?.wifiEnabled.readable()}")
                        Text("Location Mode: ${observation?.locationModeEnabled.readable()}")
                        Text(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                            "Android 13+: Nearby Wi-Fi devices permission. Android may group " +
                                "this with other Nearby devices permissions."
                        else
                            "Android 8–12L: precise location permission is required by " +
                                "Android for future discovery. No coordinates are read here.")
                        Text(if (observation?.permissionGranted == true)
                            "Runtime permission: granted" else "Runtime permission: not granted")
                        Text(permissionOutcome)
                        Button(
                            onClick = { askPermission() },
                            enabled = foreground && report?.permissionRequestAllowed == true &&
                                !requestOutstanding,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) { Text("REQUEST WI-FI DIRECT TEST PERMISSION") }
                        if (requestOutstanding) {
                            OutlinedButton(
                                onClick = {
                                    permissionGate.cancel()
                                    permissionOutcome = "Cancelled. A late result is ignored."
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            ) { Text("CANCEL PENDING REQUEST") }
                        }
                        OutlinedButton(
                            onClick = { refresh() },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) { Text("RECHECK DEVICE SETTINGS") }
                        Button(
                            onClick = {
                                if (foreground && !requestOutstanding &&
                                    report?.state ==
                                        DebugWifiDirectReadinessPolicy.State.PREREQUISITES_OBSERVED
                                ) {
                                    startActivity(
                                        Intent(this@DebugWifiDirectPreflightActivity,
                                            DebugWifiDirectDiscoveryActivity::class.java)
                                    )
                                }
                            },
                            enabled = foreground && !requestOutstanding &&
                                report?.state ==
                                    DebugWifiDirectReadinessPolicy.State.PREREQUISITES_OBSERVED,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) { Text("OPEN 30-SECOND DISCOVERY TEST") }
                        Button(
                            onClick = {
                                if (foreground && !requestOutstanding &&
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                    report?.state ==
                                        DebugWifiDirectReadinessPolicy.State.PREREQUISITES_OBSERVED
                                ) {
                                    startActivity(
                                        Intent(this@DebugWifiDirectPreflightActivity,
                                            DebugWifiDirectGroupHostActivity::class.java)
                                    )
                                }
                            },
                            enabled = foreground && !requestOutstanding &&
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                report?.state ==
                                    DebugWifiDirectReadinessPolicy.State.PREREQUISITES_OBSERVED,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) { Text("OPEN 20-SECOND LOCAL GROUP HOST TEST") }
                        Text("Temporary group-host test requires Android 10 or newer. " +
                            "It does not connect to any selected nearby device.")
                        OutlinedButton(
                            onClick = {
                                if (foreground && !requestOutstanding) {
                                    startActivity(
                                        Intent(this@DebugWifiDirectPreflightActivity,
                                            DebugWifiDirectQrActivity::class.java)
                                    )
                                }
                            },
                            enabled = foreground && !requestOutstanding,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) { Text("OPEN IN-PERSON WI-FI DIRECT QR PREPARATION") }
                        Text("QR identity preparation works without using the Wi-Fi radio. " +
                            "It does NOT identify or select an Android P2P device.")
                        if (observation?.wifiEnabled != true) {
                            OutlinedButton(
                                onClick = { openSettings(Settings.ACTION_WIFI_SETTINGS) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            ) { Text("OPEN ANDROID WI-FI SETTINGS") }
                        }
                        if (observation?.locationModeEnabled != true) {
                            OutlinedButton(
                                onClick = {
                                    openSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            ) { Text("OPEN ANDROID LOCATION SETTINGS") }
                        }
                        Text("No permission or radio setting can establish trust. " +
                            "Actual P2P discovery, QR verification and bilateral " +
                            "consent require separate reviewed development.")
                    }
                }
            }
        }
    }

    private fun Boolean?.readable(): String = when (this) {
        true -> "On"
        false -> "Off"
        null -> "Unknown"
    }

    override fun onStart() {
        super.onStart()
        foreground = true
        refresh()
    }
    override fun onResume() {
        super.onResume()
        refresh()
    }
    override fun onStop() {
        foreground = false
        if (permissionGate.outstanding) {
            permissionGate.cancel()
            permissionOutcome = "Left the screen; pending permission request cancelled."
        }
        super.onStop()
    }
}
