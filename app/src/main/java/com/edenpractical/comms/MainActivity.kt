package com.edenpractical.comms

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edenpractical.comms.ui.FieldFrame
import com.edenpractical.comms.ui.FieldHeaderRule
import androidx.core.view.WindowCompat
import com.edenpractical.comms.model.ReleaseCapabilities
import com.edenpractical.comms.security.AndroidKeystoreIdentity
import com.edenpractical.comms.security.PairingInviteCodec
import com.edenpractical.comms.transport.LanTlsProbe
import com.edenpractical.comms.transport.WifiLanEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Grounded field-utility palette: neutral graphite with restrained olive accents.
private val Graphite = Color(0xFF0D1112)
private val GraphiteSurface = Color(0xFF161B1D)
private val GraphiteRaised = Color(0xFF1F2628)
private val SoftIvory = Color(0xFFE6E8E6)
private val PaleSage = Color(0xFF8FAA6A)
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
    // Save a real screen history so system Back, gestures, and the in-app
    // Back arrow all unwind the same navigation route after rotation.
    var history by rememberSaveable { mutableStateOf(listOf("home")) }
    val page = history.last()
    val navigate: (String) -> Unit = { destination ->
        history = NavigationHistory.push(history, destination)
    }
    val back: () -> Unit = { history = NavigationHistory.pop(history) }
    BackHandler(enabled = history.size > 1) { back() }

    // Keep Android system-bar icon contrast AND bar backgrounds synchronized
    // with the selected app theme. On newer edge-to-edge systems the OS may
    // control the bar pixels; disabling its automatic nav scrim also prevents
    // the muddy gray 3-button navigation strip seen in light-mode testing.
    SideEffect {
        (context as? Activity)?.let { activity ->
            WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                isAppearanceLightStatusBars = !darkMode
                isAppearanceLightNavigationBars = !darkMode
            }
            val window = activity.window
            window.statusBarColor = if (darkMode) android.graphics.Color.rgb(13, 17, 18)
                else android.graphics.Color.rgb(245, 246, 242)
            window.navigationBarColor = if (darkMode) android.graphics.Color.rgb(13, 17, 18)
                else android.graphics.Color.rgb(245, 246, 242)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
        }
    }

    val colors = if (darkMode) {
        darkColorScheme(
            primary = PaleSage,
            onPrimary = Graphite,
            background = Graphite,
            onBackground = SoftIvory,
            surface = GraphiteSurface,
            onSurface = SoftIvory,
            surfaceVariant = GraphiteRaised,
            onSurfaceVariant = Color(0xFFAFB7B1),
            outline = Color(0xFF667269)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF536C4C),
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
                FieldHeaderRule()
                // The core pages are intentionally short enough not to scroll
                // on typical phones. Keep scrolling as an accessibility and
                // landscape fallback, with responsive density for small heights.
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    val compact = maxHeight < 650.dp
                    key(page) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = if (compact) 10.dp else 16.dp),
                            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 13.dp)
                        ) {
                        when (page) {
                            "home" -> HomeScreen(
                                onPtt = { navigate("ptt") },
                                onConnect = { navigate("connect") },
                                onSettings = { navigate("settings") },
                                onHelp = { navigate("help") }
                            )
                            "connect" -> NearbyScreen(onLocalWifi = { navigate("wifi") })
                            "wifi" -> LocalWifiScreen()
                            "ptt" -> PushToTalkScreen()
                            "settings" -> SettingsScreen(
                                darkMode = darkMode,
                                onDarkModeChange = {
                                    darkMode = it
                                    preferences.edit().putBoolean("dark_theme", it).apply()
                                },
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
                                onPtt = { navigate("ptt") },
                                onConnect = { navigate("connect") },
                                onSettings = { navigate("settings") },
                                onHelp = { navigate("help") }
                            )
                        }
                            Spacer(Modifier.height(if (compact) 2.dp else 6.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun titleForPage(page: String): String = when (page) {
    "connect" -> "Connect nearby"
    "wifi" -> "Local Wi-Fi"
    "ptt" -> "Voice / PTT"
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
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (page == "home") {
            Image(
                painter = painterResource(R.drawable.ic_eden_comms),
                contentDescription = null,
                modifier = Modifier.size(42.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "EDEN COMMS",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.7.sp,
                    maxLines = 1
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
                titleForPage(page).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HomeScreen(
    onConnect: () -> Unit,
    onPtt: () -> Unit,
    onSettings: () -> Unit,
    onHelp: () -> Unit
) {
    FieldSectionHeader("NEARBY", "Connect and communicate")
    Text(
        "Designed for conversations without permanent chat history.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    ConnectionStatus("Not connected", "No active conversation")

    Button(
        onClick = onConnect,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = CutCornerShape(7.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_eden_radio_tower),
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text("CONNECT NEARBY", fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp)
    }

    FieldSectionHeader("COMMUNICATION", null)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CompactModeTile(
            modifier = Modifier.weight(1f),
            glyph = R.drawable.ic_eden_chat,
            title = "Text",
            detail = "Coming soon",
            onClick = onConnect
        )
        CompactModeTile(
            modifier = Modifier.weight(1f),
            glyph = R.drawable.ic_eden_ptt,
            title = "Voice / PTT",
            detail = "Preview",
            onClick = onPtt
        )
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        NavigationRow("Settings", "Privacy and appearance", onSettings, R.drawable.ic_eden_settings)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
        NavigationRow("How it works", "A quick guide to connecting", onHelp, R.drawable.ic_eden_info)
    }
    Text(
        "DEVELOPMENT PREVIEW  /  MESSAGING COMING SOON",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun NearbyScreen(onLocalWifi: () -> Unit) {
    FieldSectionHeader("CONNECT", "Choose a connection")
    // The only upcoming transport is the primary action. We don't force
    // people to learn LAN, Bluetooth or Wi-Fi Direct terminology to start.
    FieldOption(
        glyph = R.drawable.ic_eden_wifi,
        title = "Local Wi-Fi",
        detail = "Same network • internet not needed",
        state = "PREVIEW",
        onClick = onLocalWifi
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    SectionLabel("LATER")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_eden_bluetooth),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Text(
            "Bluetooth and Wi-Fi Direct are planned.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Text(
        "When pairing is ready, follow the steps on the Local Wi-Fi screen.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun LocalWifiScreen() {
    FieldSectionHeader("LOCAL WI-FI", "Connect two phones")
    FieldFrame(accent = true) {
        Image(
            painter = painterResource(R.drawable.ic_eden_pairing_phones),
            contentDescription = "Two phones with Wi-Fi between them",
            modifier = Modifier.fillMaxWidth().height(86.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) { StatusLabel("PAIRING COMING SOON") }
        Text(
            "Both phones must be on the same Wi-Fi. Internet isn't needed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {},
                enabled = false,
                modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                shape = CutCornerShape(5.dp)
            ) { Text("HOST") }
            OutlinedButton(
                onClick = {},
                enabled = false,
                modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                shape = CutCornerShape(5.dp)
            ) { Text("JOIN") }
        }
    }
    Text(
        "Coming next: show or scan a QR code, confirm the other phone, then connect.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PushToTalkScreen() {
    FieldSectionHeader("VOICE / PTT", "Push-to-talk")
    ConnectionStatus("Not connected", "No active voice session")
    FieldFrame(accent = true) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "VOICE CHANNEL",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            StatusLabel("COMING SOON")
        }
        OutlinedButton(
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth().heightIn(min = 170.dp),
            shape = CutCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_eden_ptt),
                    contentDescription = null,
                    modifier = Modifier.size(38.dp)
                )
                Text(
                    "HOLD TO TRANSMIT",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp
                )
            }
        }
        Text(
            "Press to transmit • release to stop (once available).",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Text(
        "Voice is disabled until a verified connection is established.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun SettingsScreen(
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    onDeveloper: () -> Unit
) {
    SectionLabel("APPEARANCE")
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
                "Use the darker color theme",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = darkMode, onCheckedChange = onDarkModeChange)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.30f))
    SectionLabel("PRIVACY")
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("Message history", fontWeight = FontWeight.SemiBold)
        Text(
            "Session-only by default (once messaging is available)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Text(
        "Optional encrypted history for up to 30 days is planned.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.30f))
    SectionLabel("ADVANCED")
    NavigationRow("Device and developer tools", "Identity and connection testing", onDeveloper)
    Text(
        "No account or messaging server needed for local conversations.",
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
            "Connect. Talk. Done.",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Eden Comms is being built for simple conversations between nearby phones.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    SectionLabel("THE BASICS")
    StepLine("01", "Connect nearby", "Use the same Wi-Fi network")
    StepLine("02", "Confirm the other phone", "Approve the connection on both devices")
    StepLine("03", "Communicate", "Text or voice after the connection is verified")
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.30f))
    Text(
        "What privacy means here",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Text(
        "Session-only chat is the planned default: no permanent message history on your phone. " +
            "The other person can still save or photograph what they see, and a compromised phone may expose content.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        "Development preview: connections, messaging and voice aren't ready for normal use.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun FieldSectionHeader(eyebrow: String, title: String?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Spacer(
            modifier = Modifier.width(3.dp)
                .height(if (title == null) 19.dp else 30.dp)
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                eyebrow,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            )
            if (title != null) Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CompactModeTile(
    modifier: Modifier,
    glyph: Int,
    title: String,
    detail: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .heightIn(min = 88.dp)
            .clickable(role = Role.Button, onClick = onClick),
        shape = CutCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.44f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                painter = painterResource(glyph),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(23.dp)
            )
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2
            )
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FieldOption(
    glyph: Int,
    title: String,
    detail: String,
    state: String,
    onClick: (() -> Unit)? = null
) {
    val clickMod = if (onClick == null) Modifier
        else Modifier.clickable(role = Role.Button, onClick = onClick)
    Surface(
        modifier = Modifier.fillMaxWidth().then(clickMod),
        shape = CutCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(glyph),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    detail,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                state,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (onClick != null) {
                Spacer(Modifier.width(7.dp))
                Text("›", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun RowScope.FieldStep(number: String, title: String) {
    Surface(
        modifier = Modifier.weight(1f),
        color = MaterialTheme.colorScheme.surface,
        shape = CutCornerShape(5.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                number, color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        }
    }
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
        shape = CutCornerShape(6.dp),
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
private fun NavigationRow(
    title: String,
    description: String,
    onClick: () -> Unit,
    glyph: Int? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (glyph != null) {
            Icon(
                painter = painterResource(glyph),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            "›",
            modifier = Modifier.widthIn(min = 20.dp),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
        shape = CutCornerShape(7.dp),
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
