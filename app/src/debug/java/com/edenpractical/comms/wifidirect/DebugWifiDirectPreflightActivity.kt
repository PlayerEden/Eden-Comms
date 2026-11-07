package com.edenpractical.comms.wifidirect

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Debug APK only. Reads advertised feature + service presence, without asking
 * permissions, calling WifiP2pManager operations, or discovering nearby phones.
 */
class DebugWifiDirectPreflightActivity : ComponentActivity() {
    private var assessment by mutableStateOf<DebugWifiDirectPreflightPolicy.Assessment?>(null)

    private fun refresh() {
        val availableFeature = packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)
        val availableManager = getSystemService(Context.WIFI_P2P_SERVICE) is WifiP2pManager
        assessment = DebugWifiDirectPreflightPolicy.assess(
            DebugWifiDirectPreflightPolicy.Observation(
                apiLevel = Build.VERSION.SDK_INT,
                featureAdvertised = availableFeature,
                managerPresent = availableManager
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) {
            finish()
            return
        }
        refresh()
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("Wi-Fi Direct — read-only preflight",
                            style = MaterialTheme.typography.titleLarge)
                        Text("DEVELOPMENT TEST ONLY • No nearby search, permissions, " +
                            "connections, QR checks, messages or voice.")
                        Text(assessment?.description ?: "Capability not checked.")
                        Text("Android API level: ${Build.VERSION.SDK_INT}")
                        val permission = assessment?.permissionFamily
                        Text(
                            if (permission == DebugWifiDirectPreflightPolicy.PermissionFamily.NEARBY_WIFI_DEVICES) {
                                "Future Wi-Fi Direct discovery/connection needs the Nearby Wi-Fi devices " +
                                    "runtime permission on Android 13 or newer."
                            } else {
                                "Future Wi-Fi Direct discovery/connection needs precise location " +
                                    "runtime permission on Android 12L or older."
                            }
                        )
                        Text(
                            "Future discovery also requires Wi-Fi state access and may require " +
                                "Android Location Mode to be on. This screen does not check " +
                                "Wi-Fi radio readiness or request any permission."
                        )
                        Text(
                            "Support advertised is not peer discovery. A discovered device " +
                                "would still require fresh cryptographic identity verification, " +
                                "both people's approval, and a separately reviewed transport."
                        )
                        Button(
                            onClick = { refresh() },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) { Text("RECHECK DEVICE CAPABILITY") }
                        Text(
                            "No Wi-Fi Direct scan is running. Leave this screen at any time; " +
                                "no session or radio work needs cleanup."
                        )
                    }
                }
            }
        }
    }
}
