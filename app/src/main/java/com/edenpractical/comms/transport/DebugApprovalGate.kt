package com.edenpractical.comms.transport

/**
 * Per-socket, DEBUG-ONLY human approval and final-confirmation model.
 *
 * A real person must approve on both devices; then each phone writes an
 * explicit session-bound CONFIRM after it has observed BOTH approvals.
 * The four fixed phrases remain prohibited until this phone has both written
 * its own confirmation and observed the peer's confirmation on the pinned TLS
 * link. A local write is not a remote delivery receipt or production consent.
 *
 * The reader and writer run on different threads. A peer confirmation can
 * legitimately arrive just before this phone's local-write callback updates
 * its approval state; it is recorded only after the peer APPROVE was observed
 * and never unlocks anything until all four events are present.
 */
internal class DebugApprovalGate {
    enum class State {
        WAITING, LOCAL_ONLY, PEER_ONLY, BOTH_OBSERVED,
        LOCAL_CONFIRMED, PEER_CONFIRMED, CONFIRMED,
        DECLINED, INVALID, CLOSED
    }

    private var localApprovalWritten = false
    private var peerApprovalObserved = false
    private var localConfirmationWritten = false
    private var peerConfirmationObserved = false
    private var terminal: State? = null

    @Synchronized
    fun snapshot(): State = current()

    @Synchronized
    fun localWritten(): State {
        if (terminal != null) return current()
        if (localApprovalWritten) return invalidate()
        localApprovalWritten = true
        return current()
    }

    @Synchronized
    fun peerObserved(): State {
        if (terminal != null) return current()
        if (peerApprovalObserved) return invalidate()
        peerApprovalObserved = true
        return current()
    }

    /** Sending a CONFIRM is allowed only after both approvals were observed. */
    @Synchronized
    fun mayWriteConfirmation(): Boolean =
        terminal == null && localApprovalWritten && peerApprovalObserved &&
            !localConfirmationWritten

    @Synchronized
    fun localConfirmationWritten(): State {
        if (terminal != null) return current()
        if (!localApprovalWritten || !peerApprovalObserved || localConfirmationWritten) {
            return invalidate()
        }
        localConfirmationWritten = true
        return current()
    }

    /**
     * Receiver MUST first check that the CONFIRM carries this socket's exact
     * session commitment. The peer's own APPROVE must precede its CONFIRM
     * on the same ordered TLS stream.
     */
    @Synchronized
    fun peerConfirmationObserved(): State {
        if (terminal != null) return current()
        if (!peerApprovalObserved || peerConfirmationObserved) return invalidate()
        peerConfirmationObserved = true
        return current()
    }

    @Synchronized
    fun decline(): State {
        if (terminal == null) terminal = State.DECLINED
        return current()
    }

    @Synchronized
    fun close(): State {
        terminal = State.CLOSED
        return current()
    }

    @Synchronized
    fun maySendPreset(): Boolean = current() == State.CONFIRMED

    private fun invalidate(): State {
        terminal = State.INVALID
        return State.INVALID
    }

    private fun current(): State {
        terminal?.let { return it }
        if (localApprovalWritten && peerApprovalObserved) {
            return when {
                localConfirmationWritten && peerConfirmationObserved -> State.CONFIRMED
                localConfirmationWritten -> State.LOCAL_CONFIRMED
                peerConfirmationObserved -> State.PEER_CONFIRMED
                else -> State.BOTH_OBSERVED
            }
        }
        return when {
            localApprovalWritten -> State.LOCAL_ONLY
            peerApprovalObserved -> State.PEER_ONLY
            else -> State.WAITING
        }
    }
}
