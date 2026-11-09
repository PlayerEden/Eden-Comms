package com.edenpractical.comms.pairing

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.edenpractical.comms.ui.FieldFrame
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/**
 * F5-B: optional peer-memory metadata for ALREADY verified people.
 * A remembered public fingerprint does NOT automatically accept a peer.
 * Not a contacts list, room history, message store, or auto-reconnect UI.
 */
@Composable
internal fun DebugRememberPeerPanel(
    rememberedUntilMs: Long?,
    note: String,
    onRemember: (Int) -> Unit,
    onForget: () -> Unit,
    onForgetAll: () -> Unit
) {
    var showRemember by remember { mutableStateOf(false) }
    var showForget by remember { mutableStateOf(false) }
    var showForgetAll by remember { mutableStateOf(false) }
    var requestedDays by remember { mutableFloatStateOf(7f) }

    FieldFrame(accent = false) {
        Text(
            "REMEMBER THIS PHONE • OPTIONAL",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Only this phone may remember the verified public identity. " +
                "No name, phone number, address, messages or private keys are saved. " +
                "A new chat ALWAYS needs fresh QR verification, TLS and both approvals.",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            if (rememberedUntilMs == null)
                "Just this session • no remembered peer saved"
            else "Public identity remembered locally until " +
                DateFormat.getDateTimeInstance(
                    DateFormat.SHORT, DateFormat.SHORT
                ).format(Date(rememberedUntilMs)),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(note, style = MaterialTheme.typography.labelSmall)
        Button(
            onClick = { showRemember = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = CutCornerShape(6.dp)
        ) { Text(if (rememberedUntilMs == null) "REMEMBER VERIFIED PHONE"
            else "SHORTEN REMEMBER PERIOD") }
        if (rememberedUntilMs != null) {
            OutlinedButton(
                onClick = { showForget = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text("FORGET THIS PHONE") }
        }
        TextButton(
            onClick = { showForgetAll = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) { Text("Forget all remembered phones on this device") }
        Text(
            "This option does not save chat messages or change the other phone's " +
                "choices. There is no automatic reconnection or approval bypass. " +
                "Android storage deletion is best effort, not secure erasure.",
            style = MaterialTheme.typography.labelSmall
        )
    }

    if (showRemember) {
        AlertDialog(
            onDismissRequest = { showRemember = false },
            title = { Text("Remember verified phone?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Save only this phone's already verified public identity. " +
                            "This cannot skip QR scans, certificate checks, or human " +
                            "approval on a future connection."
                    )
                    Text("Remember for ${requestedDays.roundToInt()} days (maximum 30)")
                    Slider(
                        value = requestedDays,
                        onValueChange = { requestedDays = it.roundToInt().toFloat() },
                        valueRange = 1f..30f,
                        steps = 28
                    )
                    Text(
                        "Saving again cannot extend an existing unexpired " +
                            "record. Choose fewer days to shorten it.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showRemember = false
                    onRemember(requestedDays.roundToInt())
                }) { Text("Remember on this phone") }
            },
            dismissButton = {
                TextButton(onClick = { showRemember = false }) { Text("Cancel") }
            }
        )
    }
    if (showForget) {
        AlertDialog(
            onDismissRequest = { showForget = false },
            title = { Text("Forget this phone?") },
            text = { Text(
                "Delete its public-identity record from this device. " +
                    "Saved message history is independent and is not deleted."
            ) },
            confirmButton = {
                TextButton(onClick = {
                    showForget = false
                    onForget()
                }) { Text("Forget verified phone") }
            },
            dismissButton = {
                TextButton(onClick = { showForget = false }) { Text("Cancel") }
            }
        )
    }
    if (showForgetAll) {
        AlertDialog(
            onDismissRequest = { showForgetAll = false },
            title = { Text("Forget all remembered phones?") },
            text = { Text(
                "Remove all saved public peer identities from this phone. " +
                    "This will not delete saved text history, remote copies, " +
                    "or this phone's own Keystore identity."
            ) },
            confirmButton = {
                TextButton(onClick = {
                    showForgetAll = false
                    onForgetAll()
                }) { Text("Forget all on this phone") }
            },
            dismissButton = {
                TextButton(onClick = { showForgetAll = false }) { Text("Cancel") }
            }
        )
    }
}
