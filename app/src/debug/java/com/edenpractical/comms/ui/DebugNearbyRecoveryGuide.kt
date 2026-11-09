package com.edenpractical.comms.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * M6-G: read-only instructions from ACTUAL stopped/cleaning debug states.
 * The calling Activity still owns QR, radio, consent, cleanup and reconnect.
 * No string matching of transport errors, no self-approval or auto-retry.
 */
internal object DebugNearbyRecoveryGuide {
    enum class Situation {
        BLUETOOTH_ENDED, BLUETOOTH_PERMISSION,
        P2P_HOST_LINK_ENDED, P2P_HOST_CLEANING, P2P_HOST_ENDED,
        P2P_HOST_CLEANUP_UNCONFIRMED,
        P2P_JOIN_LINK_ENDED, P2P_JOIN_SEARCH_ENDED,
        P2P_JOIN_RADIO_OFF
    }

    data class Step(val heading: String, val action: String, val caution: String)

    fun forSituation(situation: Situation): Step = when (situation) {
        Situation.BLUETOOTH_ENDED -> Step(
            "BLUETOOTH TEST ENDED",
            "On both phones, start a new in-person two-code QR exchange. Host on one; scan and select its Bluetooth signal on the other. Both people must approve again.",
            "No connection is active. Old pairing codes and remembered peer details do not grant access."
        )
        Situation.BLUETOOTH_PERMISSION -> Step(
            "BLUETOOTH PERMISSION CHANGED",
            "Check Android Nearby devices permission and Bluetooth status, then begin a fresh QR exchange on both phones.",
            "Permission alone does not connect or approve a person. No automatic restart occurs."
        )
        Situation.P2P_HOST_LINK_ENDED -> Step(
            "WI-FI DIRECT CHAT DID NOT CONTINUE",
            "The temporary Wi-Fi Direct group may still exist. Use STOP AND REMOVE TEST GROUP below, check cleanup, then start over with fresh QR codes on both phones.",
            "A group is not a verified chat. Do not send until new TLS checks and both approvals complete."
        )
        Situation.P2P_HOST_CLEANING -> Step(
            "WAITING FOR WI-FI DIRECT CLEANUP",
            "Android is being asked to remove the temporary group. Do not start a new host attempt until cleanup finishes.",
            "Cleanup is not confirmed yet. If it remains unconfirmed, inspect Android Wi-Fi Direct settings."
        )
        Situation.P2P_HOST_CLEANUP_UNCONFIRMED -> Step(
            "GROUP CLEANUP NOT CONFIRMED",
            "Check Android Wi-Fi Direct settings for a remaining group. Return to device preflight before trying again on both phones.",
            "Do not assume the group was removed or reuse old QR codes."
        )
        Situation.P2P_HOST_ENDED -> Step(
            "HOST TEST ENDED",
            "Recheck Wi-Fi, Location Mode and Nearby Wi-Fi permission in the device preflight. Start a fresh host and QR exchange on both phones.",
            "This is not an active connection. Both people must approve a new verified session."
        )
        Situation.P2P_JOIN_LINK_ENDED -> Step(
            "WI-FI DIRECT LINK ENDED",
            "Stop the old link and request group removal if that control is available. Recheck both phones in preflight, then discover and exchange fresh QR codes.",
            "A remembered peer or previous group never bypasses verification and both approvals."
        )
        Situation.P2P_JOIN_SEARCH_ENDED -> Step(
            "WI-FI DIRECT SEARCH ENDED",
            "Check Wi-Fi, Location Mode and Nearby Wi-Fi permission. Start another search only when the other phone is ready to host, then exchange fresh QR codes.",
            "Finding a radio or group is not authentication; no connection is active."
        )
        Situation.P2P_JOIN_RADIO_OFF -> Step(
            "WI-FI DIRECT RADIO UNAVAILABLE",
            "Turn Wi-Fi back on and recheck device preflight permissions. Start new discovery and QR codes when both phones are ready.",
            "No verified conversation is active. Nothing resumes automatically."
        )
    }
}

/** Status text only; no clickable shortcut can bypass QR/TLS/approval. */
@Composable
internal fun DebugNearbyRecoveryPanel(step: DebugNearbyRecoveryGuide.Step) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                step.heading,
                modifier = Modifier.semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                },
                style = MaterialTheme.typography.titleSmall
            )
            Text(step.action, style = MaterialTheme.typography.bodyMedium)
            Text(step.caution, style = MaterialTheme.typography.bodySmall)
        }
    }
}
