package com.edenpractical.comms

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.edenpractical.comms.model.ConnectionMethod
import com.edenpractical.comms.model.ReleaseCapabilities

private val Night = Color(0xFF0F1B24)
private val NightPanel = Color(0xFF192D3A)
private val SeaGlass = Color(0xFF92D4CB)
private val OffWhite = Color(0xFFF2F6F5)
private val DayPanel = Color(0xFFFFFFFF)

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
    val prefs = remember(context) {
        context.getSharedPreferences("eden_comms_ui", Context.MODE_PRIVATE)
    }
    var darkMode by remember { mutableStateOf(prefs.getBoolean("dark_theme", true)) }
    var page by rememberSaveable { mutableStateOf("home") }

    val scheme = if (darkMode) {
        darkColorScheme(
            primary = SeaGlass,
            onPrimary = Night,
            background = Night,
            onBackground = OffWhite,
            surface = NightPanel,
            onSurface = OffWhite
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF176A65),
            onPrimary = Color.White,
            background = Color(0xFFF3F7F6),
            onBackground = Night,
            surface = DayPanel,
            onSurface = Night
        )
    }

    MaterialTheme(colorScheme = scheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Header(page) { page = "home" }
                when (page) {
                    "home" -> Home(
                        onConnect = { page = "connect" },
                        onSettings = { page = "settings" },
                        onHelp = { page = "help" }
                    )
                    "connect" -> Connect()
                    "settings" -> Settings(
                        darkMode = darkMode,
                        onDarkModeChange = {
                            darkMode = it
                            prefs.edit().putBoolean("dark_theme", it).apply()
                        }
                    )
                    "help" -> HowItWorks()
                    else -> Home(
                        onConnect = { page = "connect" },
                        onSettings = { page = "settings" },
                        onHelp = { page = "help" }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.14f))
                Text(
                    "EDEN PRACTICAL  /  EC • M1 SECURITY PREP",
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun Header(page: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "EDEN PRACTICAL / COMMUNICATIONS",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                "EDEN COMMS",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineMedium
            )
        }
        if (page != "home") {
            TextButton(onClick = onBack) { Text("Back") }
        }
    }
}

@Composable
private fun Home(
    onConnect: () -> Unit,
    onSettings: () -> Unit,
    onHelp: () -> Unit
) {
    Panel(
        title = "Nearby communication",
        detail = "Connect with another phone. No accounts or remote messaging servers are planned."
    ) {
        StatusLabel("NOT CONNECTED")
        Spacer(Modifier.height(12.dp))
        Text(
            "Foundation preview",
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            "This preview does not connect to other phones. Secure pairing and encrypted messaging are still under development.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(12.dp))
        Button(
            modifier = Modifier.fillMaxWidth().height(52.dp),
            onClick = onConnect
        ) {
            Text("Connection options")
        }
    }
    Panel(
        title = "Made for the essentials",
        detail = "Text and push-to-talk are planned once verified device-to-device connections are ready."
    ) {
        Text("Local Wi-Fi   •   Bluetooth   •   Wi-Fi Direct", style = MaterialTheme.typography.bodyMedium)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            modifier = Modifier.weight(1f).height(52.dp),
            onClick = onSettings,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) { Text("Settings") }
        Button(
            modifier = Modifier.weight(1f).height(52.dp),
            onClick = onHelp,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) { Text("How it works") }
    }
}

@Composable
private fun Connect() {
    Panel(
        title = "Connect nearby",
        detail = "Connection services will arrive in M1–M3. This screen is informational in M0."
    ) {
        StatusLabel("NOT AVAILABLE IN THIS BUILD")
        Spacer(Modifier.height(10.dp))
        Text(
            "We won't offer a fake connect button or claim a secure link before it exists.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
    ConnectionMethod.entries.forEach { method ->
        Panel(title = method.displayName, detail = method.simpleDescription) {
            Text(
                "Planned • not active",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun Settings(darkMode: Boolean, onDarkModeChange: (Boolean) -> Unit) {
    Panel(
        title = "Appearance",
        detail = "Readable controls and high contrast are central to Eden Practical."
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Dark appearance", fontWeight = FontWeight.SemiBold)
                Text("Saved on this phone only", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = darkMode, onCheckedChange = onDarkModeChange)
        }
    }
    Panel(
        title = "Conversation privacy",
        detail = "Not configurable until messaging is implemented."
    ) {
        Text(
            "Planned: session-only by default, optional encrypted local history, and disappearing messages.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
    Panel(
        title = "Device permissions",
        detail = "This build does not request Bluetooth, microphone, location, or network access."
    ) {
        Text(
            "Later builds will request only the permissions needed for features you choose.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun HowItWorks() {
    Panel(
        title = "The simple version",
        detail = "Connect. Communicate. Disconnect."
    ) {
        Text(
            "The future app will help two nearby phones establish a verified encrypted connection. " +
                "The connection could use a local Wi-Fi network, Bluetooth, or Wi-Fi Direct.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
    Panel(
        title = "What privacy can mean",
        detail = "The aim is no accounts, app-operated servers, advertising, or tracking."
    ) {
        Text(
            "Encryption can protect message contents. It cannot prevent the other person " +
                "from recording messages, hide every radio or network trace, or protect an already compromised phone.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
    Panel(
        title = "What verified means",
        detail = "Finding another phone is not the same as trusting it."
    ) {
        Text(
            "1. Discover nearby device. 2. Establish an authenticated encrypted channel. " +
                "3. Both people confirm a matching security code. Until then, messages stay locked.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
    Panel(
        title = "Screen privacy",
        detail = "Screenshots are available in development APKs for visual testing."
    ) {
        Text(
            "Production builds restrict Android screenshots and screen recording. " +
                "Development builds allow them to make testing and feedback easier. " +
                "Never test with real private messages or sensitive data.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
    Panel(
        title = "Current development status",
        detail = "M1 security preparation; no encryption or messaging is enabled."
    ) {
        Text(
            if (ReleaseCapabilities.CAN_SEND_MESSAGES) "Messaging is available."
            else "No communications are available yet. Do not use this preview for sensitive messages.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun StatusLabel(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
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
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                detail,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                style = MaterialTheme.typography.bodyMedium
            )
            content()
        }
    }
}
