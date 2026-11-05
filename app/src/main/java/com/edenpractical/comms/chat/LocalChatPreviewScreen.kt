package com.edenpractical.comms.chat

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.edenpractical.comms.ui.FieldFrame
import java.util.Date

/**
 * Real Compose chat layout, deliberately **not** a real communication client.
 * No network service, outgoing packet, peer identity, delivery receipt, audio,
 * database or saved Compose state is accessible from this screen.
 *
 * Sample peer bubbles are ALWAYS labeled as fictional, not received messages.
 */
@Composable
internal fun LocalChatPreviewScreen(onPairingTest: () -> Unit) {
    val context = LocalContext.current
    var session by remember { mutableStateOf(LocalChatPreview.newSession()) }
    var draft by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("Nothing has been sent or received.") }
    var showStatusGuide by remember { mutableStateOf(false) }
    val lifecycleOwner = context as? LifecycleOwner

    // End and erase content when the app leaves the foreground. A navigation
    // change also disposes this screen's non-saveable state.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                session = session.end()
                draft = ""
                notice = "Local preview erased when Eden Comms left the foreground."
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
            session = session.end()
            draft = ""
        }
    }

    Text(
        "TEXT / LAYOUT PREVIEW",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
    FieldFrame(accent = true) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Conversation", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Text("No peer connected", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = CutCornerShape(4.dp)
            ) {
                Text("LOCAL ONLY", modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold)
            }
        }
        Text(
            "This screen is a working UI preview. Text you enter is displayed " +
                "only on this phone; it is NEVER sent to another person.",
            style = MaterialTheme.typography.bodySmall
        )
        TextButton(
            onClick = { showStatusGuide = true },
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text("What will message statuses mean?")
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

        if (session.entries.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("No conversation yet", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Text(
                    "Add a local preview below or show fictional example bubbles to " +
                        "check the conversation layout.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Text("SESSION ACTIVITY / LAST 12", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                session.entries.forEach { entry ->
                    ChatPreviewBubble(entry)
                }
            }
        }

        if (session.canEnterText) {
            OutlinedButton(
                onClick = {
                    session = session.addExampleLayout()
                    notice = "Fictional example bubbles added. No remote phone involved."
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = CutCornerShape(5.dp),
                enabled = session.entries.none {
                    it.origin == LocalChatPreview.Origin.EXAMPLE_NOT_RECEIVED
                }
            ) { Text("Show sample incoming layout") }
        }
    }

    if (session.canEnterText) {
        Text("COMPOSE / LOCAL PREVIEW", style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = draft,
            onValueChange = { newText ->
                if (newText.length <= LocalChatPreview.MAX_CHARACTERS) draft = newText
            },
            label = { Text("Write a local preview") },
            placeholder = { Text("Type something to see how your message looks…") },
            supportingText = {
                Text("${draft.length}/${LocalChatPreview.MAX_CHARACTERS} • Not transmitted")
            },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 4,
            minLines = 2,
            shape = CutCornerShape(6.dp)
        )
        Button(
            onClick = {
                if (draft.isNotBlank()) {
                    val label = DateFormat.getTimeFormat(context).format(Date())
                    session = session.addLocallyTyped(draft, label)
                    draft = ""
                    notice = "Added locally for layout review. Nothing was sent."
                }
            },
            enabled = draft.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = CutCornerShape(6.dp)
        ) {
            Text("ADD TO LOCAL PREVIEW", fontWeight = FontWeight.Bold)
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val stacked = maxWidth < 350.dp || LocalDensity.current.fontScale >= 1.3f
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            session = session.clear()
                            draft = ""
                            notice = "Local preview cleared."
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = CutCornerShape(5.dp)
                    ) { Text("Clear local preview") }
                    OutlinedButton(
                        onClick = {
                            session = session.end()
                            draft = ""
                            notice = "Preview session ended; in-memory bubbles erased."
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = CutCornerShape(5.dp)
                    ) { Text("End preview") }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            session = session.clear()
                            draft = ""
                            notice = "Local preview cleared."
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        shape = CutCornerShape(5.dp)
                    ) { Text("Clear") }
                    OutlinedButton(
                        onClick = {
                            session = session.end()
                            draft = ""
                            notice = "Preview session ended; in-memory bubbles erased."
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        shape = CutCornerShape(5.dp)
                    ) { Text("End preview") }
                }
            }
        }
    } else {
        Text("Preview ended — no content retained by this screen.",
            style = MaterialTheme.typography.bodyMedium)
        Button(
            onClick = {
                session = LocalChatPreview.newSession()
                draft = ""
                notice = "New empty local preview started."
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) { Text("Start new local preview") }
    }

    Text(notice, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(
        "Avoid sensitive text here: your keyboard and Android screen previews may " +
            "retain what you type. Eden Comms does not intentionally save these " +
            "preview bubbles to disk or send them to a peer.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OutlinedButton(
        onClick = {
            session = session.end()
            draft = ""
            onPairingTest()
        },
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = CutCornerShape(5.dp)
    ) {
        Text("Open existing two-phone connection test")
    }

    if (showStatusGuide) {
        AlertDialog(
            onDismissRequest = { showStatusGuide = false },
            title = { Text("Message status meanings") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Only LOCAL PREVIEW is functional today. All other statuses " +
                            "below describe a FUTURE authenticated messaging feature, " +
                            "not activity occurring in this app.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    ConversationStatusGuide.Step.values().forEach { step ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                step.label +
                                    if (step.availableToday) " • AVAILABLE NOW"
                                    else " • FUTURE ONLY",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                step.explanation,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Text(
                        "A local encrypted write will NOT count as delivery. " +
                            "Only a separately authenticated acknowledgment " +
                            "could justify saying the other app received a " +
                            "message; it still could not prove a human read it.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showStatusGuide = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text("Close") }
            }
        )
    }
}

@Composable
private fun ChatPreviewBubble(entry: LocalChatPreview.Entry) {
    val typedHere = entry.origin == LocalChatPreview.Origin.TYPED_HERE
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (typedHere) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.90f),
            shape = CutCornerShape(8.dp),
            color = if (typedHere) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    if (typedHere) "You / THIS PHONE" else "Example peer / FICTIONAL",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(entry.text, style = MaterialTheme.typography.bodyMedium)
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(entry.timeLabel, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(entry.displayState.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
