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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
    // Explain the *current* QR action; stages never indicate approval or trust.
    // Keep the three-code exchange distinct from later TLS and human consent.
    val (stepTitle, nextAction) = when (preparation.stage) {
        DebugWifiDirectQrCoordinator.Stage.CHOOSE ->
            "QR STEP 1 OF 3" to if (hostRole)
                "Show your first QR to the other phone. No connection is approved yet."
            else "Scan the host's first QR. No connection is approved yet."
        DebugWifiDirectQrCoordinator.Stage.HOST_SHOW ->
            "QR STEP 1 OF 3" to
                "Keep your QR visible while the joiner scans it. Then scan their reply."
        DebugWifiDirectQrCoordinator.Stage.JOIN_SCAN_HOST ->
            "QR STEP 1 OF 3" to
                "Scan the first QR shown by the host. This does not approve the phone."
        DebugWifiDirectQrCoordinator.Stage.HOST_SCAN_REPLY ->
            "QR STEP 2 OF 3" to
                "Scan the reply QR now shown on the joiner's phone."
        DebugWifiDirectQrCoordinator.Stage.JOIN_SHOW_REPLY ->
            "QR STEP 2 OF 3" to
                "Show this reply QR to the host. Then scan the host's final QR."
        DebugWifiDirectQrCoordinator.Stage.HOST_SHOW_ACK ->
            "QR STEP 3 OF 3" to
                "Show this final QR to the joiner, then compare the short code in person."
        DebugWifiDirectQrCoordinator.Stage.JOIN_SCAN_ACK ->
            "QR STEP 3 OF 3" to
                "Scan the host's final QR, then compare the short code in person."
        DebugWifiDirectQrCoordinator.Stage.JOIN_PREPARED ->
            "QR CODES EXCHANGED" to
                "Compare the short code in person. The connection still needs verification and both approvals."
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stepTitle,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium
        )
        Text(nextAction, style = MaterialTheme.typography.bodyMedium)
        Text(
            "Three fresh QR codes are exchanged in person. Neither QR scanning " +
                "nor radio proximity authorizes text or voice.",
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
                            onCameraError = { preparation.cameraFailed() }
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
        Text(
            preparation.status,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodySmall
        )
        if (preparation.stage != DebugWifiDirectQrCoordinator.Stage.CHOOSE) {
            OutlinedButton(
                onClick = { preparation.clear() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text("CANCEL AND CLEAR QR CODES") }
        }
    }
}
