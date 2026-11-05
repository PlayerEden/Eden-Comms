package com.edenpractical.comms.transport

import com.edenpractical.comms.model.ConnectionMethod
import java.util.PriorityQueue

/**
 * JVM TEST-ONLY virtual adapter. No Android radio, sockets, clocks, TLS,
 * traffic, authentication, packet parsing, permissions, or production send.
 *
 * Its scheduled callbacks are intentionally delivered EVEN AFTER cancel,
 * modeling non-cancellable work already queued on a platform callback thread.
 * The model under test must reject these late events on its own.
 */
internal class ScriptedNearbyTransportAdapter(
    val method: ConnectionMethod,
    val lifecycle: NearbySessionLifecycle = NearbySessionLifecycle()
) {
    enum class Signal {
        LINK_PRESENT, PREVIEW_READY,
        PEER_LOST, NETWORK_CHANGED, TIMED_OUT, VERIFICATION_REJECTED
    }

    data class Delivery(
        val atMs: Long,
        val signal: Signal,
        val accepted: Boolean,
        val result: NearbySessionLifecycle.Snapshot
    )

    private data class Task(
        val atMs: Long,
        val sequence: Long,
        val attempt: NearbySessionLifecycle.Attempt,
        val signal: Signal
    )

    companion object {
        const val MAX_PENDING = 64
        const val MAX_ADVANCE_MS = 24L * 60L * 60L * 1000L
    }

    private val queue = PriorityQueue<Task>(
        compareBy<Task> { it.atMs }.thenBy { it.sequence }
    )
    private var sequence = 0L

    var clockMs: Long = 0L
        private set

    val pendingCount: Int get() = queue.size

    /** No connection is actually made; this only starts lifecycle bookkeeping. */
    fun begin(): NearbySessionLifecycle.Attempt? = lifecycle.begin(method)

    fun resetAfterTermination(): Boolean = lifecycle.resetAfterTermination()

    /** Cancellation is immediate. Pending events still arrive to test rejection. */
    fun cancel(attempt: NearbySessionLifecycle.Attempt): Boolean =
        lifecycle.finish(attempt, NearbySessionLifecycle.EndReason.USER_CANCELLED)

    fun appBackgrounded(attempt: NearbySessionLifecycle.Attempt): Boolean =
        lifecycle.finish(attempt, NearbySessionLifecycle.EndReason.APP_BACKGROUNDED)

    /**
     * Queue only fixed fake events. Invalid delays, queue exhaustion, or
     * virtual-time overflow are refused without allocating additional work.
     */
    fun post(
        attempt: NearbySessionLifecycle.Attempt,
        signal: Signal,
        delayMs: Long
    ): Boolean {
        if (delayMs < 0 || delayMs > MAX_ADVANCE_MS ||
            queue.size >= MAX_PENDING || sequence == Long.MAX_VALUE ||
            clockMs > Long.MAX_VALUE - delayMs
        ) return false
        queue.add(Task(clockMs + delayMs, sequence++, attempt, signal))
        return true
    }

    /**
     * Virtual elapsed time; stable ordering for same-timestamp callbacks.
     * Returns fixed metadata only, never peer or user data.
     */
    fun advanceBy(deltaMs: Long): List<Delivery> {
        require(deltaMs in 0..MAX_ADVANCE_MS) { "Invalid virtual-time increment" }
        require(clockMs <= Long.MAX_VALUE - deltaMs) { "Virtual clock overflow" }
        val target = clockMs + deltaMs
        val results = mutableListOf<Delivery>()
        while (queue.isNotEmpty() && queue.peek().atMs <= target) {
            val task = queue.remove()
            clockMs = task.atMs
            val accepted = when (task.signal) {
                Signal.LINK_PRESENT -> lifecycle.linkPresent(task.attempt)
                Signal.PREVIEW_READY -> lifecycle.previewReady(task.attempt)
                Signal.PEER_LOST -> lifecycle.finish(
                    task.attempt, NearbySessionLifecycle.EndReason.PEER_LEFT
                )
                Signal.NETWORK_CHANGED -> lifecycle.finish(
                    task.attempt, NearbySessionLifecycle.EndReason.NETWORK_CHANGED
                )
                Signal.TIMED_OUT -> lifecycle.finish(
                    task.attempt, NearbySessionLifecycle.EndReason.TIMED_OUT
                )
                Signal.VERIFICATION_REJECTED -> lifecycle.finish(
                    task.attempt, NearbySessionLifecycle.EndReason.VERIFICATION_REJECTED
                )
            }
            results.add(Delivery(task.atMs, task.signal, accepted, lifecycle.snapshot()))
        }
        clockMs = target
        return results
    }
}
