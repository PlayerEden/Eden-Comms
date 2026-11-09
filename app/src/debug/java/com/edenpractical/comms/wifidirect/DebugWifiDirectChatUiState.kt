package com.edenpractical.comms.wifidirect

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.edenpractical.comms.bluetooth.DebugBluetoothApprovedTextChannel

/** Per-Activity RAM-only state. The socket worker never directly owns Compose. */
internal class DebugWifiDirectChatUiState {
    var phase by mutableStateOf(DebugWifiDirectApprovedTcpSession.Phase.IDLE)
        private set
    var note by mutableStateOf("No verified Wi-Fi Direct session.")
        private set
    var approvalPending by mutableStateOf(false)
        private set
    var draft by mutableStateOf("")
    var lines by mutableStateOf(emptyList<DebugWifiDirectChatLine>())
        private set
    private var ackedIds = emptySet<Long>()

    fun status(next: DebugWifiDirectApprovedTcpSession.Phase, message: String) {
        phase = next
        note = message
        if (next == DebugWifiDirectApprovedTcpSession.Phase.STOPPED) {
            approvalPending = false
            draft = ""
            lines = emptyList()
            ackedIds = emptySet()
        }
    }

    fun approval(needed: Boolean) {
        approvalPending = needed
    }

    fun event(e: DebugBluetoothApprovedTextChannel.Event) {
        when (e) {
            DebugBluetoothApprovedTextChannel.Event.Ready -> {
                phase = DebugWifiDirectApprovedTcpSession.Phase.CHAT
                approvalPending = false
                note = "Encrypted P2P test chat active."
            }
            is DebugBluetoothApprovedTextChannel.Event.Written ->
                lines = (lines + DebugWifiDirectChatLine(
                    outgoing = true, id = e.id, text = e.text,
                    acknowledged = e.id in ackedIds
                )).takeLast(24)
            is DebugBluetoothApprovedTextChannel.Event.Received ->
                lines = (lines + DebugWifiDirectChatLine(
                    outgoing = false, id = e.id, text = e.text
                )).takeLast(24)
            is DebugBluetoothApprovedTextChannel.Event.Acknowledged -> {
                ackedIds = (ackedIds + e.id).toList().takeLast(128).toSet()
                lines = lines.map {
                    if (it.outgoing && it.id == e.id)
                        it.copy(acknowledged = true) else it
                }
            }
            DebugBluetoothApprovedTextChannel.Event.Failed -> Unit
        }
    }

    fun clear() {
        status(DebugWifiDirectApprovedTcpSession.Phase.STOPPED,
            "Wi-Fi Direct debug session closed.")
    }
}
