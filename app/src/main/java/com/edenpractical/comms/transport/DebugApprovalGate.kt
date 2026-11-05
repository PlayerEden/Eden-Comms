package com.edenpractical.comms.transport

/**
 * Local, ephemeral state for the DEBUG-only human-approval experiment.
 *
 * A real APPROVE frame is transported only by the existing four-preset TLS
 * debug link after its pinned certificate / reciprocal QR checks. This model
 * is NOT production peer authorization. It has no session exporter proof,
 * reviewed application protocol, message send capability or trust persistence.
 *
 * "LOCAL_ONLY" means this phone successfully wrote an APPROVE control frame
 * to TLS; it does NOT mean that the peer received it.
 * "PEER_ONLY" means this phone decoded an APPROVE frame from its pinned TLS
 * connection; it does NOT prove that both devices agree on final status.
 */
internal class DebugApprovalGate {
    enum class State {
        WAITING, LOCAL_ONLY, PEER_ONLY, BOTH_OBSERVED, DECLINED, INVALID, CLOSED
    }

    private var current = State.WAITING

    @Synchronized
    fun snapshot(): State = current

    /** Called only after a local APPROVE control packet's TLS write completes. */
    @Synchronized
    fun localWritten(): State = when (current) {
        State.WAITING -> State.LOCAL_ONLY
        State.PEER_ONLY -> State.BOTH_OBSERVED
        State.LOCAL_ONLY, State.BOTH_OBSERVED -> State.INVALID
        State.DECLINED, State.INVALID, State.CLOSED -> current
    }.also { current = it }

    /** Called only after decoding APPROVE on the pinned, already-READY link. */
    @Synchronized
    fun peerObserved(): State = when (current) {
        State.WAITING -> State.PEER_ONLY
        State.LOCAL_ONLY -> State.BOTH_OBSERVED
        State.PEER_ONLY, State.BOTH_OBSERVED -> State.INVALID
        State.DECLINED, State.INVALID, State.CLOSED -> current
    }.also { current = it }

    @Synchronized
    fun decline(): State = when (current) {
        State.CLOSED -> State.CLOSED
        State.INVALID -> State.INVALID
        else -> State.DECLINED
    }.also { current = it }

    @Synchronized
    fun close(): State = State.CLOSED.also { current = it }

    @Synchronized
    fun maySendPreset(): Boolean = current == State.BOTH_OBSERVED
}
