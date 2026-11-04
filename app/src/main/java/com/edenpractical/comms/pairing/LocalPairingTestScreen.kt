package com.edenpractical.comms.pairing

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.edenpractical.comms.security.AndroidKeystoreIdentity
import com.edenpractical.comms.security.LocalTlsIdentity
import com.edenpractical.comms.transport.LanTlsProbe
import com.edenpractical.comms.transport.LocalIpv4Policy
import com.edenpractical.comms.transport.WifiLanEndpoint
import com.edenpractical.comms.ui.FieldFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One-shot QR-assisted mutual TLS 1.3 test.
 *
 * This screen only trades PUBLIC certificate fingerprints, an ephemeral host
 * address and nonces. No chat bytes, microphone or persistent peer trust.
 * Both scans happen in person; no clipboard, external messenger or website.
 */
@Composable
internal fun LocalPairingTestScreen() {
    val context = LocalContext.current
    val repository = remember { AndroidKeystoreIdentity() }
    val scope = rememberCoroutineScope()
    val handler = remember { Handler(Looper.getMainLooper()) }

    var identity by remember { mutableStateOf<LocalTlsIdentity?>(null) }
    var signingStatus by remember {
        mutableStateOf(AndroidKeystoreIdentity.TlsSigningStatus.NOT_CREATED)
    }
    var showUpgradeConfirmation by remember { mutableStateOf(false) }
    var wifi by remember { mutableStateOf<WifiLanEndpoint.Link?>(null) }
    var stage by remember { mutableStateOf("checking") }
    var status by remember { mutableStateOf("Checking this phone…") }
    var loading by remember { mutableStateOf(false) }
    var hostInvite by remember { mutableStateOf<LocalPairingQrCodec.HostInvite?>(null) }
    var joinReply by remember { mutableStateOf<LocalPairingQrCodec.JoinReply?>(null) }
    var peerPin by remember { mutableStateOf<ByteArray?>(null) }
    var qrText by remember { mutableStateOf("") }
    var scanRevision by remember { mutableIntStateOf(0) }
    var probe by remember { mutableStateOf<LanTlsProbe?>(null) }
    val latestProbe by rememberUpdatedState(probe)

    DisposableEffect(Unit) {
        onDispose { latestProbe?.close() }
    }

    LaunchedEffect(Unit) {
        try {
            val available = withContext(Dispatchers.IO) {
                Triple(
                    repository.existing(),
                    WifiLanEndpoint.current(context),
                    repository.tlsSigningStatus()
                )
            }
            identity = available.first
            wifi = available.second
            signingStatus = available.third
            stage = "choose"
            status = if (available.second == null) "Connect both phones to the same Wi-Fi."
                else if (available.first == null) "Create this phone's identity to begin."
                else "Choose Host on one phone and Join on the other."
        } catch (_: Exception) {
            stage = "identityError"
            status = "Cannot read the local device identity. No connection started."
        }
    }

    fun reset() {
        probe?.close()
        probe = null
        peerPin = null
        hostInvite = null
        joinReply = null
        qrText = ""
        stage = "choose"
        status = "Choose Host or Join to begin another test."
    }

    fun runProbe(host: Boolean) {
        val own = identity ?: run { status = "Create an identity first."; return }
        val expected = peerPin ?: run { status = "Scan the other phone's QR first."; return }
        try {
            if (!host) {
                val invite = hostInvite ?: error("Missing host invite")
                LocalPairingQrCodec.requireFresh(invite, System.currentTimeMillis() / 1000)
                val link = WifiLanEndpoint.current(context) ?: error("Wi-Fi lost")
                LocalIpv4Policy.requireSamePrivateSubnet(
                    invite.wifiIp, link.address, link.prefixLength
                )
            } else {
                LocalPairingQrCodec.requireFresh(
                    hostInvite ?: error("Missing host invite"),
                    System.currentTimeMillis() / 1000
                )
            }
            probe?.close()
            val connection = LanTlsProbe(context.applicationContext) { event ->
                handler.post {
                    status = event.description
                    if (event.phase == LanTlsProbe.Phase.SUCCEEDED ||
                        event.phase == LanTlsProbe.Phase.FAILED
                    ) stage = "result"
                }
            }
            probe = connection
            stage = "running"
            status = if (host) "Opening the local listener…"
                else "Connecting to the verified host…"
            if (host) connection.host(own, expected)
            else connection.join(own, expected, hostInvite!!.wifiIp)
        } catch (_: Exception) {
            stage = "result"
            status = "Secure connection test could not start. Restart the pairing."
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            "QR connection test",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Two phones on the same Wi-Fi. No internet service or message history.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        FieldFrame(accent = true) {
            Text(status, style = MaterialTheme.typography.bodyMedium)

            if (stage == "choose" && identity == null) {
                Text(
                    "Creating an identity keeps your private signing key in Android Keystore.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    enabled = !loading,
                    onClick = {
                        scope.launch {
                            loading = true
                            try {
                                val details = withContext(Dispatchers.IO) {
                                    Pair(repository.createWithConsent(), repository.tlsSigningStatus())
                                }
                                identity = details.first
                                signingStatus = details.second
                                status = "Identity ready. Choose Host or Join."
                            } catch (_: Exception) {
                                status = "Identity creation failed on this phone."
                            } finally {
                                loading = false
                            }
                        }
                    }
                ) { Text("Create device identity") }
            }

            if (stage == "choose" && identity != null &&
                signingStatus == AndroidKeystoreIdentity.TlsSigningStatus.LEGACY_SHA256_ONLY
            ) {
                Text(
                    "This phone has an older test identity. Android may need a different " +
                        "signing permission to complete TLS. Update it before pairing again.",
                    style = MaterialTheme.typography.bodySmall
                )
                Button(
                    enabled = !loading,
                    onClick = { showUpgradeConfirmation = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) { Text("Update this phone's identity") }
            }

            if (stage == "choose" && identity != null &&
                signingStatus != AndroidKeystoreIdentity.TlsSigningStatus.LEGACY_SHA256_ONLY
            ) {
                if (signingStatus == AndroidKeystoreIdentity.TlsSigningStatus.INSPECTION_UNAVAILABLE) {
                    Text(
                        "This phone couldn't report its signing permissions. " +
                            "TLS will still have to authenticate successfully.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        enabled = wifi != null && !loading,
                        onClick = {
                            try {
                                val link = WifiLanEndpoint.current(context)
                                    ?: error("Wi-Fi unavailable")
                                wifi = link
                                val host = LocalPairingQrCodec.newHostInvite(
                                    link.address.hostAddress ?: error("Missing IP"),
                                    identity!!.publicFingerprint()
                                )
                                hostInvite = host
                                qrText = LocalPairingQrCodec.encodeHost(host)
                                stage = "hostQr"
                                status = "Let the other person scan this QR on their phone."
                            } catch (_: Exception) {
                                status = "Wi-Fi or identity is unavailable. Try again."
                            }
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        shape = CutCornerShape(6.dp)
                    ) { Text("HOST") }
                    OutlinedButton(
                        enabled = wifi != null && !loading,
                        onClick = {
                            scanRevision++
                            stage = "scanHost"
                            status = "Scan the QR displayed on the host phone."
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        shape = CutCornerShape(6.dp)
                    ) { Text("JOIN") }
                }
            }

            when (stage) {
                "hostQr" -> {
                    Text(
                        "Step 1 — Ask the other phone to scan this. QR expires in five minutes.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    LocalPairingQrDisplay(qrText)
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            scanRevision++
                            stage = "scanReply"
                            status = "Scan the reply QR shown on the other phone."
                        }
                    ) { Text("Scan their reply QR") }
                    TextButton(onClick = { reset() }) { Text("Start over") }
                }
                "scanHost", "scanReply" -> {
                    Text(
                        if (stage == "scanHost") "Scan the host phone's QR."
                        else "Scan the other phone's reply QR.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    key(scanRevision) {
                        LocalPairingQrScanner(
                            onPayload = { payload ->
                                try {
                                    if (stage == "scanHost") {
                                        val invite = LocalPairingQrCodec.decodeHost(payload)
                                        val link = WifiLanEndpoint.current(context)
                                            ?: error("No Wi-Fi network")
                                        LocalIpv4Policy.requireSamePrivateSubnet(
                                            invite.wifiIp, link.address, link.prefixLength
                                        )
                                        val reply = LocalPairingQrCodec.newJoinReply(
                                            invite, identity!!.publicFingerprint()
                                        )
                                        hostInvite = invite
                                        joinReply = reply
                                        peerPin = invite.certificateSha256
                                        qrText = LocalPairingQrCodec.encodeJoin(reply)
                                        stage = "showReply"
                                        status = "Show this reply QR to the host. Then connect."
                                    } else {
                                        val host = hostInvite ?: error("Missing host invite")
                                        val reply = LocalPairingQrCodec.decodeJoin(payload, host)
                                        joinReply = reply
                                        peerPin = reply.certificateSha256
                                        stage = "hostReady"
                                        status = "QR exchange complete. Tap Start listening; wait until Host is ready before the other phone connects."
                                    }
                                } catch (_: Exception) {
                                    status = "That QR isn't valid for this session. Try scanning again."
                                    scanRevision++
                                }
                            },
                            onCameraError = { status = it }
                        )
                    }
                    TextButton(onClick = { reset() }) { Text("Cancel") }
                }
                "showReply" -> {
                    Text(
                        "Step 2 — Show this code to the host phone for scanning.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    LocalPairingQrDisplay(qrText)
                    Text(
                        "Wait until the host phone displays: Host is ready. Then tap Connect.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        onClick = { runProbe(false) }
                    ) { Text("Connect to host") }
                    TextButton(onClick = { reset() }) { Text("Start over") }
                }
                "hostReady" -> {
                    Text(
                        "Step 3 — Tap Start listening here. Wait for the Host is ready message, then tell the other person to tap Connect.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        onClick = { runProbe(true) }
                    ) { Text("Start listening (60 sec)") }
                    TextButton(onClick = { reset() }) { Text("Start over") }
                }
                "running" -> {
                    Text(
                        "Testing mutual TLS 1.3. No conversation data is sent.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = { reset() }) { Text("Cancel test") }
                }
                "result" -> {
                    Text(
                        "The test connection is closed. No text or voice was transmitted.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(onClick = { reset() }) { Text("New test") }
                }
                "identityError" -> {
                    Text("Open Device identity in Advanced Settings to inspect the problem.")
                }
            }
        }
        Text(
            "This development test uses two in-person QR scans to pin both phones. " +
                "Messaging and Voice / PTT remain disabled until full security testing passes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (showUpgradeConfirmation) {
        AlertDialog(
            onDismissRequest = { showUpgradeConfirmation = false },
            title = { Text("Update this phone's identity?") },
            text = {
                Text(
                    "This creates a new Android Keystore identity with TLS-compatible " +
                        "signing permissions. The old identity is replaced only after " +
                        "the new one is verified. Both phones must scan fresh QR codes. " +
                        "Any old pairing fingerprints will no longer match."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUpgradeConfirmation = false
                        scope.launch {
                            loading = true
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    Pair(
                                        repository.upgradeLegacyWithConsent(),
                                        repository.tlsSigningStatus()
                                    )
                                }
                                identity = result.first
                                signingStatus = result.second
                                reset()
                                status = "New identity ready. Restart the QR exchange on both phones."
                            } catch (_: Exception) {
                                status = "Identity update failed. The original identity should still be available."
                            } finally {
                                loading = false
                            }
                        }
                    }
                ) { Text("Update identity") }
            },
            dismissButton = {
                TextButton(onClick = { showUpgradeConfirmation = false }) { Text("Cancel") }
            }
        )
    }
}
