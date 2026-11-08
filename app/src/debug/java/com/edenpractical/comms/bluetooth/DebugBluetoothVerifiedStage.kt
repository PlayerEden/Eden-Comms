package com.edenpractical.comms.bluetooth

/**
 * F2-K: READ-ONLY milestones emitted by the actual RFCOMM/TLS worker after
 * each specific cryptographic check has returned successfully.
 *
 * These names and counts carry no certificate, pin, exporter, nonce,
 * transcript, IP/MAC address or message. A UI observation does not become a
 * trusted-peer credential, an authorization to retain the socket, or a chat
 * capability. The real worker remains diagnostic-only and closes the link.
 */
internal enum class DebugBluetoothVerifiedStage(val step: Int, val summary: String) {
    TLS_PIN(1, "Both certificate pins checked over TLS 1.3"),
    QR_CHALLENGES(2, "Fresh QR challenges matched"),
    EXPORTER(3, "Both sides agreed on the same TLS channel"),
    COMMITMENT(4, "Encrypted session transcript matched"),
    HUMAN_CONFIRMATIONS(5, "Both approvals and final confirmations checked");

    fun display(): String = "Verified test step $step/5: $summary"
}
