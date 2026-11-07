package com.edenpractical.comms.wifidirect

import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager

/**
 * M3r only: read up to eight volatile Android P2P objects per callback.
 * Caller must pass a CURRENT, foreground, permission-checked discovery
 * authority closure; async callbacks recheck it before touching the ledger.
 * No identifying device fields, connection initiation, group, socket or persistence.
 */
internal class DebugWifiDirectCandidateReader {
    companion object { const val MAX_READS = 12 }
    private var outstanding = false
    private var readCount = 0
    private var closed = false

    fun request(
        manager: WifiP2pManager,
        channel: WifiP2pManager.Channel,
        authorized: () -> Boolean,
        onSnapshot: (List<WifiP2pDevice>) -> Unit
    ): Boolean {
        if (closed || outstanding || readCount >= MAX_READS || !authorized()) return false
        outstanding = true
        readCount++
        try {
            manager.requestPeers(channel) { peers ->
                outstanding = false
                if (closed || !authorized()) return@requestPeers
                // No raw names, MAC addresses, identifiers or Wi-Fi metadata
                // leave this one-session debug in-memory boundary.
                val objects = peers.deviceList.take(DebugWifiDirectCandidateLedger.MAX_CANDIDATES)
                onSnapshot(objects)
            }
        } catch (_: RuntimeException) {
            outstanding = false
            return false
        }
        return true
    }

    fun close() {
        closed = true
        outstanding = false
    }
}
