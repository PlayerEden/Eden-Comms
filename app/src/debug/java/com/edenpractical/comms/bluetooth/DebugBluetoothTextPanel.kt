package com.edenpractical.comms.bluetooth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.edenpractical.comms.pairing.DebugTextDraftPolicy
import com.edenpractical.comms.ui.DebugNearbyTextUiCopy
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** In-memory view of actual verified encrypted TEXT_WRITTEN, RECEIVED and peer ACK events. */
internal data class DebugBluetoothChatLine(
    val outgoing: Boolean,
    val id: Long,
    val text: String,
    val acknowledged: Boolean = false
)

/**
 * Text presentation only. Parent renders this AFTER real QR, pinned TLS and
 * both human confirmations. No local button grants trust or authorizes I/O.
 * The timeline is scroll-bounded and never persists its own transcript.
 */
@Composable
internal fun DebugBluetoothTextPanel(
    lines: List<DebugBluetoothChatLine>,
    draft: String,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    sendFeedback: String = ""
) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var followNewest by remember { mutableStateOf(true) }
    var autoScrolling by remember { mutableStateOf(false) }

    LaunchedEffect(scroll) {
        snapshotFlow { Triple(scroll.value, scroll.maxValue, scroll.isScrollInProgress) }
            .collect { (position, maximum, moving) ->
                if (moving && !autoScrolling) followNewest = maximum - position <= 64
            }
    }
    // A new observation may append after the 24-message rolling cap, so
    // watch the final ID/direction rather than just the number of lines.
    // Never steal a user's reading position while they have scrolled back.
    LaunchedEffect(lines.lastOrNull()?.id, lines.lastOrNull()?.outgoing) {
        if (lines.isNotEmpty() && followNewest) {
            withFrameNanos { }
            autoScrolling = true
            try {
                scroll.animateScrollTo(scroll.maxValue)
            } finally {
                autoScrolling = false
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(
            "BLUETOOTH TEXT • DEBUG",
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            "This verified Bluetooth test link carries short text. Other app returned ACK does not mean a person read it. Not for private or emergency use.",
            style = MaterialTheme.typography.bodySmall
        )
        if (lines.isEmpty()) {
            Text("Ready for a short test message.")
        } else {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .heightIn(max = 236.dp)
                    .verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                lines.forEach { line ->
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .semantics(mergeDescendants = true) { },
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            (if (line.outgoing) "You • " else "Other phone • ") + line.text,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            DebugNearbyTextUiCopy.lineStatus(line.outgoing, line.acknowledged),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            if (!followNewest) {
                OutlinedButton(
                    onClick = {
                        followNewest = true
                        scope.launch { scroll.animateScrollTo(scroll.maxValue) }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text("JUMP TO LATEST") }
            }
        }

        OutlinedTextField(
            value = draft,
            onValueChange = { candidate ->
                // Code points, not UTF-16 units: emoji count as one.
                // Reject invalid paste instead of silently truncating text.
                if (DebugNearbyTextUiCopy.acceptsDraft(candidate)) onDraft(candidate)
            },
            label = { Text("Bluetooth test message") },
            placeholder = { Text("Write a harmless test message…") },
            supportingText = {
                Text("${DebugTextDraftPolicy.codePoints(draft)}/${DebugNearbyTextUiCopy.MAX_CODE_POINTS} code points • test only")
            },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = {
                if (DebugNearbyTextUiCopy.canSubmit(draft)) onSend()
            })
        )
        Button(
            onClick = onSend,
            enabled = DebugNearbyTextUiCopy.canSubmit(draft),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) { Text("SEND TEST MESSAGE") }
        if (sendFeedback.isNotEmpty()) {
            Text(
                sendFeedback,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.labelSmall
            )
        }
        Text(
            "Queued is not written. Written is not delivered. A peer-app ACK is not a read receipt.",
            style = MaterialTheme.typography.labelSmall
        )
    }
}
