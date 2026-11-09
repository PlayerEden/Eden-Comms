package com.edenpractical.comms.qa

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * QA assistance is opt-in, behind the active conversation's Advanced detail.
 * Nothing is copied or persisted unless the user taps the explicit button.
 * This shows redacted local counts only; it cannot assert remote audibility.
 */
@Composable
internal fun DebugNearbyQaPanel(evidence: DebugNearbyQaEvidence) {
    val context = LocalContext.current
    var notice by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("THIS PHONE'S TEST COUNTS", style = MaterialTheme.typography.titleSmall)
        Text(
            "Only locally observed text, ACK, floor and audio-frame counts. " +
                "No message bodies, pairing codes, peer fingerprints, audio " +
                "samples or IP addresses are collected in this report.",
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedButton(
            onClick = {
                try {
                    val clipboard = context.getSystemService(
                        Context.CLIPBOARD_SERVICE
                    ) as? ClipboardManager
                    if (clipboard == null) {
                        notice = "Clipboard unavailable; no report copied."
                    } else {
                        clipboard.setPrimaryClip(ClipData.newPlainText(
                            "Eden Comms local nearby QA", evidence.summary()
                        ))
                        notice = "Copied this phone's local QA counters. " +
                            "Paste them into your test notes."
                    }
                } catch (_: RuntimeException) {
                    notice = "Unable to copy local QA counters."
                }
            },
            enabled = evidence.hasObservation(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) { Text("COPY THIS PHONE'S QA COUNTS") }
        if (notice.isNotEmpty())
            Text(notice, style = MaterialTheme.typography.bodySmall)
        Text(
            "This is not a remote delivery/read receipt, verified audible speech " +
                "or proof of secure messaging. Android and other apps can read " +
                "clipboard content; paste only where you intend.",
            style = MaterialTheme.typography.labelSmall
        )
    }
}
