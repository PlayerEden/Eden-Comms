package com.edenpractical.comms.transport

/**
 * A reader-thread, per-socket monotonically increasing message-ID check.
 * This is defense-in-depth on top of the existing pinned TLS debug test;
 * it is NOT cryptographic replay protection, session authentication, or an ACK.
 */
internal class DebugTextReceiveGuard {
    private var lastSeen = 0L

    fun accept(messageId: Long, confirmedHere: Boolean): Boolean {
        if (!confirmedHere || messageId <= lastSeen || messageId <= 0L) return false
        lastSeen = messageId
        return true
    }
}
