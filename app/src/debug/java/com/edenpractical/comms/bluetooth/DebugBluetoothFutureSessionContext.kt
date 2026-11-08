package com.edenpractical.comms.bluetooth

import java.security.MessageDigest

/**
 * F2-I: UNTRUSTED, debug-only future handoff matching context.
 *
 * Contains copies of the expected peer's public certificate SHA-256 pin and
 * the domain-separated 32-byte verified-transcript commitment, plus local
 * host/join role. Matching these bytes is necessary for a future handoff,
 * but NEVER sufficient to prove any TLS, QR, exporter or human consent:
 * this type can be constructed with arbitrary inputs.
 *
 * Must NOT enter the real RFCOMM controller/probe, persist peer details,
 * grant send/receive, or become a credential. Live message authorization
 * requires a separately reviewed same-socket TLS/session owner and device QA.
 */
internal class DebugBluetoothFutureSessionContext(
    val hostRole: Boolean,
    expectedPeerPin: ByteArray,
    transcriptCommitment: ByteArray
) : AutoCloseable {
    private val pin = expectedPeerPin.copyOf()
    private val commitment = transcriptCommitment.copyOf()
    private var closed = false

    init {
        require(valid(pin) && valid(commitment)) {
            "Future Bluetooth review context requires two nonzero 32-byte values"
        }
    }

    @Synchronized
    fun copyForReview(): DebugBluetoothFutureSessionContext {
        check(!closed) { "Closed future Bluetooth context cannot be reused" }
        return DebugBluetoothFutureSessionContext(hostRole, pin, commitment)
    }

    /**
     * Constant-time comparison for the two public-sized bindings. A caller
     * can fabricate a matching context: this is a correlation guard only.
     */
    fun sameAs(other: DebugBluetoothFutureSessionContext): Boolean {
        // Lock ordering avoided by comparing separate immutable copies taken
        // while their individual monitors are held.
        val local = snapshotOrNull() ?: return false
        val remote = other.snapshotOrNull() ?: run {
            local.first.fill(0)
            local.second.fill(0)
            return false
        }
        return try {
            hostRole == other.hostRole &&
                MessageDigest.isEqual(local.first, remote.first) &&
                MessageDigest.isEqual(local.second, remote.second)
        } finally {
            local.first.fill(0)
            local.second.fill(0)
            remote.first.fill(0)
            remote.second.fill(0)
        }
    }

    private fun snapshotOrNull(): Pair<ByteArray, ByteArray>? = synchronized(this) {
        if (closed) null else pin.copyOf() to commitment.copyOf()
    }

    @Synchronized
    override fun close() {
        if (!closed) {
            pin.fill(0)
            commitment.fill(0)
            closed = true
        }
    }

    companion object {
        private fun valid(bytes: ByteArray): Boolean =
            bytes.size == 32 && bytes.any { it != 0.toByte() }
    }
}
