package com.edenpractical.comms.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.edenpractical.comms.ui.FieldFrame

/**
 * Pure presentation of REAL debug socket callbacks, never fabricated receipt
 * states or transport/pairing authority. Caller owns ephemeral draft/state.
 *
 * The conversation list has a fixed scroll viewport: the main route already
 * scrolls vertically on narrow phones, so an unbounded nested LazyColumn
 * would fail layout constraints and bury the composer under long histories.
 */
@Composable
internal fun DebugConversationPanel(
    transcript: DebugTextTranscript,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit
) {
    val scroll = rememberScrollState()
    val lastEntry = transcript.visible.lastOrNull()
    LaunchedEffect(transcript.writtenHere, transcript.receivedHere) {
        scroll.animateScrollTo(scroll.maxValue)
    }
    FieldFrame(accent = true) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Conversation",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Current nearby session • temporary text",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = CutCornerShape(5.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)
            ) {
                Text(
                    "DEV TEST",
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        Text(
            "Only test messages. This encrypted test connection has not had an " +
                "independent security review. Do not use private information.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(CutCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f))
                .verticalScroll(scroll)
                .padding(horizontal = 10.dp, vertical = 12.dp)
        ) {
            if (transcript.visible.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 55.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Start the conversation",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "Write a short test message below. Anything received " +
                            "from the other phone will appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    transcript.visible.forEach { entry ->
                        val writtenHere =
                            entry.direction == DebugTextTranscript.Direction.WRITTEN_TO_TLS
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (writtenHere) Arrangement.End
                                else Arrangement.Start
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(0.88f),
                                shape = CutCornerShape(8.dp),
                                color = if (writtenHere)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                                else MaterialTheme.colorScheme.surface
                            ) {
                                Column(
                                    modifier = Modifier.padding(
                                        horizontal = 12.dp, vertical = 9.dp
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        if (writtenHere) "You • Written to TLS"
                                        else "Other phone • Received here",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        entry.text,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (writtenHere) {
                                        Text(
                                            "Not a delivery or read receipt",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Text(
            "This phone: ${transcript.writtenHere} TLS writes • " +
                "${transcript.receivedHere} received",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        OutlinedTextField(
            value = draft,
            onValueChange = { value ->
                if (DebugTextDraftPolicy.accepts(value)) onDraftChange(value)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Message this phone's test partner") },
            placeholder = { Text("Write a short test message…") },
            minLines = 2,
            maxLines = 4,
            supportingText = {
                Text("${DebugTextDraftPolicy.codePoints(draft)}/280 characters • Test only")
            },
            shape = CutCornerShape(6.dp)
        )
        Button(
            onClick = onSend,
            enabled = DebugTextDraftPolicy.maySend(draft),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = CutCornerShape(6.dp)
        ) {
            Text("SEND TEST MESSAGE", fontWeight = FontWeight.Bold)
        }
        Text(
            "Session-only: leaving this screen or disconnecting clears the app's " +
                "visible messages. Your keyboard or screenshots may still retain text.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
