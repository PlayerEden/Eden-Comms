package com.edenpractical.comms.transport

import com.edenpractical.comms.model.ConnectionMethod

/**
 * FUTURE RADIO DISCOVERY STATE CONTRACT — NOT connected to Android radios/UI.
 *
 * A future Bluetooth/Wi-Fi Direct adapter must independently prove that its
 * Android permission was granted, that the app is foreground, and that the
 * OS radio scan was actually started. This object only tests the required
 * lifecycle ordering and bounds. It NEVER starts an actual scan, looks up
 * peers, handles permissions, retains addresses, or authorizes messaging.
 *
 * Instances are per discovery attempt coordinator; all callbacks require the
 * exact Attempt reference. Stopped attempts cannot resume or accept events.
 */
internal class NearbyDiscoverySession {
    enum class Phase {
        IDLE, WAITING_FOR_PERSON_CONSENT, WAITING_FOR_OS_PERMISSION,
        READY_FOR_RADIO, SCANNING, STOPPED
    }

    enum class StopReason {
        USER_CANCELLED, APP_BACKGROUNDED, PERMISSION_DENIED,
        PERMISSION_REVOKED, RADIO_UNAVAILABLE, TIME_LIMIT,
        INVALID_CLOCK, EVENT_LIMIT, INVALID_SEQUENCE
    }

    class Attempt internal constructor(
        val generation: Long,
        val method: ConnectionMethod
    )

    data class Snapshot(
        val phase: Phase,
        val method: ConnectionMethod?,
        val generation: Long,
        val observations: Int,
        val reason: StopReason?
    )

    companion object {
        const val MAX_SCAN_MS = 30_000L
        const val MAX_OBSERVATIONS = 32
    }

    private var generation = 0L
    private var current: Attempt? = null
    private var phase = Phase.IDLE
    private var reason: StopReason? = null
    private var startedElapsedMs: Long? = null
    private var observations = 0

    @Synchronized
    fun snapshot(): Snapshot = Snapshot(
        phase, current?.method, current?.generation ?: generation,
        observations, reason
    )

    /** Only explicitly user-started future-radio discovery, never LAN QR. */
    @Synchronized
    fun begin(method: ConnectionMethod, foreground: Boolean): Attempt? {
        if (!foreground || method == ConnectionMethod.LAN ||
            phase != Phase.IDLE || generation == Long.MAX_VALUE
        ) return null
        val attempt = Attempt(++generation, method)
        current = attempt
        phase = Phase.WAITING_FOR_PERSON_CONSENT
        reason = null
        startedElapsedMs = null
        observations = 0
        return attempt
    }

    /** An explicit person action, NOT a permission grant or peer approval. */
    @Synchronized
    fun personConsented(attempt: Attempt, foreground: Boolean): Boolean {
        if (!isCurrent(attempt)) return false
        if (!foreground) return stopCurrent(StopReason.APP_BACKGROUNDED).let { false }
        if (phase != Phase.WAITING_FOR_PERSON_CONSENT) {
            stopCurrent(StopReason.INVALID_SEQUENCE)
            return false
        }
        phase = Phase.WAITING_FOR_OS_PERMISSION
        return true
    }

    /**
     * Called only after a future Android adapter checks the real OS grant.
     * This method NEVER requests the permission or treats consent as a grant.
     */
    @Synchronized
    fun platformPermissionResult(
        attempt: Attempt,
        foreground: Boolean,
        granted: Boolean
    ): Boolean {
        if (!isCurrent(attempt)) return false
        if (!foreground) {
            stopCurrent(StopReason.APP_BACKGROUNDED)
            return false
        }
        if (phase != Phase.WAITING_FOR_OS_PERMISSION) {
            stopCurrent(StopReason.INVALID_SEQUENCE)
            return false
        }
        if (!granted) {
            stopCurrent(StopReason.PERMISSION_DENIED)
            return false
        }
        phase = Phase.READY_FOR_RADIO
        return true
    }

    /**
     * Future radio adapter reports its own real start. A model READY state
     * alone cannot trigger scanning; no radio call exists in this class.
     */
    @Synchronized
    fun radioScanStarted(
        attempt: Attempt,
        foreground: Boolean,
        adapterStarted: Boolean,
        elapsedMs: Long
    ): Boolean {
        if (!isCurrent(attempt)) return false
        if (!foreground) {
            stopCurrent(StopReason.APP_BACKGROUNDED)
            return false
        }
        if (phase != Phase.READY_FOR_RADIO) {
            stopCurrent(StopReason.INVALID_SEQUENCE)
            return false
        }
        if (!adapterStarted) {
            stopCurrent(StopReason.RADIO_UNAVAILABLE)
            return false
        }
        if (elapsedMs < 0L) {
            stopCurrent(StopReason.INVALID_CLOCK)
            return false
        }
        startedElapsedMs = elapsedMs
        phase = Phase.SCANNING
        return true
    }

    /**
     * Counts bounded, unauthenticated sightings; NO peer identity is stored.
     * Even a positive result cannot authorize a connection or message.
     */
    @Synchronized
    fun candidateObserved(
        attempt: Attempt,
        foreground: Boolean,
        elapsedMs: Long
    ): Boolean {
        if (!isCurrent(attempt)) return false
        if (!foreground) {
            stopCurrent(StopReason.APP_BACKGROUNDED)
            return false
        }
        if (phase != Phase.SCANNING) return false
        if (!withinWindow(elapsedMs)) return false
        if (observations >= MAX_OBSERVATIONS) {
            stopCurrent(StopReason.EVENT_LIMIT)
            return false
        }
        observations += 1
        return true
    }

    /** The adapter calls this as well as performing its real hardware stop. */
    @Synchronized
    fun tick(attempt: Attempt, elapsedMs: Long): Boolean {
        if (!isCurrent(attempt) || phase != Phase.SCANNING) return false
        return withinWindow(elapsedMs)
    }

    @Synchronized
    fun cancel(attempt: Attempt): Boolean = finish(attempt, StopReason.USER_CANCELLED)

    @Synchronized
    fun onBackground(attempt: Attempt): Boolean =
        finish(attempt, StopReason.APP_BACKGROUNDED)

    @Synchronized
    fun permissionRevoked(attempt: Attempt): Boolean =
        finish(attempt, StopReason.PERMISSION_REVOKED)

    @Synchronized
    fun radioUnavailable(attempt: Attempt): Boolean =
        finish(attempt, StopReason.RADIO_UNAVAILABLE)

    /**
     * A stopped attempt must be reset explicitly and remains unusable.
     * Generation never resets so late callbacks can't reanimate it.
     */
    @Synchronized
    fun resetAfterStop(): Boolean {
        if (phase != Phase.STOPPED) return false
        current = null
        phase = Phase.IDLE
        reason = null
        observations = 0
        startedElapsedMs = null
        return true
    }

    private fun withinWindow(elapsedMs: Long): Boolean {
        val start = startedElapsedMs ?: return false
        if (elapsedMs < start) {
            stopCurrent(StopReason.INVALID_CLOCK)
            return false
        }
        if (elapsedMs - start >= MAX_SCAN_MS) {
            stopCurrent(StopReason.TIME_LIMIT)
            return false
        }
        return true
    }

    private fun isCurrent(attempt: Attempt): Boolean =
        current === attempt && phase != Phase.IDLE && phase != Phase.STOPPED

    private fun finish(attempt: Attempt, stoppedBecause: StopReason): Boolean {
        if (!isCurrent(attempt)) return false
        return stopCurrent(stoppedBecause)
    }

    private fun stopCurrent(stoppedBecause: StopReason): Boolean {
        phase = Phase.STOPPED
        reason = stoppedBecause
        startedElapsedMs = null
        return true
    }
}
