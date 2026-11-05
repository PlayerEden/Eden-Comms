package com.edenpractical.comms.pairing

import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.edenpractical.comms.security.AndroidKeystoreIdentity
import com.edenpractical.comms.security.LocalTlsIdentity
import com.edenpractical.comms.security.SessionProofBinding
import com.edenpractical.comms.protocol.TestPacketCodec
import com.edenpractical.comms.transport.LanDemoSession
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
 * No manual listener or Connect buttons, user-supplied chat/voice traffic,
 * remembered peers, cloud services, background discovery, or plaintext fallback.
 * Development builds may exchange ONLY fixed test phrases over proven TLS.
 */
@Composable
internal fun LocalPairingTestScreen() {
    val context = LocalContext.current
    if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) {
        Text("Local pairing tests are available only in development builds.")
        return
    }
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
    var cameraError by remember { mutableStateOf(false) }
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
    var probe by remember { mutableStateOf<LanDemoSession?>(null) }
    data class TestLine(val fromThisPhone: Boolean, val phrase: String)
    val testLines = remember { mutableStateListOf<TestLine>() }
    var sentCount by remember { mutableIntStateOf(0) }
    var receivedCount by remember { mutableIntStateOf(0) }
    var qaRole by remember { mutableStateOf(LocalQaReport.Role.NOT_SELECTED) }
    var qaFinish by remember { mutableStateOf(LocalQaReport.Finish.NOT_RECORDED) }
    val latestProbe by rememberUpdatedState(probe)

    fun stopTest() {
        // Ignore messages posted by previous or cancelled attempts.
        attemptVersion += 1
        probe?.close()
        probe = null
    }

    fun clearTemporaryPairing() {
        // QR fingerprints/nonces are public but privacy-sensitive metadata.
        // Drop Compose references when no longer needed (not secure erase).
        hostInvite = null
        joinReply = null
        peerPin = null
        qrText = ""
    }

    fun reset() {
        stopTest()
        clearTemporaryPairing()
        details = ""
        showDetails = false
        cameraError = false
        passed = false
        testLines.clear()
        sentCount = 0
        receivedCount = 0
        qaRole = LocalQaReport.Role.NOT_SELECTED
        qaFinish = LocalQaReport.Finish.NOT_RECORDED
        pendingRole = null
        stage = "choose"
        status = "Choose how to begin."
    }

    fun startRole(role: String) {
        // The buttons are disabled while loading; continuation after explicit
        // identity consent also needs to enter this flow before loading clears.
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
            qaRole = if (role == "show") LocalQaReport.Role.PRESENTER
                else LocalQaReport.Role.JOINER
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
                cameraError = false
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
            val connection = LanDemoSession(context.applicationContext) { event ->
                handler.post {
                    if (thisAttempt != attemptVersion) return@post
                    when (event.phase) {
                        LanDemoSession.Phase.LISTENING -> status =
                            "Waiting for the other phone to connect…"
                        LanDemoSession.Phase.CONNECTING -> status =
                            "Show your code to the other phone. Connecting automatically…"
                        LanDemoSession.Phase.READY -> {
                            clearTemporaryPairing()
                            passed = true
                            details = event.description
                            status = "Connected for the two-phone test."
                            stage = "demo"
                        }
                        LanDemoSession.Phase.SENT, LanDemoSession.Phase.RECEIVED -> {
                            // Events from writer and reader threads can be queued
                            // around disconnect. Never resurrect an ended transcript.
                            if (stage != "demo") return@post
                            val phrase = event.phraseId?.let(TestPacketCodec::phrase)
                            if (phrase != null) {
                                val fromThisPhone = event.phase == LanDemoSession.Phase.SENT
                                if (fromThisPhone) sentCount++ else receivedCount++
                                testLines.add(TestLine(fromThisPhone, phrase))
                                while (testLines.size > 8) testLines.removeAt(0)
                            }
                        }
                        LanDemoSession.Phase.ENDED -> {
                            qaFinish = LocalQaReport.Finish.REMOTE_OR_LINK_STOPPED
                            clearTemporaryPairing()
                            details = event.description
                            status = "Test connection ended."
                            testLines.clear()
                            stage = "result"
                        }
                        LanDemoSession.Phase.FAILED -> {
                            qaFinish = LocalQaReport.Finish.CONNECTION_FAILED
                            clearTemporaryPairing()
                            passed = false
                            details = event.description
                            status = "Couldn't connect. Check both phones are on the same Wi-Fi."
                            testLines.clear()
                            stage = "result"
                        }
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
            qaFinish = LocalQaReport.Finish.CONNECTION_FAILED
            stopTest()
            clearTemporaryPairing()
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
                val hadConnected = stage == "demo"
                qaFinish = LocalQaReport.Finish.APP_BACKGROUNDED
                stopTest()
                clearTemporaryPairing()
                testLines.clear()
                passed = hadConnected
                details = "The app went into the background, closing the local test socket."
                status = if (hadConnected) "Test connection ended when the app left the screen."
                    else "Connection cancelled. Start again when both phones are ready."
                stage = "result"
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
            latestProbe?.close()
            clearTemporaryPairing()
            testLines.clear()
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
            PairingTestUiCopy.progress(stage)?.let { progress ->
                Text(progress, style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary)
            }
            Text(status, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
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
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val stack = PairingTestUiCopy.useStackedActions(
                                maxWidth.value, LocalDensity.current.fontScale)
                            if (stack) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { startRole("show") },
                                        enabled = !loading,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                        shape = CutCornerShape(6.dp)
                                    ) { Text("Show a code") }
                                    OutlinedButton(
                                        onClick = { startRole("scan") },
                                        enabled = !loading,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                        shape = CutCornerShape(6.dp)
                                    ) { Text("Scan a code") }
                                }
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
                            cameraError = false
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
                            onCameraError = {
                                cameraError = true
                                status = it
                            }
                        )
                    }
                    if (cameraError) {
                        OutlinedButton(
                            onClick = {
                                cameraError = false
                                scanRevision += 1
                                status = "Restarting the QR camera…"
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) { Text("Restart camera") }
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
                        Text("Connecting automatically. Keep this code visible…",
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
                "demo" -> {
                    Text("Connected — test conversation", fontWeight = FontWeight.SemiBold)
                    Text("Only predefined test phrases are available. No personal messages, voice, or saved history.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // A connected debug socket is not user consent or authorization
                    // to send real text. Keep provider diagnostics behind a disclosure.
                    TextButton(onClick = { showDetails = !showDetails }) {
                        Text(if (showDetails) "Hide technical link status" else "Technical link status")
                    }
                    if (showDetails) {
                        Text(details,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("This phone's channel-binding result is a compatibility check, not bilateral approval.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("Written to TLS here: $sentCount  •  Received here: $receivedCount",
                        style = MaterialTheme.typography.labelMedium)
                    if (testLines.isEmpty()) {
                        Text("No preset phrases yet. Send one and compare both phones.",
                            style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text("RECENT TEST ACTIVITY • LAST 8",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp),
                            modifier = Modifier.fillMaxWidth()) {
                            testLines.forEach { line ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = if (line.fromThisPhone) Arrangement.End
                                        else Arrangement.Start
                                ) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(0.9f),
                                        shape = CutCornerShape(6.dp),
                                        color = if (line.fromThisPhone)
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text(
                                                if (line.fromThisPhone) "You • Written to TLS"
                                                else "Other phone • Received",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(line.phrase, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val columns = if (PairingTestUiCopy.useStackedActions(
                            maxWidth.value, LocalDensity.current.fontScale)) 1 else 2
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TestPacketCodec.phrases.chunked(columns).forEach { group ->
                                Row(modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    group.forEach { phrase ->
                                        OutlinedButton(
                                            onClick = {
                                                when (probe?.sendPreset(phrase.id)) {
                                                    LanDemoSession.SendResult.QUEUED -> Unit
                                                    LanDemoSession.SendResult.BUSY ->
                                                        status = "Sending… Try again in a moment."
                                                    LanDemoSession.SendResult.NOT_CONNECTED, null ->
                                                        status = "The test is no longer connected."
                                                }
                                            },
                                            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                                            shape = CutCornerShape(6.dp)
                                        ) {
                                            Text(phrase.label,
                                                style = MaterialTheme.typography.bodyMedium,
                                                textAlign = TextAlign.Center,
                                                maxLines = 3)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Text("Sent means written to TLS, not a guaranteed delivery receipt. Compare both phone screens.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = {
                            val ending = probe
                            qaFinish = LocalQaReport.Finish.USER_ENDED
                            probe = null
                            attemptVersion += 1
                            ending?.endByUser()
                            clearTemporaryPairing()
                            details = "Test ended on this phone."
                            status = "Test ended."
                            testLines.clear()
                            stage = "result"
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) { Text("End test and disconnect") }
                }
                "result" -> {
                    Text(PairingTestUiCopy.resultHeading(passed),
                        fontWeight = FontWeight.SemiBold)
                    Text(PairingTestUiCopy.resultSummary(passed, sentCount, receivedCount),
                        style = MaterialTheme.typography.bodySmall)
                    if (details.isNotEmpty()) {
                        Text(details, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("Voice and normal messaging remain disabled.",
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(
                        onClick = {
                            // Explicit tap only. The report contains fixed categories,
                            // counts and platform version: never QR/pin/IP/text/details.
                            val installedVersion = try {
                                context.packageManager.getPackageInfo(
                                    context.packageName, 0
                                ).versionName ?: "unavailable"
                            } catch (_: Exception) { "unavailable" }
                            val observation = LocalQaReport.Observation(
                                versionName = installedVersion,
                                androidApiLevel = Build.VERSION.SDK_INT,
                                role = qaRole,
                                reachedReady = passed,
                                writtenToTls = sentCount,
                                receivedHere = receivedCount,
                                finish = qaFinish
                            )
                            try {
                                val clipboard = context.getSystemService(
                                    ClipboardManager::class.java
                                )
                                clipboard?.setPrimaryClip(ClipData.newPlainText(
                                    "Eden Comms local test observation",
                                    LocalQaReport.format(observation)
                                ))
                                status = "Local test summary copied. Paste it into your QA notes."
                            } catch (_: Exception) {
                                status = "Couldn't copy the test summary on this phone."
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) { Text("Copy this phone's test summary") }
                    Text(
                        "Optional: copies only test status, counts, app version and Android API to " +
                            "the system clipboard—never pairing codes, peer identities or messages. " +
                            "Clipboard content can be visible to the OS and other apps.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
            "This development build can send only preset test phrases over a verified local connection. " +
                "Normal text messaging, history and Voice / PTT remain disabled.",
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
