package com.edenpractical.comms.wifidirect

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import com.edenpractical.comms.security.SessionProofBinding

/**
 * F3-B1/F3-C: role-ordered QR-to-TLS EXPECTATION, never authorization.
 *
 * Both public certificate fingerprints and all three fresh, canonical QR
 * frames are checked before constructing an exact peer pin. This is NOT an
 * authenticated Wi-Fi Direct radio connection or proof of two human approvals.
 * The host's creation of the third QR does not prove that the joiner scanned
 * it. Matching public transcript data cannot authenticate a selected P2P
 * candidate; the actual TLS peer MUST present the exact pinned certificate.
 *
 * F3-C live QR coordinator can only copy exact peer-pin and nonce inputs;
 * full same-channel TLS/QR/exporter + two-person consent is independently
 * required before ANY app data. This class itself has no socket or send API.
 */
internal class DebugWifiDirectFutureQrTlsExpectation private constructor(
    val role: Role,
    peerPin: ByteArray,
    transcriptDigest: ByteArray,
    hostPin: ByteArray,
    joinPin: ByteArray,
    hostNonce: ByteArray,
    joinNonce: ByteArray
) : AutoCloseable {
    enum class Role { HOST, JOIN }

    private val pin = peerPin.copyOf()
    private val transcript = transcriptDigest.copyOf()
    private val hostQrPin = hostPin.copyOf()
    private val joinQrPin = joinPin.copyOf()
    private val hostQrNonce = hostNonce.copyOf()
    private val joinQrNonce = joinNonce.copyOf()
    private var closed = false

    init {
        require(pin.size == 32 && pin.any { it != 0.toByte() })
        require(transcript.size == 32 && transcript.any { it != 0.toByte() })
        require(hostQrPin.size == 32 && joinQrPin.size == 32 &&
            hostQrNonce.size == 16 && joinQrNonce.size == 16)
        require(!MessageDigest.isEqual(hostQrPin, joinQrPin) &&
            !MessageDigest.isEqual(hostQrNonce, joinQrNonce))
        val expected = if (role == Role.HOST) joinQrPin else hostQrPin
        require(MessageDigest.isEqual(pin, expected))
    }

    @Synchronized
    fun copyPeerPinForTls(): ByteArray {
        check(!closed) { "Closed Wi-Fi Direct QR expectation cannot be used" }
        return pin.copyOf()
    }

    @Synchronized
    fun copyTranscriptForReview(): ByteArray {
        check(!closed) { "Closed Wi-Fi Direct QR expectation cannot be used" }
        return transcript.copyOf()
    }

    /**
     * F3-B2 only: independent copy of the exact ordered QR pins/nonces
     * for one encrypted SessionProofExchange on the SAME pinned TLS socket.
     * A binding is not human approval; the lab must wipe its copies.
     */
    /**
     * A checked pin/nonce expectation is not a send grant. The live P2P
     * controller still requires actual mutual TLS/QR/exporter, both human
     * approvals and encrypted final confirmations on the same socket.
     */
    @Synchronized
    fun copyLiveQrProofBinding(): SessionProofBinding {
        check(!closed) { "Closed QR preparation cannot be used" }
        return SessionProofBinding(hostQrPin, joinQrPin, hostQrNonce, joinQrNonce)
    }

    @Synchronized
    fun copyQrProofBindingForLab(): SessionProofBinding {
        check(!closed) { "Closed Wi-Fi Direct QR expectation cannot be reused" }
        return SessionProofBinding(hostQrPin, joinQrPin, hostQrNonce, joinQrNonce)
    }

    @Synchronized
    override fun close() {
        if (!closed) {
            pin.fill(0)
            transcript.fill(0)
            hostQrPin.fill(0)
            joinQrPin.fill(0)
            hostQrNonce.fill(0)
            joinQrNonce.fill(0)
            closed = true
        }
    }

    companion object {
        /**
         * Host has validated the joiner's reply and minted a third QR.
         * Does NOT prove the joiner received/scanned that third QR.
         */
        fun host(
            host: DebugWifiDirectQrCodec.Host,
            reply: DebugWifiDirectQrCodec.Reply,
            ack: DebugWifiDirectQrCodec.Ack,
            ownPin: ByteArray,
            now: Long
        ): DebugWifiDirectFutureQrTlsExpectation {
            DebugWifiDirectQrCodec.checkFresh(host, now)
            require(MessageDigest.isEqual(host.pin, ownPin)) {
                "Host QR certificate does not match local public identity"
            }
            val parsedReply = DebugWifiDirectQrCodec.decodeReply(
                DebugWifiDirectQrCodec.encodeReply(reply), host, now
            )
            val expected = DebugWifiDirectQrCodec.makeAck(host, parsedReply, now)
            try {
                require(MessageDigest.isEqual(ack.hostNonce, expected.hostNonce) &&
                    MessageDigest.isEqual(ack.replyNonce, expected.replyNonce) &&
                    MessageDigest.isEqual(ack.transcript, expected.transcript)
                ) { "Host QR transcript mismatch" }
            } finally {
                parsedReply.clear()
                expected.clear()
            }
            return DebugWifiDirectFutureQrTlsExpectation(
                Role.HOST, reply.pin, digest(host, reply, ack),
                host.pin, reply.pin, host.nonce, reply.nonce
            )
        }

        /**
         * Joiner scanned and validated third QR against exact own public pin.
         * This remains public OOB correlation, not private-key possession.
         */
        fun join(
            host: DebugWifiDirectQrCodec.Host,
            reply: DebugWifiDirectQrCodec.Reply,
            ack: DebugWifiDirectQrCodec.Ack,
            ownPin: ByteArray,
            now: Long
        ): DebugWifiDirectFutureQrTlsExpectation {
            val parsed = DebugWifiDirectQrCodec.decodeAck(
                DebugWifiDirectQrCodec.encodeAck(ack), host, reply, ownPin, now
            )
            parsed.clear()
            return DebugWifiDirectFutureQrTlsExpectation(
                Role.JOIN, host.pin, digest(host, reply, ack),
                host.pin, reply.pin, host.nonce, reply.nonce
            )
        }

        private fun digest(
            host: DebugWifiDirectQrCodec.Host,
            reply: DebugWifiDirectQrCodec.Reply,
            ack: DebugWifiDirectQrCodec.Ack
        ): ByteArray {
            val hash = MessageDigest.getInstance("SHA-256")
            hash.update("eden-comms/wifi-direct/future-pinned-tls-lab/v1".toByteArray(
                StandardCharsets.US_ASCII
            ))
            for (entry in listOf(
                DebugWifiDirectQrCodec.encodeHost(host),
                DebugWifiDirectQrCodec.encodeReply(reply),
                DebugWifiDirectQrCodec.encodeAck(ack)
            )) {
                val bytes = entry.toByteArray(StandardCharsets.US_ASCII)
                hash.update((bytes.size ushr 8).toByte())
                hash.update(bytes.size.toByte())
                hash.update(bytes)
            }
            return hash.digest()
        }
    }
}
