package com.edenpractical.comms.bluetooth

/**
 * F2-E: one-use, non-authorizing *diagnostic completion* marker.
 *
 * Minted only at the end of the live debug RFCOMM probe, after the real pinned
 * TLS 1.3, fresh bilateral QR challenge, exporter, commitment, two individual
 * human approvals and two encrypted CONFIRM records all succeed.
 *
 * Important: this is NOT an authenticated transport capability or reusable
 * credential. The controller must still bind the result to its CURRENT socket
 * token, check foreground/permission and immediately close that socket.
 * It deliberately contains no SSLEngine, socket, key, peer pin, exporter,
 * commitment, plaintext, send API, or durable trust/persistence.
 */
internal class DebugBluetoothProbeCompletion private constructor(
    private val hostRole: Boolean,
    private val issuedAtSeconds: Long,
    private val qrExpiresAtSeconds: Long
) : AutoCloseable {
    companion object {
        const val MAX_HANDOFF_AGE_SECONDS = 3L

        /**
         * This constructor is isolated for deterministic tests. Calling it
         * outside the real probe DOES NOT prove TLS, QR or human consent.
         * Never use this object as a standalone session authorization.
         */
        internal fun afterVerifiedDiagnostic(
            hostRole: Boolean,
            issuedAtSeconds: Long,
            qrExpiresAtSeconds: Long
        ): DebugBluetoothProbeCompletion {
            require(issuedAtSeconds >= 0 && qrExpiresAtSeconds >= issuedAtSeconds) {
                "Invalid Bluetooth diagnostic completion clock or QR expiry"
            }
            return DebugBluetoothProbeCompletion(
                hostRole, issuedAtSeconds, qrExpiresAtSeconds
            )
        }
    }

    private var consumed = false
    private var closed = false

    /**
     * One opportunity to record diagnostic completion. Even a mismatch burns
     * the receipt, preventing a later retry under a changed role or clock.
     *
     * The caller separately checks that this receipt came from the SAME live
     * controller worker/socket token; this object has no access to sockets.
     */
    @Synchronized
    fun acknowledgeOnce(
        expectedHostRole: Boolean,
        observedAtSeconds: Long,
        foreground: Boolean,
        connectPermission: Boolean
    ): Boolean {
        if (consumed || closed) return false
        consumed = true
        return expectedHostRole == hostRole &&
            observedAtSeconds >= issuedAtSeconds &&
            observedAtSeconds <= qrExpiresAtSeconds &&
            observedAtSeconds - issuedAtSeconds <= MAX_HANDOFF_AGE_SECONDS &&
            foreground && connectPermission
    }

    @Synchronized
    override fun close() {
        consumed = true
        closed = true
    }
}
