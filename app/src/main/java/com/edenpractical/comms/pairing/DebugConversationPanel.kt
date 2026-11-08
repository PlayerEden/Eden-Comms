package com.edenpractical.comms.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.edenpractical.comms.ui.FieldFrame
import com.edenpractical.comms.ui.FieldHelp
import com.edenpractical.comms.privacy.ConversationPrivacyContent
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Presentation of ACTUAL debug session writer/receiver callbacks only.
 * The caller owns the ephemeral transcript/draft, actual bilateral consent,
 * send policy, socket and disconnect. No saved chat, networking or receipts.
 *
 * A bounded 220dp viewport avoids unbounded nested scroll in the route.
 * User scrolling away from the bottom suspends auto-follow. A manual
 * Jump to latest control is available without inventing an unread receipt.
 */
@Composable
internal fun DebugConversationPanel(
    transcript: DebugTextTranscript,
    peerAckIds: Set<Long>,
    draft: String,
    hidden: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onToggleHidden: () -> Unit,
    onClearRequested: () -> Unit
) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var followNewest by remember { mutableStateOf(true) }
    var autoScrolling by remember { mutableStateOf(false) }
    LaunchedEffect(scroll) {
        snapshotFlow { Triple(scroll.value, scroll.maxValue, scroll.isScrollInProgress) }
            .collect { (position, maximum, moving) ->
                // Only the user's actual scroll affects follow mode; programmatic
                // scrolls and new item layout cannot steal reading position.
                if (moving && !autoScrolling) {
                    followNewest = maximum - position <= 64
                }
            }
    }
    LaunchedEffect(transcript.writtenHere, transcript.receivedHere, hidden) {
        if (!hidden && followNewest) {
            withFrameNanos { }
            autoScrolling = true
            try {
                scroll.animateScrollTo(scroll.maxValue)
            } finally {
                autoScrolling = false
            }
        }
    }
    FieldFrame(accent = true) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Nearby test chat",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Active on this phone • temporary messages",
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
            "Unreviewed test only. Do not send sensitive information. " +
                "Voice is disabled.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val stacked = PairingTestUiCopy.useStackedActions(
                maxWidth.value, LocalDensity.current.fontScale
            )
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ConversationPrivacyButton(hidden, onToggleHidden,
                        Modifier.fillMaxWidth())
                    ConversationClearButton(
                        transcript.visible.isNotEmpty() || draft.isNotEmpty(),
                        onClearRequested, Modifier.fillMaxWidth()
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ConversationPrivacyButton(hidden, onToggleHidden,
                        Modifier.weight(1f))
                    ConversationClearButton(
                        transcript.visible.isNotEmpty() || draft.isNotEmpty(),
                        onClearRequested, Modifier.weight(1f)
                    )
                }
            }
        }
        if (hidden) {
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                shape = CutCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Conversation hidden on this screen",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        ConversationPrivacyContent.curtainExplanation,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Tap Show conversation to reveal messages and write again.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        } else {
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
                            "Ready for a test message",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "Write below. Messages received on this phone appear here.",
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
                                    modifier = Modifier.fillMaxWidth(0.88f)
                                        .semantics(mergeDescendants = true) { },
                                    shape = CutCornerShape(8.dp),
                                    color = if (writtenHere)
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                                    else MaterialTheme.colorScheme.surface
                                ) {
                                    Column(
                                        modifier = Modifier.padding(
                                            horizontal = 12.dp, vertical = 10.dp
                                        ),
                                        verticalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Text(
                                            DebugConversationUiCopy.participant(entry.direction),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            entry.text,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            DebugConversationUiCopy.observation(entry.direction),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (writtenHere && entry.id in peerAckIds) {
                                            Text(
                                                DebugConversationUiCopy.peerAppAckLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (!followNewest && transcript.visible.isNotEmpty()) {
                TextButton(
                    onClick = {
                        followNewest = true
                        scope.launch {
                            autoScrolling = true
                            try { scroll.animateScrollTo(scroll.maxValue) }
                            finally { autoScrolling = false }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text("Jump to latest") }
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { value ->
                    if (DebugTextDraftPolicy.accepts(value)) onDraftChange(value)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Test message") },
                placeholder = { Text("Write a short test message…") },
                minLines = 2,
                maxLines = 4,
                supportingText = {
                    Text("${DebugTextDraftPolicy.codePoints(draft)}/280 characters • Test only")
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (!hidden && DebugTextDraftPolicy.maySend(draft)) onSend()
                }),
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
                DebugConversationUiCopy.counts(transcript.writtenHere, transcript.receivedHere),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
            Text(
                "Not a delivery or read receipt. A write here does not prove the " +
                    "other phone received or read it. A peer-app test ACK only " +
                    "means a returned control was observed, not that anyone read it.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FieldHelp(
                question = "What does written to test link mean?",
                answer = "The test link reported that this phone wrote the " +
                    "message to its local encrypted connection. This is NOT " +
                    "proof that the other phone received, displayed or read it. " +
                    "Only a Received on this phone entry confirms local reception."
            )
        }
        Text(
            "This development chat ends after 30 minutes from test-link readiness. " +
                "The link also closes if the peer stops responding.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Session-only: leaving this screen or disconnecting clears the app's " +
                "visible messages. Your keyboard or screenshots may still retain text.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ConversationPrivacyButton(
    hidden: Boolean,
    onToggleHidden: () -> Unit,
    modifier: Modifier
) {
    OutlinedButton(
        onClick = onToggleHidden,
        modifier = modifier.heightIn(min = 48.dp),
        shape = CutCornerShape(6.dp)
    ) {
        Text(if (hidden) "Show conversation" else "Hide conversation")
    }
}

@Composable
private fun ConversationClearButton(
    enabled: Boolean,
    onClearRequested: () -> Unit,
    modifier: Modifier
) {
    OutlinedButton(
        onClick = onClearRequested,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = CutCornerShape(6.dp)
    ) {
        Text("Clear my screen")
    }
}
