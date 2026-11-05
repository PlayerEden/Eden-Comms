package com.edenpractical.comms.pairing

/**
 * Human-readable copy for the debug-only two-QR / four-preset-phrase flow.
 *
 * A successful READY event establishes a local experimental test connection,
 * not general messaging authorization, delivery confirmation or certification.
 * These strings deliberately avoid describing simulated consent as real consent.
 */
internal object PairingTestUiCopy {
    fun progress(stage: String): String? = when (stage) {
        "showHost", "scanHost" -> "PAIRING • STEP 1 OF 2"
        "scanReply", "showReply" -> "PAIRING • STEP 2 OF 2"
        "connecting" -> "LOCAL CONNECTION • VERIFYING"
        "approval" -> "LOCAL CONNECTION • TWO-PERSON TEST APPROVAL"
        "demo" -> "LOCAL CONNECTION • TEST ACTIVE"
        "result" -> "CONNECTION TEST • SUMMARY"
        else -> null
    }

    /** Keeps QR controls and preset phrases reachable at narrow widths / large text. */
    fun useStackedActions(availableWidthDp: Float, fontScale: Float): Boolean =
        availableWidthDp < 360f || fontScale >= 1.3f

    fun resultHeading(reachedReady: Boolean, tlsAndQrReady: Boolean = false): String =
        when {
            reachedReady -> "Test connection established"
            tlsAndQrReady -> "Test link ended before final agreement"
            else -> "Connection not established"
        }

    fun resultSummary(
        reachedReady: Boolean,
        sent: Int,
        received: Int,
        tlsAndQrReady: Boolean = false
    ): String {
        require(sent >= 0 && received >= 0) { "Counters cannot be negative" }
        return when {
            reachedReady ->
                "This phone wrote $sent preset phrase(s) to TLS and received $received. " +
                    "Sent does not mean delivered or read. The on-screen phrase list was cleared."
            tlsAndQrReady ->
                "The debug TLS/QR/channel-binding test passed, but both human approvals " +
                    "and final two-way confirmations were not completed here. " +
                    "No preset conversation began. " +
                    "This is not independently reviewed messaging authorization."
            else ->
                "The test stopped before reaching a verified local session. " +
                    "Start again with two fresh codes and both phones on the same Wi-Fi."
        }
    }
}
