package com.edenpractical.comms

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.edenpractical.comms.model.ReleaseCapabilities
import com.edenpractical.comms.security.AndroidKeystoreIdentity
import com.edenpractical.comms.security.PairingInviteCodec
import com.edenpractical.comms.transport.LanTlsProbe
import com.edenpractical.comms.transport.WifiLanEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Graphite = Color(0xFF121613)
private val GraphiteSurface = Color(0xFF1D231F)
private val GraphiteRaised = Color(0xFF272E28)
private val SoftIvory = Color(0xFFE9EEE7)
private val PaleSage = Color(0xFFB9CCB9)
private val LightSurface = Color(0xFFFFFFFF)
private val Ink = Color(0xFF232A25)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Development APKs allow screenshots so testers can report visual bugs.
        // Keep the release-only protection; revisit its UX and accessibility
        // before shipping a production app.
        val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!isDebuggable) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        // Reduce unintended data collection by autofill/content-capture services.
        window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.decorView.importantForContentCapture =
                View.IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS
        }
        setContent { EdenCommsApp() }
    }
}

@Composable
private fun EdenCommsApp() {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("eden_comms_ui", Context.MODE_PRIVATE)
    }
    var darkMode by remember { mutableStateOf(preferences.getBoolean("dark_theme", true)) }
    var page by rememberSaveable { mutableStateOf("home") }

    val colors = if (darkMode) {
        darkColorScheme(
            primary = PaleSage,
            onPrimary = Graphite,
            background = Graphite,
            onBackground = SoftIvory,
            surface = GraphiteSurface,
            onSurface = SoftIvory,
            surfaceVariant = GraphiteRaised,
            onSurfaceVariant = Color(0xFFB7C1B5),
            outline = Color(0xFF515F53)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF42634A),
            onPrimary = Color.White,
            background = Color(0xFFF5F6F2),
            onBackground = Ink,
            surface = LightSurface,
            onSurface = Ink,
            surfaceVariant = Color(0xFFECF0EA),
            onSurfaceVariant = Color(0xFF58665C),
            outline = Color(0xFFC5CEC5)
        )
    }

    val navigate: (String) -> Unit = { page = it }
    val back: () -> Unit = {
        page = when (page) {
            "lan-test" -> "developer"
            "developer", "identity" -> "settings"
            else -> "home"
        }
    }

    MaterialTheme(colorScheme = colors) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                TopBar(page = page, onBack = back)
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
                )
                // Page-keyed scroll prevents a previous long developer screen
                // leaving the next screen scrolled out of view.
                key(page) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        when (page) {
                            "home" -> HomeScreen(
                                onConnect = { navigate("connect") },
                                onSettings = { navigate("settings") },
                                onHelp = { navigate("help") }
                            )
                            "connect" -> NearbyScreen()
                            "settings" -> SettingsScreen(
                                darkMode = darkMode,
                                onDarkModeChange = {
                                    darkMode = it
                                    preferences.edit().putBoolean("dark_theme", it).apply()
                                },
                                onIdentity = { navigate("identity") },
                                onDeveloper = { navigate("developer") }
                            )
                            "developer" -> DeveloperScreen(
                                onIdentity = { navigate("identity") },
                                onLanTest = { navigate("lan-test") }
                            )
                            "identity" -> DeviceIdentitySetup()
                            "lan-test" -> LanHandshakeTest()
                            "help" -> HelpScreen()
                            else -> HomeScreen(
                                onConnect = { navigate("connect") },
                                onSettings = { navigate("settings") },
                                onHelp = { navigate("help") }
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

private fun titleForPage(page: String): String = when (page) {
    "connect" -> "Connect nearby"
    "settings" -> "Settings"
    "developer" -> "Developer tools"
    "identity" -> "Device identity"
    "lan-test" -> "LAN connection test"
    "help" -> "How it works"
    else -> "Eden Comms"
}

@Composable
private fun TopBar(page: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = 62.dp)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (page == "home") {
            Image(
                painter = painterResource(R.drawable.ic_eden_comms),
                contentDescription = null,
                modifier = Modifier.size(38.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    "Eden Comms",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "EDEN PRACTICAL  /  COMMUNICATIONS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp).semantics { contentDescription = "Back" }
            ) {
                Text(
                    "←",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                titleForPage(page),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HomeScreen(
    onConnect: () -> Unit,
    onSettings: () -> Unit,
    onHelp: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Nearby communication",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Simple, direct conversations on local Wi-Fi.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    ConnectionStatus(
        title = "Not connected",
        detail = "No active conversation"
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            onClick = onConnect,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Connect nearby", fontWeight = FontWeight.SemiBold)
        }
        Text(
            "Secure messaging is still in development.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        NavigationRow("Settings", "Appearance and privacy", onSettings)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
        NavigationRow("How it works", "How local conversations work", onHelp)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Session-only by default",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "No account needed",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NearbyScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            "Local Wi-Fi",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Use the same Wi-Fi network. Internet access isn't required.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Panel(title = "Secure pairing", detail = "Not available in this preview") {
        StatusLabel("IN DEVELOPMENT")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = {},
                enabled = false,
                modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                shape = RoundedCornerShape(8.dp)
            ) { Text("Host") }
            OutlinedButton(
                onClick = {},
                enabled = false,
                modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                shape = RoundedCornerShape(8.dp)
            ) { Text("Join") }
        }
    }

    SectionLabel("PAIRING WILL BE SIMPLE")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StepLine("01", "Scan", "Use the QR code shown on the other phone")
        StepLine("02", "Confirm", "Both people approve the verified connection")
        StepLine("03", "Talk", "Messages stay locked until verification succeeds")
    }

    Text(
        "For internal LAN testing: Settings → Developer tools.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun SettingsScreen(
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    onIdentity: () -> Unit,
    onDeveloper: () -> Unit
) {
    SectionLabel("PREFERENCES")
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text("Dark appearance", fontWeight = FontWeight.SemiBold)
            Text(
                "Calm, low-distraction design",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = darkMode, onCheckedChange = onDarkModeChange)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.30f))
    SectionLabel("PRIVACY")
    CompactFact("Message history", "Session-only (default)")
    Text(
        "Encrypted temporary history, up to 30 days per chat, is planned—not available yet.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.30f))
    SectionLabel("DEVICE")
    NavigationRow("Device identity", "Local cryptographic key and reset", onIdentity)
    NavigationRow("Developer tools", "Advanced LAN handshake testing", onDeveloper)
    Text(
        "LAN testing uses local Wi-Fi. It does not send messages or use an Eden Comms server.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun DeveloperScreen(
    onIdentity: () -> Unit,
    onLanTest: () -> Unit
) {
    Panel(
        title = "Internal testing",
        detail = "These tools are for development, not normal pairing."
    ) {
        StatusLabel("ADVANCED  •  TEST ONLY")
        Text(
            "The manual fingerprint is intentionally temporary. The final connection flow will use an in-app QR scan and authenticated confirmation.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
    NavigationRow(
        title = "Local device identity",
        description = "Create or inspect a device certificate",
        onClick = onIdentity
    )
    NavigationRow(
        title = "LAN TLS handshake test",
        description = "Host / Join over same Wi-Fi; no messages",
        onClick = onLanTest
    )
    Text(
        "Never test with sensitive conversations or share full fingerprints through another messaging app.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun HelpScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "A conversation, not a permanent transcript.",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Eden Comms is designed for direct conversations that can end without keeping a chat archive.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    SectionLabel("THREE SIMPLE STEPS")
    StepLine("01", "Connect", "Find and approve the nearby phone")
    StepLine("02", "Talk", "Exchange messages through a verified encrypted connection")
    StepLine("03", "Disconnect", "Leave without intentional history by default")

    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.30f))
    Text(
        "Privacy has limits.",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Text(
        "A participant can still remember, copy or photograph a conversation. " +
            "A compromised phone and some network metadata are outside the app's protection.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        if (ReleaseCapabilities.CAN_SEND_MESSAGES) "Messaging is available."
        else "Development preview: secure messaging is not active yet.",
        style = MaterialTheme.typography.bodySmall
    )
    Text(
        "Debug builds allow screenshots for testing. Release screen protection will be reviewed separately.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun CompactFact(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            title,
            modifier = Modifier.weight(0.37f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            value,
            modifier = Modifier.weight(0.63f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun ConnectionStatus(title: String, detail: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(7.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.27f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(
                Modifier.size(9.dp).background(
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    CircleShape
                )
            )
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    detail,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun StepLine(index: String, title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            index,
            modifier = Modifier.width(34.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NavigationRow(title: String, description: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
        contentPadding = PaddingValues(vertical = 6.dp, horizontal = 0.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "›",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DeviceIdentitySetup() {
    // No identity is created at app startup. Reading existing public
    // identity state and key generation happen off the UI thread.
    val repository = remember { AndroidKeystoreIdentity() }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var fingerprint by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Checking this phone…") }
    var confirmForget by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        busy = true
        try {
            fingerprint = withContext(Dispatchers.IO) {
                repository.existing()?.fingerprintDisplay()
            }
            status = if (fingerprint == null) "No identity created on this phone."
                else "Local public identity is ready. No connection is active."
        } catch (_: Exception) {
            status = "Could not inspect Android Keystore. No network connection was attempted."
        } finally {
            busy = false
        }
    }

    Panel(
        title = "Local device identity",
        detail = "Required before secure nearby connections. Created only if you choose."
    ) {
        StatusLabel("OFFLINE • NOT CONNECTED")
        Spacer(Modifier.height(12.dp))
        Text(status, style = MaterialTheme.typography.bodyMedium)
        if (fingerprint != null) {
            Spacer(Modifier.height(10.dp))
            Text("Public certificate fingerprint", style = MaterialTheme.typography.labelMedium)
            Text(
                fingerprint ?: "",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "This identifies your local key, not a verified other person. QR pairing and LAN are still being built.",
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Spacer(Modifier.height(10.dp))
            Button(
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        try {
                            val display = withContext(Dispatchers.IO) {
                                repository.createWithConsent().fingerprintDisplay()
                            }
                            fingerprint = display
                            status = "Local identity created. No connection is active."
                        } catch (_: Exception) {
                            status = "Identity creation failed on this device. No connection was enabled."
                        } finally {
                            busy = false
                        }
                    }
                }
            ) { Text(if (busy) "Working…" else "Create local identity") }
        }
        if (fingerprint != null) {
            Spacer(Modifier.height(10.dp))
            TextButton(
                enabled = !busy,
                onClick = { confirmForget = true }
            ) { Text("Forget this identity") }
        }
    }

    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text("Forget local identity?") },
            text = {
                Text(
                    "Other phones will no longer recognize this identity. You'll need to pair again. This does not erase messages on other phones."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmForget = false
                    scope.launch {
                        busy = true
                        try {
                            withContext(Dispatchers.IO) { repository.forgetWithConsent() }
                            fingerprint = null
                            status = "Local identity forgotten."
                        } catch (_: Exception) {
                            status = "Android Keystore could not remove the identity."
                        } finally {
                            busy = false
                        }
                    }
                }) { Text("Forget identity") }
            },
            dismissButton = {
                TextButton(onClick = { confirmForget = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun LanHandshakeTest() {
    val context = LocalContext.current
    val repository = remember { AndroidKeystoreIdentity() }
    val handler = remember { Handler(Looper.getMainLooper()) }
    var localFingerprint by remember { mutableStateOf<String?>(null) }
    var wifiAddress by remember { mutableStateOf<String?>(null) }
    var remoteFingerprintText by rememberSaveable { mutableStateOf("") }
    var hostIpv4 by rememberSaveable { mutableStateOf("") }
    var personallyChecked by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Checking local Wi-Fi and identity…") }
    var probe by remember { mutableStateOf<LanTlsProbe?>(null) }

    LaunchedEffect(Unit) {
        try {
            val details = withContext(Dispatchers.IO) {
                Pair(
                    repository.existing()?.fingerprintDisplay(),
                    WifiLanEndpoint.current(context)?.address?.hostAddress
                )
            }
            localFingerprint = details.first
            wifiAddress = details.second
            status = when {
                details.first == null -> "Open Settings → Device identity before testing."
                details.second == null -> "Join a local Wi-Fi network first. Router internet is not required."
                else -> "Ready for a one-shot, encrypted TLS handshake test. No messages will be sent."
            }
        } catch (_: Exception) {
            status = "Could not read device identity or Wi-Fi. No connection attempted."
        }
    }

    DisposableEffect(Unit) {
        onDispose { probe?.close() }
    }

    Panel(
        title = "LAN handshake • developer test",
        detail = "Advanced test using the same Wi-Fi network. Router internet is not required."
    ) {
        StatusLabel("DEVELOPMENT TEST • NO MESSAGING")
        Text(status, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "This phone's Wi-Fi address: " + (wifiAddress ?: "Not available"),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            "This phone's public fingerprint:",
            style = MaterialTheme.typography.labelMedium
        )
        Text(
            localFingerprint ?: "No local identity yet",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = remoteFingerprintText,
            onValueChange = { remoteFingerprintText = it.take(96) },
            label = { Text("Other phone's full fingerprint") },
            placeholder = { Text("64 hex digits; spaces allowed") },
            minLines = 2
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = personallyChecked,
                onCheckedChange = { personallyChecked = it }
            )
            Text(
                "I compared this fingerprint with the other physical phone.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = hostIpv4,
            onValueChange = { hostIpv4 = it.take(15) },
            label = { Text("Host phone's Wi-Fi IPv4 (Join only)") },
            placeholder = { Text("192.168.1.10") },
            singleLine = true
        )
        Spacer(Modifier.height(10.dp))
        val ready = localFingerprint != null && wifiAddress != null && personallyChecked && !busy
        val start: (Boolean) -> Unit = { joining ->
            try {
                val canonical = remoteFingerprintText.filterNot(Char::isWhitespace)
                val full = if (canonical.startsWith("EC1:", ignoreCase = true))
                    canonical.substring(4) else canonical
                val pin = PairingInviteCodec.decode("EC1:" + full.uppercase())
                // The actual identity/Keystore operation remains on a worker
                // thread. We never open a socket before explicit confirmation.
                probe?.close()
                busy = true
                status = "Preparing an authenticated local-only TLS handshake…"
                val connection = LanTlsProbe(context.applicationContext) { event ->
                    handler.post {
                        status = event.description
                        if (event.phase == LanTlsProbe.Phase.SUCCEEDED ||
                            event.phase == LanTlsProbe.Phase.FAILED ||
                            event.phase == LanTlsProbe.Phase.CANCELLED
                        ) busy = false
                    }
                }
                probe = connection
                val suppliedHost = hostIpv4
                Thread {
                    try {
                        val identity = repository.existing()
                            ?: throw IllegalStateException("No local identity")
                        if (joining) connection.join(identity, pin, suppliedHost)
                        else connection.host(identity, pin)
                    } catch (_: Exception) {
                        handler.post {
                            busy = false
                            status = "Unable to prepare local TLS identity; no connection opened."
                        }
                    }
                }.apply {
                    name = "eden-comms-identity-loader"
                    isDaemon = true
                    start()
                }
            } catch (_: IllegalArgumentException) {
                status = "Enter the other phone's entire 64-hex-digit fingerprint."
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                modifier = Modifier.weight(1f).height(52.dp),
                enabled = ready,
                onClick = { start(false) }
            ) { Text("Host test") }
            Button(
                modifier = Modifier.weight(1f).height(52.dp),
                enabled = ready && hostIpv4.isNotBlank(),
                onClick = { start(true) }
            ) { Text("Join test") }
        }
        if (busy) {
            TextButton(onClick = {
                probe?.close()
                busy = false
                status = "Test cancelled. All sockets were closed."
            }) { Text("Cancel test") }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "No messages or media are sent. Both phones must enter the other's fingerprint, " +
                "and the TLS 1.3 handshake must succeed. Close this screen to cancel.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun StatusLabel(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun Panel(
    title: String,
    detail: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                detail,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            content()
        }
    }
}
