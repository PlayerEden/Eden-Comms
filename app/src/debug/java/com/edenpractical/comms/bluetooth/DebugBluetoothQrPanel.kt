package com.edenpractical.comms.bluetooth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
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

/** Debug-only QR metadata exchange; this is NOT authentication of an RF socket. */
@Composable
internal fun DebugBluetoothQrPanel(qr: DebugBluetoothQrCoordinator) {
    // QR progress is guidance only; a prepared exchange is NOT a trusted peer.
    val (stepTitle, nextAction) = when (qr.stage) {
        DebugBluetoothQrCoordinator.Stage.CHOOSE ->
            "QR STEP 1 OF 2" to
                "One phone shows the first code; the other scans it in person."
        DebugBluetoothQrCoordinator.Stage.HOST_SHOW ->
            "QR STEP 1 OF 2" to
                "Let the other phone scan this first QR. Then scan their reply."
        DebugBluetoothQrCoordinator.Stage.JOIN_SCAN_HOST ->
            "QR STEP 1 OF 2" to
                "Scan the first QR displayed by the other phone."
        DebugBluetoothQrCoordinator.Stage.HOST_SCAN_REPLY ->
            "QR STEP 2 OF 2" to
                "Scan the reply QR displayed by the joining phone."
        DebugBluetoothQrCoordinator.Stage.JOIN_SHOW_REPLY ->
            "QR STEP 2 OF 2" to
                "Show your reply QR to the host. Continue only after they have scanned it."
        DebugBluetoothQrCoordinator.Stage.HOST_PREPARED,
        DebugBluetoothQrCoordinator.Stage.JOIN_PREPARED ->
            "QR CODES EXCHANGED" to
                "The link is still unverified. Complete the later TLS checks and both approvals."
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "IN-PERSON BLUETOOTH QR — UNVERIFIED",
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            "Both people exchange two fresh QR codes before this development " +
                "socket test. Scanning is NOT peer authentication, and no chat " +
                "or voice can be sent. Only proceed with someone physically present."
        )
        Text(stepTitle, style = MaterialTheme.typography.titleMedium)
        Text(nextAction, style = MaterialTheme.typography.bodyMedium)
        if (!qr.identityReady) {
            Button(
                onClick = { qr.createIdentityWithConsent() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) { Text("CREATE LOCAL DEVICE IDENTITY") }
            Text(
                "This creates an Android Keystore-backed local identity only " +
                    "after your tap. Existing identities will not be replaced."
            )
        } else {
            when (qr.stage) {
                DebugBluetoothQrCoordinator.Stage.CHOOSE -> {
                    Button(
                        onClick = { qr.startHost() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) { Text("1 — SHOW MY FRESH HOST QR") }
                    OutlinedButton(
                        onClick = { qr.startJoin() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) { Text("1 — SCAN OTHER PHONE'S HOST QR") }
                }
                DebugBluetoothQrCoordinator.Stage.HOST_SHOW -> {
                    LocalPairingQrDisplay(qr.qrPayload)
                    Button(
                        onClick = { qr.scanReply() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) { Text("2 — SCAN OTHER PHONE'S REPLY QR") }
                }
                DebugBluetoothQrCoordinator.Stage.HOST_SCAN_REPLY,
                DebugBluetoothQrCoordinator.Stage.JOIN_SCAN_HOST -> {
                    key(qr.scannerGeneration) {
                        LocalPairingQrScanner(
                            onPayload = qr::scanned,
                            onCameraError = {
                                // Never automatically reopen a broken camera forever.
                                qr.cameraFailed()
                            }
                        )
                    }
                    OutlinedButton(
                        onClick = { qr.retryCamera() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) { Text("RETRY QR CAMERA") }
                }
                DebugBluetoothQrCoordinator.Stage.JOIN_SHOW_REPLY -> {
                    LocalPairingQrDisplay(qr.qrPayload)
                    Text("The host must scan this reply. You cannot verify that here.")
                    Button(
                        onClick = { qr.replyShown() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) { Text("I SHOWED THE REPLY — CONTINUE") }
                }
                DebugBluetoothQrCoordinator.Stage.HOST_PREPARED,
                DebugBluetoothQrCoordinator.Stage.JOIN_PREPARED -> {
                    Text(
                        "QR metadata prepared locally only. Not a trusted " +
                            "peer or authenticated transport. Continue below " +
                            "only for the bounded debug RFCOMM socket experiment."
                    )
                }
            }
        }
        Text(
            qr.status,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodySmall
        )
        if (qr.stage != DebugBluetoothQrCoordinator.Stage.CHOOSE) {
            OutlinedButton(
                onClick = { qr.clear() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) { Text("CANCEL AND ERASE QR PREPARATION") }
        }
    }
}
