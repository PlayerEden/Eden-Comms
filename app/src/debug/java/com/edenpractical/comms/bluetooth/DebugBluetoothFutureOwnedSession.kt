package com.edenpractical.comms.bluetooth

import java.io.Closeable

/**
 * F2-H: FUTURE-ONLY, DEBUG-ONLY ownership rehearsal for a single bounded
 * post-consent connection resource. NOT wired to any real Android RFCOMM
 * socket, SSLEngine, live callback, peer identity, UI, or message transport.
 *
 * This class intentionally has no send/read API. The observations supplied
 * to [begin] are CALLER CLAIMS, NOT cryptographic proof or authorization.
 * Neither this class nor the F2-E diagnostic completion can authorize chat.
 * Real socket retention still requires independent security/protocol review
 * and two-phone Android/TLS provider acceptance.
 *
 * Ownership transfers to begin() even if admission fails: every rejected
 * distinct resource is closed. After admission, stop/clock rollback/expiry/
 * background/permission loss/close irrevocably terminate the observation
 * and close the owned resource. Caller time must be a monotonic time source
 * (e.g. elapsedRealtime on Android); all checks are policy rehearsals only.
 */
internal class DebugBluetoothFutureOwnedSession(
    private val budget: DebugBluetoothContinuationBudget =
        DebugBluetoothContinuationBudget()
) : AutoCloseable {
    enum class Phase { NEW, OBSERVING, CLOSED }
    enum class End { REFUSED, INVALIDATED, USER, OWNER_CLOSED }
    data class Snapshot(val phase: Phase, val end: End?)

    private val lock = Any()
    private var phase = Phase.NEW
    private var end: End? = null
    private var attempt: Long? = null
    private var resource: Closeable? = null

    fun begin(
        generation: Long,
        elapsedMs: Long,
        observations: DebugBluetoothContinuationBudget.Observations,
        owned: Closeable
    ): Boolean {
        val admitted = synchronized(lock) {
            if (phase != Phase.NEW) {
                false
            } else if (!budget.beginObservation(generation, elapsedMs, observations)) {
                phase = Phase.CLOSED
                end = End.REFUSED
                false
            } else {
                attempt = generation
                resource = owned
                phase = Phase.OBSERVING
                true
            }
        }
        if (!admitted) {
            // A duplicate call cannot close the resource currently held by
            // the live observation, even if caller passes the same instance.
            val alreadyOwned = synchronized(lock) { resource === owned }
            if (!alreadyOwned) closeQuietly(owned)
        }
        return admitted
    }

    /**
     * A stale/wrong generation is ignored without affecting an active owner.
     * A matching invalid observation permanently consumes this session and
     * closes its owned resource; no second QR attempt is accepted here.
     */
    fun observe(
        generation: Long,
        elapsedMs: Long,
        foreground: Boolean,
        connectPermission: Boolean
    ): Boolean {
        var released: Closeable? = null
        val active = synchronized(lock) {
            if (phase != Phase.OBSERVING || attempt != generation) {
                false
            } else if (budget.stillObserved(
                    generation, elapsedMs, foreground, connectPermission
                )
            ) {
                true
            } else {
                phase = Phase.CLOSED
                end = End.INVALIDATED
                released = resource
                resource = null
                false
            }
        }
        closeQuietly(released)
        return active
    }

    /** One-way user cancel. Stale queued cancellation cannot close a new link. */
    fun stop(generation: Long): Boolean {
        var released: Closeable? = null
        val didStop = synchronized(lock) {
            if (phase != Phase.OBSERVING || attempt != generation) {
                false
            } else {
                budget.terminate(generation)
                phase = Phase.CLOSED
                end = End.USER
                released = resource
                resource = null
                true
            }
        }
        closeQuietly(released)
        return didStop
    }

    fun snapshot(): Snapshot = synchronized(lock) { Snapshot(phase, end) }

    override fun close() {
        var released: Closeable? = null
        synchronized(lock) {
            if (phase != Phase.CLOSED) {
                attempt?.let { budget.terminate(it) }
                phase = Phase.CLOSED
                end = End.OWNER_CLOSED
                released = resource
                resource = null
            }
        }
        closeQuietly(released)
    }

    private fun closeQuietly(target: Closeable?) {
        try { target?.close() } catch (_: Exception) {
            // A close failure must never resurrect or re-arm a terminated
            // session. The real owner must report/handle OS close failures.
        }
    }
}
