package com.edenpractical.comms.bluetooth

import com.edenpractical.comms.security.SessionProofBinding
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Fresh in-person QR METADATA for a future Bluetooth-authenticated session.
 *
 * These public QR fields identify expected certificate pins and nonce roles,
 * NOT a cryptographically verified peer, link key, trusted contact, or approval.
 * The RFCOMM test link must still run separately reviewed mutual authentication
 * and channel-specific binding BEFORE any application payload can be enabled.
 *
 * No IP/network address, Bluetooth MAC/name, contact, message or private key.
 * This code runs only in the src/debug source set.
 */
internal object BluetoothQrPairingCodec {
    private const val HOST_TAG = "ECBH1"
    private const val JOIN_TAG = "ECBJ1"
    private const val PIN_BYTES = 32
    private const val NONCE_BYTES = 16
    private const val MAX_QR_LENGTH = 170
    const val MAX_AGE_SECONDS = 300L
    private const val MAX_FUTURE_SKEW_SECONDS = 45L
    private val HEX = "0123456789ABCDEF".toCharArray()

    class HostInvite(pin: ByteArray, nonce: ByteArray, val issuedAtSeconds: Long) {
        val pin = pin.copyOf()
        val nonce = nonce.copyOf()
    }

    class JoinReply(hostNonce: ByteArray, pin: ByteArray, nonce: ByteArray) {
        val hostNonce = hostNonce.copyOf()
        val pin = pin.copyOf()
        val nonce = nonce.copyOf()
    }

    class Prepared internal constructor(
        private val hostPin: ByteArray,
        private val joinPin: ByteArray,
        private val hostNonce: ByteArray,
        private val joinNonce: ByteArray,
        val hostRole: Boolean,
        val expiresAtSeconds: Long
    ) {
        private var cleared = false

        /** Canonical presenter/joiner QR transcript; NOT proof or authorization. */
        fun binding(nowSeconds: Long): SessionProofBinding {
            requireFreshPrepared(nowSeconds)
            return SessionProofBinding(hostPin, joinPin, hostNonce, joinNonce)
        }

        fun peerFingerprint(nowSeconds: Long): ByteArray {
            requireFreshPrepared(nowSeconds)
            return (if (hostRole) joinPin else hostPin).copyOf()
        }

        private fun requireFreshPrepared(nowSeconds: Long) {
            require(!cleared &&
                nowSeconds in
                    (expiresAtSeconds - MAX_AGE_SECONDS - MAX_FUTURE_SKEW_SECONDS)..expiresAtSeconds
            ) { "Bluetooth QR preparation expired or clock invalid" }
        }

        fun clear() {
            hostPin.fill(0)
            joinPin.fill(0)
            hostNonce.fill(0)
            joinNonce.fill(0)
            cleared = true
        }
    }

    fun freshHost(
        localPin: ByteArray,
        nowSeconds: Long,
        random: SecureRandom = SecureRandom()
    ): HostInvite =
        HostInvite(localPin, randomNonce(random), nowSeconds).also(::requireValidHost)

    fun encodeHost(invite: HostInvite): String {
        requireValidHost(invite)
        return listOf(HOST_TAG, hex(invite.pin), hex(invite.nonce),
            invite.issuedAtSeconds.toString()).joinToString("|")
    }

    fun decodeHost(text: String, nowSeconds: Long): HostInvite {
        val fields = split(text, HOST_TAG, 4)
        val issued = parseTime(fields[3])
        return HostInvite(
            unhex(fields[1], PIN_BYTES), unhex(fields[2], NONCE_BYTES), issued
        ).also { requireValidHost(it); requireFresh(it, nowSeconds) }
    }

    fun freshReply(
        host: HostInvite,
        localPin: ByteArray,
        nowSeconds: Long,
        random: SecureRandom = SecureRandom()
    ): JoinReply {
        requireFresh(host, nowSeconds)
        requireValidPin(localPin)
        require(!MessageDigest.isEqual(host.pin, localPin)) { "Cannot pair with this phone" }
        return JoinReply(host.nonce, localPin, randomNonce(random)).also(::requireValidReply)
    }

    fun encodeReply(reply: JoinReply): String {
        requireValidReply(reply)
        return listOf(JOIN_TAG, hex(reply.hostNonce), hex(reply.pin),
            hex(reply.nonce)).joinToString("|")
    }

    fun decodeReply(text: String, host: HostInvite, nowSeconds: Long): JoinReply {
        requireFresh(host, nowSeconds)
        val fields = split(text, JOIN_TAG, 4)
        val reply = JoinReply(
            unhex(fields[1], NONCE_BYTES), unhex(fields[2], PIN_BYTES),
            unhex(fields[3], NONCE_BYTES)
        )
        requireValidReply(reply)
        require(MessageDigest.isEqual(reply.hostNonce, host.nonce)) {
            "Reply belongs to a different Bluetooth QR attempt"
        }
        require(!MessageDigest.isEqual(reply.pin, host.pin)) {
            "Cannot pair with this phone"
        }
        return reply
    }

    fun preparedHost(
        host: HostInvite, reply: JoinReply, nowSeconds: Long
    ): Prepared {
        requireFresh(host, nowSeconds)
        requireValidReply(reply)
        require(MessageDigest.isEqual(host.nonce, reply.hostNonce)) { "Wrong Bluetooth QR reply" }
        require(!MessageDigest.isEqual(host.pin, reply.pin)) { "Same device" }
        return Prepared(
            host.pin.copyOf(), reply.pin.copyOf(),
            host.nonce.copyOf(), reply.nonce.copyOf(),
            hostRole = true, expiresAtSeconds = host.issuedAtSeconds + MAX_AGE_SECONDS
        )
    }

    fun preparedJoin(
        host: HostInvite, reply: JoinReply, ownPin: ByteArray, nowSeconds: Long
    ): Prepared {
        requireFresh(host, nowSeconds)
        requireValidReply(reply)
        requireValidPin(ownPin)
        require(MessageDigest.isEqual(reply.pin, ownPin)) { "Reply is not this phone's identity" }
        require(MessageDigest.isEqual(host.nonce, reply.hostNonce)) { "Wrong Bluetooth QR host" }
        require(!MessageDigest.isEqual(host.pin, ownPin)) { "Same device" }
        return Prepared(
            host.pin.copyOf(), ownPin.copyOf(),
            host.nonce.copyOf(), reply.nonce.copyOf(),
            hostRole = false, expiresAtSeconds = host.issuedAtSeconds + MAX_AGE_SECONDS
        )
    }

    fun requireFresh(host: HostInvite, nowSeconds: Long) {
        requireValidHost(host)
        val age = nowSeconds - host.issuedAtSeconds
        require(age in -MAX_FUTURE_SKEW_SECONDS..MAX_AGE_SECONDS) {
            "Bluetooth QR expired; begin fresh in-person pairing"
        }
    }

    private fun requireValidHost(host: HostInvite) {
        requireValidPin(host.pin)
        requireValidNonce(host.nonce)
        require(host.issuedAtSeconds > 0L &&
            host.issuedAtSeconds <= Long.MAX_VALUE - MAX_AGE_SECONDS) {
            "Invalid Bluetooth QR timestamp"
        }
    }

    private fun requireValidReply(reply: JoinReply) {
        requireValidNonce(reply.hostNonce)
        requireValidPin(reply.pin)
        requireValidNonce(reply.nonce)
        require(!MessageDigest.isEqual(reply.hostNonce, reply.nonce)) {
            "QR challenge must differ from host nonce"
        }
    }

    private fun requireValidPin(pin: ByteArray) {
        require(pin.size == PIN_BYTES && pin.any { it != 0.toByte() }) {
            "Full nonzero certificate pin required"
        }
    }

    private fun requireValidNonce(nonce: ByteArray) {
        require(nonce.size == NONCE_BYTES && nonce.any { it != 0.toByte() }) {
            "Full nonzero nonce required"
        }
    }

    private fun randomNonce(random: SecureRandom): ByteArray {
        val nonce = ByteArray(NONCE_BYTES)
        do { random.nextBytes(nonce) } while (nonce.all { it == 0.toByte() })
        return nonce
    }

    private fun split(text: String, tag: String, size: Int): List<String> {
        require(text.length in 1..MAX_QR_LENGTH && text.all { it.code in 33..126 }) {
            "Invalid Bluetooth pairing QR format"
        }
        val fields = text.split('|')
        require(fields.size == size && fields[0] == tag) { "Wrong Bluetooth QR type or version" }
        return fields
    }

    private fun parseTime(text: String): Long {
        require(text.isNotEmpty() && text.all { it in '0'..'9' } &&
            (text.length == 1 || text.first() != '0')) { "Noncanonical QR timestamp" }
        return text.toLongOrNull() ?: throw IllegalArgumentException("Invalid QR timestamp")
    }

    private fun hex(data: ByteArray): String = buildString(data.size * 2) {
        for (byte in data) {
            val value = byte.toInt() and 255
            append(HEX[value shr 4])
            append(HEX[value and 15])
        }
    }

    private fun unhex(value: String, size: Int): ByteArray {
        require(value.length == 2 * size) { "Wrong Bluetooth QR field length" }
        return ByteArray(size) { index ->
            val high = nibble(value[index * 2])
            val low = nibble(value[index * 2 + 1])
            ((high shl 4) or low).toByte()
        }
    }

    private fun nibble(c: Char): Int = when (c) {
        in '0'..'9' -> c.code - '0'.code
        in 'A'..'F' -> c.code - 'A'.code + 10
        else -> throw IllegalArgumentException("Only uppercase hex allowed")
    }
}
