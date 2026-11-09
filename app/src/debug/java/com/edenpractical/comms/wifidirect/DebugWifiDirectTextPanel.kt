package com.edenpractical.comms.wifidirect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

internal data class DebugWifiDirectChatLine(
    val outgoing: Boolean,
    val id: Long,
    val text: String,
    val acknowledged: Boolean = false
)

/** Actual authenticated test TCP/TLS callbacks only, no simulated delivery. */
@Composable
internal fun DebugWifiDirectTextPanel(
    lines: List<DebugWifiDirectChatLine>,
    draft: String,
    onDraft: (String) -> Unit,
    onSend: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("WI-FI DIRECT ENCRYPTED TEXT • DEBUG",
            style = MaterialTheme.typography.titleMedium)
        Text(
            "No router or Internet is needed after the Wi-Fi Direct group forms. " +
                "Only app ACKs are shown, not human read receipts. " +
                "This experimental protocol is NOT reviewed for private or emergency use.",
            style = MaterialTheme.typography.bodySmall
        )
        if (lines.isEmpty()) Text("Connected. Send a short harmless test message.")
        lines.forEach { line ->
            Text((if (line.outgoing) "You • " else "Other phone • ") + line.text,
                style = MaterialTheme.typography.bodyMedium)
            Text(if (!line.outgoing) "Received here"
                 else if (line.acknowledged) "Other app returned ACK"
                 else "Written to encrypted P2P link",
                style = MaterialTheme.typography.labelSmall)
        }
        OutlinedTextField(
            value = draft, onValueChange = { onDraft(it.take(280)) },
            label = { Text("Wi-Fi Direct test message") }, maxLines = 3,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() })
        )
        Button(onClick = onSend, enabled = draft.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text("SEND ENCRYPTED P2P TEXT")
        }
    }
}
