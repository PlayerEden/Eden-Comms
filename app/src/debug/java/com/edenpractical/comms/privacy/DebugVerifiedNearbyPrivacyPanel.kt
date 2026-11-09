package com.edenpractical.comms.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.edenpractical.comms.pairing.DebugRememberPeerPanel

/**
 * The SAME opt-in privacy choices for actually confirmed Bluetooth & P2P
 * chats. There is no radio/connect/approval button or QR metadata here.
 * Both storage choices reset on each new confirmed session.
 */
@Composable
internal fun DebugVerifiedNearbyPrivacyPanel(owner: DebugVerifiedNearbyPrivacy) {
    if (!owner.verified) return
    var showOptions by remember { mutableStateOf(false) }
    var showSaved by remember { mutableStateOf(false) }
    var confirmErase by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text("OPTIONAL LOCAL PRIVACY", style = MaterialTheme.typography.titleMedium)
        Text(
            "History is session-only by default. Saving starts ONLY after you " +
                "choose a duration in this already verified encrypted chat. " +
                "The other phone makes its own choices. No shared cloud.",
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedButton(
            onClick = { showOptions = true },
            enabled = !owner.busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) { Text("SAVE NEW MESSAGES: " + LocalHistoryPolicy.label(owner.retentionMs)) }
        DropdownMenu(
            expanded = showOptions,
            onDismissRequest = { showOptions = false }
        ) {
            LocalHistoryPolicy.choices.forEach { choice ->
                DropdownMenuItem(text = { Text(choice.label) }, onClick = {
                    showOptions = false
                    owner.chooseRetention(choice.durationMs)
                })
            }
        }
        Text(owner.historyNote, style = MaterialTheme.typography.bodySmall)
        OutlinedButton(
            onClick = {
                showSaved = !showSaved
                if (showSaved) owner.viewSaved()
            },
            enabled = !owner.busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) { Text(if (showSaved) "HIDE SAVED LOCAL TEXT" else "VIEW SAVED LOCAL TEXT") }
        if (showSaved) {
            if (owner.rows.isEmpty()) {
                Text("No unexpired saved text for this verified phone.",
                    style = MaterialTheme.typography.bodySmall)
            } else {
                owner.rows.takeLast(16).forEach { msg ->
                    Text(
                        (if (msg.direction == LocalHistoryPolicy.Direction.WRITTEN_HERE)
                            "You • " else "Other phone • ") + msg.text,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Text("This is device-local history only, not proof the other person read it.",
                style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(
            onClick = { confirmErase = true },
            enabled = !owner.busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) { Text("DELETE SAVED HISTORY FOR VERIFIED PHONE") }
        Text(
            "Local deletion is best effort; it cannot erase the other phone, " +
                "Android snapshots or flash remnants. Never store sensitive " +
                "material in unreviewed debug tests.",
            style = MaterialTheme.typography.bodySmall
        )
        DebugRememberPeerPanel(
            rememberedUntilMs = owner.rememberedUntilMs,
            note = owner.peerNote,
            busy = owner.busy,
            onRemember = { owner.remember(it) },
            onForget = { owner.forgetPeer() },
            onForgetAll = { owner.forgetAll() }
        )
    }
    if (confirmErase) {
        AlertDialog(
            onDismissRequest = { confirmErase = false },
            title = { Text("Delete this phone's saved messages?") },
            text = { Text(
                "Delete encrypted messages for this freshly verified peer " +
                    "from this phone only. This will NOT erase remembered " +
                    "peer identity or anything stored on the other phone."
            ) },
            confirmButton = {
                TextButton(onClick = {
                    confirmErase = false
                    showSaved = false
                    owner.eraseSaved()
                }) { Text("Delete this phone's saved text") }
            },
            dismissButton = {
                TextButton(onClick = { confirmErase = false }) { Text("Cancel") }
            }
        )
    }
}
