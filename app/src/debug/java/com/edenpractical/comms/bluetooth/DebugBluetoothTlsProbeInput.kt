package com.edenpractical.comms.bluetooth

import com.edenpractical.comms.security.LocalTlsIdentity
import com.edenpractical.comms.security.SessionProofBinding
import java.security.MessageDigest

/**
 * Snapshot of the two in-person QR public certificate pins and ephemeral
 * nonces for ONE DEBUG Bluetooth TLS attempt. Does not retain private-key
 * material (the local Keystore owns signing); not a trusted peer record.
 */
internal class DebugBluetoothTlsProbeInput(
    val localIdentity: LocalTlsIdentity,
    expectedPeerPin: ByteArray,
    val hostRole: Boolean,
    binding: SessionProofBinding,
    val expiresAtSeconds: Long
) : AutoCloseable {
    private val peerPin = expectedPeerPin.copyOf()
    private val pinsAndNonces = SessionProofBinding(
        binding.hostPin, binding.joinPin, binding.hostNonce, binding.joinNonce
    )
    @Volatile private var closed = false

    init {
        require(peerPin.size == 32 && peerPin.any { it != 0.toByte() }) {
            "A fresh exact peer certificate pin is required"
        }
        require(!MessageDigest.isEqual(peerPin, localIdentity.publicFingerprint())) {
            "Cannot authenticate this phone as its own peer"
        }
        require(MessageDigest.isEqual(
            peerPin, if (hostRole) pinsAndNonces.joinPin else pinsAndNonces.hostPin
        ) && MessageDigest.isEqual(
            localIdentity.publicFingerprint(),
            if (hostRole) pinsAndNonces.hostPin else pinsAndNonces.joinPin
        )) { "Local identity or expected peer does not match the QR role" }
    }

    @Synchronized
    fun pinCopy(): ByteArray {
        requireLive()
        return peerPin.copyOf()
    }

    @Synchronized
    fun freshBinding(): SessionProofBinding {
        requireLive()
        return SessionProofBinding(
            pinsAndNonces.hostPin, pinsAndNonces.joinPin,
            pinsAndNonces.hostNonce, pinsAndNonces.joinNonce
        )
    }

    @Synchronized
    override fun close() {
        peerPin.fill(0)
        pinsAndNonces.hostPin.fill(0)
        pinsAndNonces.joinPin.fill(0)
        pinsAndNonces.hostNonce.fill(0)
        pinsAndNonces.joinNonce.fill(0)
        closed = true
    }

    private fun requireLive() {
        val now = System.currentTimeMillis() / 1000
        check(!closed && now in (expiresAtSeconds - 345L)..expiresAtSeconds) {
            "This Bluetooth QR pairing attempt expired or was cleared"
        }
    }
}
