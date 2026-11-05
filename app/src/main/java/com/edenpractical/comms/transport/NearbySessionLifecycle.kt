package com.edenpractical.comms.transport

import com.edenpractical.comms.model.ConnectionMethod

/**
 * Transport-neutral LIFECYCLE BOOKKEEPING ONLY.
 *
 * A future adapter may report how far a nearby attempt has progressed on local
 * Wi-Fi, Bluetooth or Wi-Fi Direct. This class does not open a socket, parse
 * packets, verify identities, implement user consent or encrypt anything.
 *
 * In particular, PREVIEW_READY is only a diagnostic lifecycle label. It is
 * NOT an authorization decision and must never gate production sending/audio.
 * No actual adapter or UI uses this model until reviewed integration work.
 */
internal class NearbySessionLifecycle {
    enum class Phase {
        IDLE, STARTING, LINK_PRESENT, PREVIEW_READY, TERMINATED
    }

    /** Fixed, privacy-safe classifications; no raw errors, addresses or peers. */
    enum class EndReason {
        USER_CANCELLED, APP_BACKGROUNDED, PEER_LEFT, NETWORK_CHANGED,
        TIMED_OUT, VERIFICATION_REJECTED, INVALID_SEQUENCE, TEST_ENDED
    }

    /**
     * An attempt's identity is an object reference only. It is not a secret,
     * a peer authentication proof or a bearer token for sending messages.
     */
    class Attempt internal constructor(
        val generation: Long,
        val method: ConnectionMethod
    )

    data class Snapshot(
        val phase: Phase,
        val method: ConnectionMethod?,
        val generation: Long,
        val endReason: EndReason?
    )

    private var nextGeneration = 0L
    private var current: Attempt? = null
    private var phase = Phase.IDLE
    private var endReason: EndReason? = null

    @Synchronized
    fun snapshot(): Snapshot = Snapshot(
        phase = phase,
        method = current?.method,
        generation = current?.generation ?: nextGeneration,
        endReason = endReason
    )

    /**
     * Start only from IDLE. After termination an explicit reset is required.
     * Any ConnectionMethod can be represented in a simulation; this neither
     * enables nor implements the corresponding Android radio transport.
     */
    @Synchronized
    fun begin(method: ConnectionMethod): Attempt? {
        if (phase != Phase.IDLE || nextGeneration == Long.MAX_VALUE) return null
        val attempt = Attempt(++nextGeneration, method)
        current = attempt
        phase = Phase.STARTING
        endReason = null
        return attempt
    }

    /**
     * An adapter reports only that its underlying link exists. No crypto or
     * identity assertion can be inferred from this notification.
     */
    @Synchronized
    fun linkPresent(attempt: Attempt): Boolean =
        transition(attempt, Phase.STARTING, Phase.LINK_PRESENT)

    /**
     * A later, separately verified adapter may report a test-only READY.
     * This state is still NOT permission to send arbitrary text or voice.
     */
    @Synchronized
    fun previewReady(attempt: Attempt): Boolean =
        transition(attempt, Phase.LINK_PRESENT, Phase.PREVIEW_READY)

    /** A read-only lifecycle check; not a send, consent or security gate. */
    @Synchronized
    fun isPreviewActive(attempt: Attempt): Boolean =
        attempt === current && phase == Phase.PREVIEW_READY

    @Synchronized
    fun finish(attempt: Attempt, reason: EndReason): Boolean {
        if (attempt !== current || phase == Phase.IDLE ||
            phase == Phase.TERMINATED
        ) return false
        phase = Phase.TERMINATED
        endReason = reason
        return true
    }

    /**
     * Clear the previous attempt only after it terminated. The monotonic
     * counter is deliberately never reset: old callbacks cannot be reused
     * after starting a new local attempt on this coordinator instance.
     */
    @Synchronized
    fun resetAfterTermination(): Boolean {
        if (phase != Phase.TERMINATED) return false
        current = null
        phase = Phase.IDLE
        endReason = null
        return true
    }

    private fun transition(
        attempt: Attempt, expected: Phase, next: Phase
    ): Boolean {
        if (attempt !== current || phase == Phase.IDLE ||
            phase == Phase.TERMINATED
        ) return false
        if (phase != expected) {
            // A current attempt with a duplicate/out-of-order callback is
            // terminal. Stale attempts must not poison the newer attempt.
            phase = Phase.TERMINATED
            endReason = EndReason.INVALID_SEQUENCE
            return false
        }
        phase = next
        return true
    }
}
