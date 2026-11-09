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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
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

    // Android microphone permission can be revoked in Settings while the app
    // is away. A stale remembered true must not leave an enabled Talk control
    // after the app resumes. This only re-reads permission; it never requests it.
    DisposableEffect(context) {
        val lifecycle = (context as? LifecycleOwner)?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                micAllowed = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    if (!owner.isActive()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text(
            "LIVE HOLD-TO-TALK • DEBUG",
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            "Hold to talk for up to 3 seconds. Audio sends while you hold; " +
                "release to stop. Only one phone can talk at a time. If the " +
                "other person is speaking, wait and try again. Voice isn't " +
                "intentionally saved. This is an experimental test, not " +
                "for private or emergency conversations.",
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
                "Screen reader or keyboard: activate once to request a turn " +
                    "and begin speaking; activate again to stop or cancel. " +
                    "Disconnect immediately stops microphone and playback.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(
            owner.notice,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodySmall
        )
    }
}
