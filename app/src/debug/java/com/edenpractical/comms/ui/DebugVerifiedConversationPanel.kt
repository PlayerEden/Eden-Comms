package com.edenpractical.comms.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * M6-A: a common CHAT-FIRST presentation for the two actual verified nearby
 * transports. Parent activities may compose this ONLY after their existing
 * post-TLS, post-QR/exporter, both-approved CONFIRM callbacks enter CHAT.
 *
 * This is deliberately not a transport, connection state machine, remembered
 * trust cache, or approval API. Text/Talk selection NEVER grants authority,
 * requests a microphone, starts a search, or reconnects another phone.
 *
 * This panel exists only in DEBUG source and is forgotten at session end.
 */
@Composable
internal fun DebugVerifiedConversationPanel(
    method: String,
    onDisconnect: () -> Unit,
    /** Called when the user exits Talk; must stop a live mic or cancel pending floor. */
    onLeaveTalk: () -> Unit,
    textContent: @Composable () -> Unit,
    talkContent: @Composable () -> Unit,
    privacyContent: @Composable () -> Unit,
    qaContent: @Composable () -> Unit
) {
    var selection by remember { mutableStateOf("text") }
    var showPrivacy by remember { mutableStateOf(false) }
    var showQa by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val softwareKeyboard = LocalSoftwareKeyboardController.current

    // Switching tabs must never leave the microphone transmitting invisibly.
    // Also release if the verified conversation UI is unmounted while on Talk.
    // Owner.release() is idempotent and keeps END/RELEASE in the TLS FIFO.
    DisposableEffect(selection) {
        onDispose {
            if (selection == "talk") onLeaveTalk()
        }
    }
    val chooseText = {
        if (selection == "talk") onLeaveTalk()
        selection = "text"
    }
    val chooseTalk = {
        // TextField is removed from composition when Talk opens. Drop its
        // focus and close the IME before showing microphone controls.
        focusManager.clearFocus(force = true)
        softwareKeyboard?.hide()
        selection = "talk"
    }
    val disconnect = {
        focusManager.clearFocus(force = true)
        softwareKeyboard?.hide()
        onDisconnect()
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        // Reuse the same angular field-card language as Connect and Settings.
        shape = CutCornerShape(7.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.52f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "TEST CONVERSATION • $method",
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Both phones completed the experimental connection checks. " +
                    "Choose Text or Talk below. This is not independently reviewed " +
                    "secure messaging; never use it for secrets or emergencies.",
                style = MaterialTheme.typography.bodySmall
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (selection == "text") {
                    Button(
                        onClick = chooseText,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                            .semantics { stateDescription = "Text mode selected" }
                    ) { Text("TEXT") }
                } else {
                    OutlinedButton(
                        // The same safe path as the selected button. Hiding
                        // Talk must never leave a recording turn invisible.
                        onClick = chooseText,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                            .semantics { stateDescription = "Text mode not selected" }
                    ) { Text("TEXT") }
                }
                if (selection == "talk") {
                    Button(
                        onClick = chooseTalk,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                            .semantics { stateDescription = "Talk mode selected" }
                    ) { Text("TALK") }
                } else {
                    OutlinedButton(
                        onClick = chooseTalk,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                            .semantics { stateDescription = "Talk mode not selected" }
                    ) { Text("TALK") }
                }
            }
            if (selection == "talk") {
                Text(
                    "Changing to Text stops the microphone or cancels a pending talk request.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            // Neither child receives a synthetic "connected" flag: the owner
            // has already authenticated and gated composition of this panel.
            if (selection == "text") textContent()
            else talkContent()

            HorizontalDivider()
            OutlinedButton(
                onClick = disconnect,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) { Text("DISCONNECT THIS TEST SESSION") }

            TextButton(
                onClick = { showPrivacy = !showPrivacy },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .semantics { stateDescription = if (showPrivacy) "Expanded" else "Collapsed" }
            ) {
                Text(if (showPrivacy) "HIDE LOCAL PRIVACY OPTIONS"
                     else "SHOW LOCAL PRIVACY OPTIONS")
            }
            if (showPrivacy) privacyContent()
            TextButton(
                onClick = { showQa = !showQa },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .semantics { stateDescription = if (showQa) "Expanded" else "Collapsed" }
            ) {
                Text(if (showQa) "HIDE ADVANCED TEST COUNTS"
                     else "ADVANCED: THIS PHONE'S TEST COUNTS")
            }
            if (showQa) qaContent()
        }
    }
}
