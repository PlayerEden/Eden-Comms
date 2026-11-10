package com.edenpractical.comms.wifidirect

/**
 * Pure identity-only lifetime checks for asynchronous Android P2P callbacks.
 *
 * No pointer, OS group, QR transcript or previous controller can confer
 * authorization to chat. These comparisons only decide whether an old
 * callback may update/cancel the CURRENT owner; TLS + both approvals
 * remain separate and mandatory.
 */
internal object DebugWifiDirectCallbackFence {
    fun owns(current: Any?, expected: Any?): Boolean =
        expected != null && current === expected

    fun mayFailPending(
        current: Any?,
        expected: Any?,
        pendingRequest: Any?,
        expectedRequest: Any?,
        groupObserved: Boolean,
        verifiedSessionPresent: Boolean
    ): Boolean =
        owns(current, expected) &&
            expectedRequest != null &&
            pendingRequest === expectedRequest &&
            groupObserved &&
            !verifiedSessionPresent
}
