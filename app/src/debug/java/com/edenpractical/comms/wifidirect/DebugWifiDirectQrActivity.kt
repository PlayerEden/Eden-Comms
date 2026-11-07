package com.edenpractical.comms.wifidirect

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
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
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.edenpractical.comms.pairing.LocalPairingQrDisplay
import com.edenpractical.comms.pairing.LocalPairingQrScanner

/**
 * M3s-B debug-only in-person QR metadata preparation. Completely separate
 * from P2P radio scans/group hosting and from LAN/Bluetooth payload transports.
 * No device MAC/IP, target binding, live socket, chat or voice.
 */
class DebugWifiDirectQrActivity : ComponentActivity() {
    private val preparation = DebugWifiDirectQrCoordinator()
    private val handler = Handler(Looper.getMainLooper())
    private var foreground = false
    private val expireCheck = object : Runnable {
        override fun run() {
            if (!foreground) return
            preparation.expireIfNeeded()
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) {
            finish()
            return
        }
        // QR payloads can contain certificate pins and session challenges.
        // Keep them off screenshots, the app switcher and media projection.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        preparation.loadExistingIdentity()
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Wi-Fi Direct — in-person QR preparation",
                            style = MaterialTheme.typography.titleLarge)
                        Text("DEBUG ONLY • Public identity and fresh QR metadata. " +
                            "This does NOT identify an Android radio target, " +
                            "prove a private key, pair phones, or establish trust.")
                        if (!preparation.identityReady) {
                            Text("This phone needs an existing Keystore identity. " +
                                "Creating one requires your tap; no keys are sent.")
                            Button(
                                onClick = { preparation.createIdentityWithConsent() },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            ) { Text("CREATE MY LOCAL DEVICE IDENTITY") }
                        } else {
                            when (preparation.stage) {
                                DebugWifiDirectQrCoordinator.Stage.CHOOSE -> {
                                    Text("Place both phones together and choose " +
                                        "one host and one joiner for a three-code exchange.")
                                    Button(
                                        onClick = { preparation.startHost() },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                                    ) { Text("1 — SHOW MY HOST QR") }
                                    OutlinedButton(
                                        onClick = { preparation.startJoin() },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                                    ) { Text("1 — SCAN THE OTHER PHONE'S HOST QR") }
                                }
                                DebugWifiDirectQrCoordinator.Stage.HOST_SHOW -> {
                                    LocalPairingQrDisplay(preparation.qrPayload)
                                    Button(
                                        onClick = { preparation.scanReply() },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                                    ) { Text("2 — SCAN THE JOINER'S REPLY QR") }
                                }
                                DebugWifiDirectQrCoordinator.Stage.JOIN_SHOW_REPLY -> {
                                    LocalPairingQrDisplay(preparation.qrPayload)
                                    Button(
                                        onClick = { preparation.scanAck() },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                                    ) { Text("3 — SCAN THE HOST'S ACKNOWLEDGEMENT QR") }
                                }
                                DebugWifiDirectQrCoordinator.Stage.HOST_SHOW_ACK -> {
                                    LocalPairingQrDisplay(preparation.qrPayload)
                                    Text("Show the acknowledgement QR to the joiner. " +
                                        "Compare the code only after they scan it.")
                                }
                                DebugWifiDirectQrCoordinator.Stage.HOST_SCAN_REPLY,
                                DebugWifiDirectQrCoordinator.Stage.JOIN_SCAN_HOST,
                                DebugWifiDirectQrCoordinator.Stage.JOIN_SCAN_ACK -> {
                                    key(preparation.scannerGeneration) {
                                        LocalPairingQrScanner(
                                            onPayload = preparation::scanned,
                                            onCameraError = { preparation.cameraFailed() }
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { preparation.retryScanner() },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                                    ) { Text("RETRY QR CAMERA") }
                                }
                                DebugWifiDirectQrCoordinator.Stage.JOIN_PREPARED -> {
                                    Text("Third QR scanned. Compare the code aloud in person.")
                                }
                            }
                            if (preparation.comparison.isNotEmpty()) {
                                Text("IN-PERSON COMPARISON: ${preparation.comparison}",
                                    style = MaterialTheme.typography.titleMedium)
                                Text("Matching codes mean the public QR transcript matches, " +
                                    "not that the Wi-Fi Direct device or TLS peer is verified.")
                            }
                        }
                        Text(preparation.status)
                        if (preparation.stage != DebugWifiDirectQrCoordinator.Stage.CHOOSE) {
                            OutlinedButton(
                                onClick = { preparation.clear() },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            ) { Text("CANCEL AND ERASE QR PREPARATION") }
                        }
                        Text("No scan results, radio connection, device address, " +
                            "Wi-Fi group, socket, message, audio or trusted contact " +
                            "is created by this screen. Leaving erases the preparation.")
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        foreground = true
        handler.removeCallbacks(expireCheck)
        handler.post(expireCheck)
    }

    override fun onStop() {
        foreground = false
        handler.removeCallbacks(expireCheck)
        preparation.clear()
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacks(expireCheck)
        preparation.clear()
        super.onDestroy()
    }
}
