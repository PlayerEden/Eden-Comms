package com.edenpractical.comms.audio

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.edenpractical.comms.pairing.DebugHoldToTalkControl

/**
 * Reused inside the actual authenticated Bluetooth and Wi-Fi Direct chat.
 * Permission is an explicit click, not triggered from scanning/QR or app start.
 * The OS permission flow may background and STOP the transport; fresh pairing
 * may be required afterward. No microphone UI appears before confirmation.
 */
@Composable
internal fun DebugNearbyVoicePanel(owner: DebugNearbyVoiceOwner) {
    val context = LocalContext.current
    var micAllowed by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> micAllowed = granted }

    if (!owner.isActive()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text("LIVE HOLD-TO-TALK • DEBUG", style = MaterialTheme.typography.titleMedium)
        Text(
            "Hold to request the talk channel, then send live 20ms audio " +
                "for up to 3 seconds while held. Only one phone has the floor " +
                "at a time; the joiner waits for the host's encrypted GRANT. " +
                "Releasing sends END and releases the floor. Test-quality audio: " +
                "no voice is intentionally saved. Not for private or emergency use.",
            style = MaterialTheme.typography.bodySmall
        )
        if (!micAllowed) {
            Button(
                onClick = { permission.launch(Manifest.permission.RECORD_AUDIO) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text("ALLOW MICROPHONE FOR THIS TEST") }
            Text("The Android permission prompt may interrupt this test link.",
                style = MaterialTheme.typography.bodySmall)
        } else {
            DebugHoldToTalkControl(
                enabled = micAllowed && owner.isActive() && !owner.finalizing,
                recording = owner.capturing,
                onPressStart = { owner.beginCapture() },
                onRelease = { owner.release() },
                onCancel = { owner.cancelCapture() },
                onAccessibilityClick = { owner.accessibilityTap() }
            )
            Text(
                "Screen reader: activate once to record, again to send. " +
                    "Disconnect immediately stops microphone and playback.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(owner.notice, style = MaterialTheme.typography.bodySmall)
    }
}
