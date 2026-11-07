package com.edenpractical.comms.bluetooth

import com.edenpractical.comms.security.LocalTlsIdentity
import java.security.MessageDigest

/**
 * Transient, per-user-tap input for an experimental TLS 1.3 RFCOMM handshake.
 * Contains an Android Keystore private-key HANDLE (never exported) and a
 * defensively copied expected full public certificate pin from the in-person
 * QR. The QR is not a proof until the actual peer signs the TLS handshake.
 */
internal class DebugBluetoothTlsProbeInput(
    val localIdentity: LocalTlsIdentity,
    expectedPeerPin: ByteArray,
    val hostRole: Boolean
) : AutoCloseable {
    private val peerPin = expectedPeerPin.copyOf()
    @Volatile private var closed = false

    init {
        require(peerPin.size == 32 && peerPin.any { it != 0.toByte() }) {
            "A fresh exact peer certificate pin is required"
        }
        require(!MessageDigest.isEqual(peerPin, localIdentity.publicFingerprint())) {
            "Cannot authenticate this phone as its own peer"
        }
    }

    @Synchronized
    fun pinCopy(): ByteArray {
        check(!closed) { "Bluetooth TLS attempt expired" }
        return peerPin.copyOf()
    }

    @Synchronized
    override fun close() {
        peerPin.fill(0)
        closed = true
    }
}
