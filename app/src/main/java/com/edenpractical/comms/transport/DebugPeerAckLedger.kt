package com.edenpractical.comms.transport

/**
 * In-memory, per-LAN-debug-session accounting for peer-app ACKs.
 *
 * A peer app ACK is not a read receipt, authenticated human action, proof
 * of display, or production delivery guarantee. TLS/QR/consent are separate
 * gates. This ledger has no socket, persistence or automatic retry logic.
 *
 * Writer and reader run on different threads; an authentic peer ACK can
 * race with the local writer callback. In that case it is held (without
 * reporting success) until the writer confirms that the packet was written.
 */
internal class DebugPeerAckLedger(
    private val maxOutstanding: Int = 16
) {
    enum class Result { REJECT, WAITING_FOR_WRITE, ACKNOWLEDGED }
    private enum class Status { QUEUED, WRITTEN, EARLY_ACK }
    private val guard = Any()
    private val pending = LinkedHashMap<Long, Status>()
    private var lastIssued = 0L
    private var closed = false

    init {
        require(maxOutstanding in 1..64)
    }

    fun queue(id: Long): Boolean = synchronized(guard) {
        if (closed || id <= 0L || id <= lastIssued ||
            pending.size >= maxOutstanding) return@synchronized false
        lastIssued = id
        pending[id] = Status.QUEUED
        true
    }

    /**
     * A true return means an ACK arrived before the local write callback;
     * the caller MUST report TEXT_WRITTEN before reporting the ACK.
     */
    fun written(id: Long): Boolean = synchronized(guard) {
        if (closed) return@synchronized false
        when (pending[id]) {
            Status.QUEUED -> {
                pending[id] = Status.WRITTEN
                false
            }
            Status.EARLY_ACK -> {
                pending.remove(id)
                true
            }
            else -> false
        }
    }

    fun acknowledge(id: Long, mutuallyConfirmed: Boolean): Result = synchronized(guard) {
        if (closed || !mutuallyConfirmed || id <= 0L) return@synchronized Result.REJECT
        when (pending[id]) {
            Status.WRITTEN -> {
                pending.remove(id)
                Result.ACKNOWLEDGED
            }
            Status.QUEUED -> {
                pending[id] = Status.EARLY_ACK
                Result.WAITING_FOR_WRITE
            }
            else -> Result.REJECT // unknown ID or a duplicate/replayed ACK
        }
    }

    fun cancel(id: Long) {
        synchronized(guard) { pending.remove(id) }
    }

    fun close() {
        synchronized(guard) {
            closed = true
            pending.clear()
        }
    }

    val outstanding: Int get() = synchronized(guard) { pending.size }
}
