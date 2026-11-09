package com.edenpractical.comms.bluetooth

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

/** In-memory view of real TEXT_WRITTEN / TEXT_RECEIVED / peer-app ACK events. */
internal data class DebugBluetoothChatLine(
    val outgoing: Boolean,
    val id: Long,
    val text: String,
    val acknowledged: Boolean = false
)

/**
 * Only shown AFTER the actual same-engine Bluetooth TLS QR and two human
 * confirmations. No other path can render a "Ready" state to authorize I/O.
 */
@Composable
internal fun DebugBluetoothTextPanel(
    lines: List<DebugBluetoothChatLine>,
    draft: String,
    onDraft: (String) -> Unit,
    onSend: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("BLUETOOTH ENCRYPTED TEXT • DEBUG",
            style = MaterialTheme.typography.titleMedium)
        Text(
            "Short text uses this verified Bluetooth connection. A peer-app ACK " +
                "does not mean a person read it. Don't use this unreviewed test " +
                "for private or emergency communications.",
            style = MaterialTheme.typography.bodySmall
        )
        if (lines.isEmpty()) Text("Ready for a short test message.")
        lines.forEach { line ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    (if (line.outgoing) "You" else "Other phone") + " • " + line.text,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    if (!line.outgoing) "Received on this phone"
                    else if (line.acknowledged) "Other app returned ACK"
                    else "Written to encrypted test link",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        OutlinedTextField(
            value = draft,
            onValueChange = { onDraft(it.take(280)) },
            label = { Text("Bluetooth test message") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() })
        )
        Button(
            onClick = onSend,
            enabled = draft.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) {
            Text("SEND ENCRYPTED TEST TEXT")
        }
    }
}
