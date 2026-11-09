package com.edenpractical.comms.wifidirect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.edenpractical.comms.pairing.LocalPairingQrDisplay
import com.edenpractical.comms.pairing.LocalPairingQrScanner

/**
 * The Wi-Fi Direct radio and all three QR steps stay INSIDE the same activity
 * and activity-owned coordinator. Leaving clears both; there is no global QR
 * store, intent/clipboard credential transfer, saved peer, or automatic trust.
 */
@Composable
internal fun DebugWifiDirectInlineQrPanel(
    preparation: DebugWifiDirectQrCoordinator,
    hostRole: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Step 1 — Verify nearby person", style = MaterialTheme.typography.titleMedium)
        Text(
            "Exchange three fresh QR codes in person before opening a group. " +
                "Radio proximity alone never authorizes text.",
            style = MaterialTheme.typography.bodySmall
        )
        if (!preparation.identityReady) {
            Text("Create or load this phone's Android Keystore identity first.")
            Button(onClick = { preparation.createIdentityWithConsent() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text("CREATE LOCAL DEVICE IDENTITY")
            }
        } else {
            when (preparation.stage) {
                DebugWifiDirectQrCoordinator.Stage.CHOOSE -> {
                    Button(
                        onClick = { if (hostRole) preparation.startHost()
                                    else preparation.startJoin() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) {
                        Text(if (hostRole) "SHOW HOST QR" else "SCAN HOST QR")
                    }
                }
                DebugWifiDirectQrCoordinator.Stage.HOST_SHOW -> {
                    LocalPairingQrDisplay(preparation.qrPayload)
                    Button(onClick = { preparation.scanReply() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text("SCAN JOINER'S REPLY QR")
                    }
                }
                DebugWifiDirectQrCoordinator.Stage.JOIN_SHOW_REPLY -> {
                    LocalPairingQrDisplay(preparation.qrPayload)
                    Button(onClick = { preparation.scanAck() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text("SCAN HOST'S THIRD QR")
                    }
                }
                DebugWifiDirectQrCoordinator.Stage.HOST_SHOW_ACK -> {
                    LocalPairingQrDisplay(preparation.qrPayload)
                    Text("Show this third QR to the joiner. Compare the short code in person.")
                }
                DebugWifiDirectQrCoordinator.Stage.HOST_SCAN_REPLY,
                DebugWifiDirectQrCoordinator.Stage.JOIN_SCAN_HOST,
                DebugWifiDirectQrCoordinator.Stage.JOIN_SCAN_ACK -> {
                    key(preparation.scannerGeneration) {
                        LocalPairingQrScanner(
                            onPayload = preparation::scanned,
                            onCameraError = preparation::cameraFailed
                        )
                    }
                    OutlinedButton(onClick = { preparation.retryScanner() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("RETRY SCANNER")
                    }
                }
                DebugWifiDirectQrCoordinator.Stage.JOIN_PREPARED -> {
                    Text("Third QR checked. Compare the short code on both phones.")
                }
            }
        }
        if (preparation.comparison.isNotEmpty()) {
            Text("IN-PERSON CODE: ${preparation.comparison}",
                style = MaterialTheme.typography.titleMedium)
        }
        Text(preparation.status, style = MaterialTheme.typography.bodySmall)
        if (preparation.stage != DebugWifiDirectQrCoordinator.Stage.CHOOSE) {
            OutlinedButton(
                onClick = { preparation.clear() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text("CANCEL AND CLEAR QR CODES") }
        }
    }
}
