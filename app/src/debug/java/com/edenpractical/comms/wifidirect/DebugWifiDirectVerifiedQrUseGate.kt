package com.edenpractical.comms.wifidirect

/**
 * One use per successfully prepared three-QR Wi-Fi Direct transcript.
 *
 * This is NOT authorization: the request still needs actual QR-bound pinned
 * TLS, exporter checks, two human approvals and encrypted confirmations.
 * This gate only prevents accidental reuse of an already offered proof.
 *
 * reset() belongs exclusively to a fresh QR exchange / explicit clearing.
 * A failed socket, timeout, rejection or stopped radio must not reset it.
 */
internal class DebugWifiDirectVerifiedQrUseGate {
    private var issued = false

    @Synchronized
    fun available(): Boolean = !issued

    @Synchronized
    fun claim(): Boolean {
        if (issued) return false
        issued = true
        return true
    }

    @Synchronized
    fun resetForNewQrExchange() {
        issued = false
    }
}
