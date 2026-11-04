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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.edenpractical.comms.security.AndroidKeystoreIdentity
import com.edenpractical.comms.security.LocalTlsIdentity
import com.edenpractical.comms.security.SessionProofBinding
import com.edenpractical.comms.transport.LanTlsProbe
import com.edenpractical.comms.transport.LocalIpv4Policy
import com.edenpractical.comms.transport.WifiLanEndpoint
import com.edenpractical.comms.ui.FieldFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Guided two-QR LOCAL CONNECTION TEST. The second QR is still required:
 * it authenticates the joiner's identity to the presenter.
 *
 * No manual listener or Connect buttons, chat/voice traffic, remembered peers,
 * cloud services, background discovery, or plaintext fallback.
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
    var wifi by remember { mutableStateOf<WifiLanEndpoint.Link?>(null) }
    var stage by remember { mutableStateOf("checking") }
    var status by remember { mutableStateOf("Checking this phone…") }
    var details by remember { mutableStateOf("") }
    var showDetails by remember { mutableStateOf(false) }
    var passed by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var showIdentityConsent by remember { mutableStateOf(false) }
    var showUpgradeConfirmation by remember { mutableStateOf(false) }
    var pendingRole by remember { mutableStateOf<String?>(null) }
    var hostInvite by remember { mutableStateOf<LocalPairingQrCodec.HostInvite?>(null) }
    var joinReply by remember { mutableStateOf<LocalPairingQrCodec.JoinReply?>(null) }
    var peerPin by remember { mutableStateOf<ByteArray?>(null) }
    var qrText by remember { mutableStateOf("") }
    var scanRevision by remember { mutableIntStateOf(0) }
    var attemptVersion by remember { mutableIntStateOf(0) }
    var probe by remember { mutableStateOf<LanTlsProbe?>(null) }
    val latestProbe by rememberUpdatedState(probe)

    fun stopTest() {
        // Ignore messages posted by previous or cancelled attempts.
        attemptVersion += 1
        probe?.close()
        probe = null
    }

    fun reset() {
        stopTest()
        hostInvite = null
        joinReply = null
        peerPin = null
        qrText = ""
        details = ""
        showDetails = false
        passed = false
        pendingRole = null
        stage = "choose"
        status = "Choose how to begin."
    }

    fun startRole(role: String) {
        if (loading) return
        pendingRole = role
        if (identity == null) {
            showIdentityConsent = true
            return
        }
        if (signingStatus == AndroidKeystoreIdentity.TlsSigningStatus.LEGACY_SHA256_ONLY) {
            showUpgradeConfirmation = true
            return
        }
        try {
            val link = WifiLanEndpoint.current(context) ?: error("Wi-Fi unavailable")
            wifi = link
            if (role == "show") {
                val invite = LocalPairingQrCodec.newHostInvite(
                    link.address.hostAddress ?: error("Wi-Fi address unavailable"),
                    identity!!.publicFingerprint()
                )
                hostInvite = invite
                joinReply = null
                peerPin = null
                qrText = LocalPairingQrCodec.encodeHost(invite)
                stage = "showHost"
                status = "Ask the other person to scan this code."
            } else {
                hostInvite = null
                joinReply = null
                peerPin = null
                qrText = ""
                scanRevision += 1
                stage = "scanHost"
                status = "Point your camera at the code on their phone."
            }
        } catch (_: Exception) {
            stage = "choose"
            status = "Connect both phones to the same Wi-Fi and try again."
        }
    }

    fun runProbe(asPresenter: Boolean) {
        val own = identity
        val expected = peerPin
        val invite = hostInvite
        val reply = joinReply
        if (own == null || expected == null || invite == null || reply == null) {
            stage = "result"
            status = "Pairing was incomplete. Start again with fresh codes."
            passed = false
            return
        }
        try {
            LocalPairingQrCodec.requireFresh(invite, System.currentTimeMillis() / 1000)
            val binding = SessionProofBinding(
                invite.certificateSha256, reply.certificateSha256, invite.nonce, reply.nonce
            )
            val ownPin = if (asPresenter) invite.certificateSha256 else reply.certificateSha256
            require(own.publicFingerprint().contentEquals(ownPin)) {
                "Local identity changed"
            }
            if (!asPresenter) {
                val link = WifiLanEndpoint.current(context) ?: error("Wi-Fi lost")
                LocalIpv4Policy.requireSamePrivateSubnet(
                    invite.wifiIp, link.address, link.prefixLength
                )
            }
            stopTest()
            val thisAttempt = attemptVersion
            val connection = LanTlsProbe(context.applicationContext) { event ->
                handler.post {
                    if (thisAttempt != attemptVersion) return@post
                    when (event.phase) {
                        LanTlsProbe.Phase.LISTENING -> status =
                            "Waiting for the other phone to connect…"
                        LanTlsProbe.Phase.CONNECTING -> status =
                            "Show your code on the other phone. Connecting automatically…"
                        LanTlsProbe.Phase.SUCCEEDED -> {
                            passed = true
                            details = event.description
                            status = "Secure connection test passed on this phone."
                            stage = "result"
                        }
                        LanTlsProbe.Phase.FAILED -> {
                            passed = false
                            details = event.description
                            status = "Couldn't complete the connection. Check both phones are on the same Wi-Fi."
                            stage = "result"
                        }
                        LanTlsProbe.Phase.CANCELLED -> Unit
                    }
                }
            }
            probe = connection
            // Keep reply QR visible while the other phone scans it.
            stage = if (asPresenter) "connecting" else "showReply"
            status = if (asPresenter) "Waiting for the other phone to connect…"
                else "Show this code to the other phone. Connecting automatically…"
            if (asPresenter) connection.host(own, expected, binding)
            else connection.join(
                own, expected, invite.wifiIp, binding, retryWhileWaiting = true
            )
        } catch (_: Exception) {
            stopTest()
            stage = "result"
            passed = false
            details = "Pairing codes were invalid, expired, or the Wi-Fi network changed."
            status = "Couldn't start the test. Start again with fresh codes."
        }
    }

    LaunchedEffect(Unit) {
        try {
            val state = withContext(Dispatchers.IO) {
                Triple(repository.existing(), WifiLanEndpoint.current(context),
                    repository.tlsSigningStatus())
            }
            identity = state.first
            wifi = state.second
            signingStatus = state.third
            stage = "choose"
            status = if (wifi == null) "Connect both phones to the same Wi-Fi."
                else "Choose how to begin."
        } catch (_: Exception) {
            stage = "identityError"
            status = "This phone couldn't open its local identity."
        }
    }

    // Stop test sockets if the user leaves the app, including during retries.
    // Launching a camera permission dialog normally does not stop the Activity.
    val lifecycleOwner = context as? LifecycleOwner
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP &&
                stage !in listOf("checking", "choose", "result", "identityError")
            ) {
                stopTest()
                passed = false
                details = "The app left the screen, so the connection test was cancelled."
                status = "Connection cancelled. Start again when both phones are ready."
                stage = "result"
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
            latestProbe?.close()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Connect nearby", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold)
        Text(
            "Connection test only • Both phones need the same Wi-Fi. No internet required.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FieldFrame(accent = true) {
            Text(status, style = MaterialTheme.typography.bodyMedium)
            when (stage) {
                "checking" -> CircularProgressIndicator(modifier = Modifier.size(28.dp))
                "choose" -> {
                    Text(
                        "On one phone, choose Show a code. On the other, choose Scan a code.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (wifi == null) {
                        Text("Wi-Fi isn't available. Join the same network on both phones, then retry.",
                            style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(
                            onClick = {
                                wifi = WifiLanEndpoint.current(context)
                                status = if (wifi == null) "Wi-Fi is still unavailable."
                                    else "Choose how to begin."
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) { Text("Check Wi-Fi again") }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { startRole("show") },
                                enabled = !loading,
                                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                                shape = CutCornerShape(6.dp)
                            ) { Text("Show a code") }
                            OutlinedButton(
                                onClick = { startRole("scan") },
                                enabled = !loading,
                                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                                shape = CutCornerShape(6.dp)
                            ) { Text("Scan a code") }
                        }
                    }
                    if (loading) CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    Text("The app handles the connection automatically after scanning. " +
                        "You will scan one more code to verify both phones.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                "showHost" -> {
                    Text("1 of 2 • Let the other person scan this code.",
                        style = MaterialTheme.typography.bodySmall)
                    LocalPairingQrDisplay(qrText)
                    Button(
                        onClick = {
                            scanRevision += 1
                            stage = "scanReply"
                            status = "Now scan the code shown on the other phone."
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) { Text("Scan their code") }
                    TextButton(onClick = { reset() }) { Text("Cancel") }
                }
                "scanHost", "scanReply" -> {
                    Text("Point your camera at the code on the other phone.",
                        style = MaterialTheme.typography.bodySmall)
                    key(scanRevision) {
                        LocalPairingQrScanner(
                            onPayload = { payload ->
                                try {
                                    if (stage == "scanHost") {
                                        val invite = LocalPairingQrCodec.decodeHost(payload)
                                        val link = WifiLanEndpoint.current(context)
                                            ?: error("Wi-Fi unavailable")
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
                                        runProbe(false)
                                    } else if (stage == "scanReply") {
                                        val invite = hostInvite ?: error("No active invitation")
                                        val reply = LocalPairingQrCodec.decodeJoin(payload, invite)
                                        joinReply = reply
                                        peerPin = reply.certificateSha256
                                        stage = "connecting"
                                        runProbe(true)
                                    }
                                } catch (_: Exception) {
                                    status = "That code didn't work. Try scanning again."
                                    scanRevision += 1
                                }
                            },
                            onCameraError = { status = it }
                        )
                    }
                    TextButton(onClick = { reset() }) { Text("Cancel") }
                }
                "showReply" -> {
                    Text("2 of 2 • Show this code to the first phone.",
                        style = MaterialTheme.typography.bodySmall)
                    LocalPairingQrDisplay(qrText)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Connecting in the background of this screen…",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { reset() }) { Text("Cancel test") }
                }
                "connecting" -> {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    Text("Keep both phones open while we check the connection.",
                        style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { reset() }) { Text("Cancel test") }
                }
                "result" -> {
                    Text(if (passed) "✓ Connection check passed" else "Connection not completed",
                        fontWeight = FontWeight.SemiBold)
                    Text("No messages or voice were sent. The test connection is closed.",
                        style = MaterialTheme.typography.bodySmall)
                    if (details.isNotEmpty()) {
                        TextButton(onClick = { showDetails = !showDetails }) {
                            Text(if (showDetails) "Hide technical details" else "Technical details")
                        }
                        if (showDetails) Text(details,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = { reset() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) { Text("Start a new test") }
                }
                "identityError" -> {
                    Text("Open Device identity in Developer Tools for diagnostics.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text(
            "During development, both people verify each other's phone with QR codes. " +
                "Text and Voice / PTT remain disabled until security testing is complete.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (showIdentityConsent) {
        AlertDialog(
            onDismissRequest = { showIdentityConsent = false; pendingRole = null },
            title = { Text("Set up a private device key?") },
            text = {
                Text("Eden Comms needs a local identity to verify this phone. " +
                    "Its private key stays in Android Keystore on this phone. " +
                    "It isn't uploaded or shared.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showIdentityConsent = false
                    val chosen = pendingRole
                    scope.launch {
                        loading = true
                        try {
                            val created = withContext(Dispatchers.IO) {
                                Pair(repository.createWithConsent(), repository.tlsSigningStatus())
                            }
                            identity = created.first
                            signingStatus = created.second
                            if (created.second ==
                                AndroidKeystoreIdentity.TlsSigningStatus.LEGACY_SHA256_ONLY
                            ) {
                                showUpgradeConfirmation = true
                            } else if (chosen != null) {
                                startRole(chosen)
                            }
                        } catch (_: Exception) {
                            status = "Couldn't set up this phone's identity."
                        } finally {
                            loading = false
                        }
                    }
                }) { Text("Create and continue") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showIdentityConsent = false
                    pendingRole = null
                }) { Text("Not now") }
            }
        )
    }

    if (showUpgradeConfirmation) {
        AlertDialog(
            onDismissRequest = { showUpgradeConfirmation = false; pendingRole = null },
            title = { Text("Update this phone's test key?") },
            text = {
                Text("This older key may not work on some phones. " +
                    "Updating creates a compatible local key and changes this phone's identity. " +
                    "Previous QR codes will stop working, so both phones must use fresh codes.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showUpgradeConfirmation = false
                    val chosen = pendingRole
                    scope.launch {
                        loading = true
                        try {
                            val updated = withContext(Dispatchers.IO) {
                                Pair(repository.upgradeLegacyWithConsent(),
                                    repository.tlsSigningStatus())
                            }
                            identity = updated.first
                            signingStatus = updated.second
                            if (chosen != null) startRole(chosen)
                        } catch (_: Exception) {
                            status = "Couldn't update the test key. The old key should still exist."
                        } finally {
                            loading = false
                        }
                    }
                }) { Text("Update and continue") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showUpgradeConfirmation = false
                    pendingRole = null
                }) { Text("Cancel") }
            }
        )
    }
}
