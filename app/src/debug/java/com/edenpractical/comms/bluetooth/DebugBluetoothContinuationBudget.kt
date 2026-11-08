package com.edenpractical.comms.bluetooth

/**
 * F2-C: future-only, bounded post-consent lifecycle model for one Bluetooth
 * attempt. NOT connected to any RFCOMM socket, TLS engine, callback or chat.
 * Caller-supplied booleans are observations, not cryptographic verification:
 * this model MUST NOT be reused as proof of identity, trust or send authority.
 *
 * Current Bluetooth diagnostic still stops immediately on mutual confirmation.
 * Security review and physical Android tests are required before any wiring.
 */
internal class DebugBluetoothContinuationBudget(
    private val maxLifetimeMs: Long = 90_000L
) {
    companion object { const val MAX_FUTURE_LIFETIME_MS = 120_000L }
    init { require(maxLifetimeMs in 1..MAX_FUTURE_LIFETIME_MS) }

    data class Observations(
        val peerPinChecked: Boolean,
        val qrChallengeChecked: Boolean,
        val exporterChecked: Boolean,
        val bilateralApprovals: Boolean,
        val bilateralConfirmations: Boolean,
        val foreground: Boolean,
        val connectPermission: Boolean
    ) {
        fun allObserved(): Boolean =
            peerPinChecked && qrChallengeChecked && exporterChecked &&
                bilateralApprovals && bilateralConfirmations &&
                foreground && connectPermission
    }

    enum class Phase { NOT_STARTED, OBSERVING, TERMINATED }
    private var phase = Phase.NOT_STARTED
    private var attemptId: Long? = null
    private var deadlineMs = -1L

    @Synchronized
    fun beginObservation(
        generation: Long,
        elapsedMs: Long,
        observations: Observations
    ): Boolean {
        if (phase != Phase.NOT_STARTED || generation <= 0 ||
            elapsedMs < 0 || elapsedMs > Long.MAX_VALUE - maxLifetimeMs ||
            !observations.allObserved()
        ) return false
        attemptId = generation
        deadlineMs = elapsedMs + maxLifetimeMs
        phase = Phase.OBSERVING
        return true
    }

    /**
     * Strictly bounded observation only. This method does not send, retain or
     * make a socket usable. Losing either Android prerequisite permanently
     * closes this one-shot model rather than allowing automatic reconnect.
     */
    @Synchronized
    fun stillObserved(
        generation: Long,
        elapsedMs: Long,
        foreground: Boolean,
        connectPermission: Boolean
    ): Boolean {
        if (phase != Phase.OBSERVING || attemptId != generation) return false
        if (elapsedMs < 0 || elapsedMs >= deadlineMs ||
            !foreground || !connectPermission
        ) {
            phase = Phase.TERMINATED
            return false
        }
        return true
    }

    @Synchronized
    fun terminate(generation: Long): Boolean {
        if (phase != Phase.OBSERVING || attemptId != generation) return false
        phase = Phase.TERMINATED
        return true
    }

    @Synchronized
    fun snapshot(): Phase = phase
}
