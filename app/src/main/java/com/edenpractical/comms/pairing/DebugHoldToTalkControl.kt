package com.edenpractical.comms.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Debug-only foreground touch surface. Press starts *local* microphone
 * capture; 20ms frames are sent over the existing approved LAN TLS
 * session WHILE pressed. Release ends the bounded live turn.
 *
 * This never bypasses QR/consent, starts a separate radio link, requests
 * microphone permissions or claims reviewed/production radio quality.
 *
 * The press handler is not restarted when [recording] changes: cancelling
 * a pointer during navigation/capture teardown stops future samples.
 * Already transmitted live audio cannot be retracted.
 * Screen-reader activation uses the existing two-tap accessible fallback.
 */
@Composable
internal fun DebugHoldToTalkControl(
    enabled: Boolean,
    recording: Boolean,
    onPressStart: () -> Boolean,
    onRelease: () -> Unit,
    onCancel: () -> Unit,
    onAccessibilityClick: () -> Unit
) {
    val currentStart by rememberUpdatedState(onPressStart)
    val currentRelease by rememberUpdatedState(onRelease)
    val currentCancel by rememberUpdatedState(onCancel)
    val currentAccessibility by rememberUpdatedState(onAccessibilityClick)
    val colors = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .clip(CutCornerShape(8.dp))
            .background(if (recording) colors.tertiaryContainer else colors.primaryContainer)
            .pointerInput(enabled) {
                detectTapGestures(
                    onPress = {
                        if (enabled && currentStart()) {
                            var finished = false
                            try {
                                val released = tryAwaitRelease()
                                finished = true
                                if (released) currentRelease() else currentCancel()
                            } finally {
                                // Cancellation stops future samples; earlier
                                // live frames may already have reached the peer.
                                if (!finished) currentCancel()
                            }
                        }
                    }
                )
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = if (recording)
                    "Transmitting short live test audio. Release to stop."
                else "Hold to transmit live test voice, release to stop."
                if (!enabled) disabled()
                else onClick {
                    // TalkBack/keyboard users need not perform a long press.
                    currentAccessibility()
                    true
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (recording) "RELEASE TO STOP" else "HOLD TO TALK",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            color = if (recording) colors.onTertiaryContainer else colors.onPrimaryContainer,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
