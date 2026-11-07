package com.edenpractical.comms.wifidirect

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * M3s-B: three QR frames for in-person EXPECTED-IDENTITY METADATA only.
 * Not proof of the private key, Android P2P radio target, TLS session,
 * recipient receipt, or bilateral authorization. No device MAC, IP, SSID,
 * group secret, account, message, or location in these payloads.
 */
internal object DebugWifiDirectQrCodec {
    private const val HOST_TAG = "ECWDH1"
    private const val JOIN_TAG = "ECWDJ1"
    private const val ACK_TAG = "ECWDA1"
    private const val PIN_SIZE = 32
    private const val NONCE_SIZE = 16
    private const val ACK_SIZE = 16
    private const val MAX_LENGTH = 170
    const val MAX_AGE_SECONDS = 180L
    private const val FUTURE_SKEW_SECONDS = 45L
    private val DIGITS = "0123456789ABCDEF"

    class Host(pin: ByteArray, nonce: ByteArray, val issuedAt: Long) {
        val pin = pin.copyOf()
        val nonce = nonce.copyOf()
        fun clear() { pin.fill(0); nonce.fill(0) }
    }
    class Reply(hostNonce: ByteArray, pin: ByteArray, nonce: ByteArray) {
        val hostNonce = hostNonce.copyOf()
        val pin = pin.copyOf()
        val nonce = nonce.copyOf()
        fun clear() { hostNonce.fill(0); pin.fill(0); nonce.fill(0) }
    }
    class Ack(hostNonce: ByteArray, replyNonce: ByteArray, transcript: ByteArray) {
        val hostNonce = hostNonce.copyOf()
        val replyNonce = replyNonce.copyOf()
        val transcript = transcript.copyOf()
        fun clear() { hostNonce.fill(0); replyNonce.fill(0); transcript.fill(0) }
    }

    fun newHost(pin: ByteArray, now: Long, random: SecureRandom = SecureRandom()): Host =
        Host(pin, freshNonce(random), now).also { checkHost(it) }

    fun encodeHost(host: Host): String {
        checkHost(host)
        return listOf(HOST_TAG, hex(host.pin), hex(host.nonce),
            host.issuedAt.toString()).joinToString("|")
    }

    fun decodeHost(value: String, now: Long): Host {
        val fields = split(value, HOST_TAG, 4)
        val time = parseTime(fields[3])
        return Host(unhex(fields[1], PIN_SIZE), unhex(fields[2], NONCE_SIZE),
            time).also { checkFresh(it, now) }
    }

    fun newReply(host: Host, ownPin: ByteArray, now: Long,
                 random: SecureRandom = SecureRandom()): Reply {
        checkFresh(host, now)
        checkPin(ownPin)
        require(!MessageDigest.isEqual(host.pin, ownPin)) { "Cannot use this phone as both roles" }
        var nonce = freshNonce(random)
        while (MessageDigest.isEqual(nonce, host.nonce)) nonce = freshNonce(random)
        return Reply(host.nonce, ownPin, nonce).also { checkReply(host, it) }
    }

    fun encodeReply(reply: Reply): String {
        checkPin(reply.pin)
        checkNonce(reply.hostNonce)
        checkNonce(reply.nonce)
        require(!MessageDigest.isEqual(reply.hostNonce, reply.nonce))
        return listOf(JOIN_TAG, hex(reply.hostNonce),
            hex(reply.pin), hex(reply.nonce)).joinToString("|")
    }

    fun decodeReply(value: String, host: Host, now: Long): Reply {
        checkFresh(host, now)
        val fields = split(value, JOIN_TAG, 4)
        return Reply(unhex(fields[1], NONCE_SIZE), unhex(fields[2], PIN_SIZE),
            unhex(fields[3], NONCE_SIZE)).also { checkReply(host, it) }
    }

    fun makeAck(host: Host, reply: Reply, now: Long): Ack {
        checkFresh(host, now)
        checkReply(host, reply)
        return Ack(host.nonce, reply.nonce, digest(host, reply).copyOfRange(0, ACK_SIZE))
    }

    fun encodeAck(ack: Ack): String {
        checkNonce(ack.hostNonce)
        checkNonce(ack.replyNonce)
        require(ack.transcript.size == ACK_SIZE)
        return listOf(ACK_TAG, hex(ack.hostNonce), hex(ack.replyNonce),
            hex(ack.transcript)).joinToString("|")
    }

    fun decodeAck(value: String, host: Host, reply: Reply, ownPin: ByteArray,
                  now: Long): Ack {
        checkFresh(host, now)
        checkReply(host, reply)
        checkPin(ownPin)
        require(MessageDigest.isEqual(reply.pin, ownPin)) {
            "Reply is not this phone's identity"
        }
        val fields = split(value, ACK_TAG, 4)
        return Ack(unhex(fields[1], NONCE_SIZE), unhex(fields[2], NONCE_SIZE),
            unhex(fields[3], ACK_SIZE)).also { ack ->
            require(MessageDigest.isEqual(ack.hostNonce, host.nonce) &&
                MessageDigest.isEqual(ack.replyNonce, reply.nonce) &&
                MessageDigest.isEqual(ack.transcript, digest(host, reply).copyOfRange(0, ACK_SIZE))) {
                "Unrelated or modified QR acknowledgement"
            }
        }
    }

    /**
     * Short visual transcript check. Same public transcript -> same code;
     * NOT private-key possession, channel binding or authenticated pairing.
     * Never offer a send/connect method based on this value.
     */
    fun comparisonCode(host: Host, reply: Reply, now: Long): String {
        checkFresh(host, now)
        checkReply(host, reply)
        val digest = digest(host, reply)
        return hex(digest.copyOfRange(16, 20)).let { "${it.take(4)}-${it.drop(4)}" }
    }

    fun checkFresh(host: Host, now: Long) {
        checkHost(host)
        require(now - host.issuedAt in -FUTURE_SKEW_SECONDS..MAX_AGE_SECONDS) {
            "QR preparation expired"
        }
    }

    private fun checkHost(host: Host) {
        checkPin(host.pin)
        checkNonce(host.nonce)
        require(host.issuedAt > 0 && host.issuedAt <= Long.MAX_VALUE - MAX_AGE_SECONDS)
    }

    private fun checkReply(host: Host, reply: Reply) {
        checkNonce(reply.hostNonce)
        checkPin(reply.pin)
        checkNonce(reply.nonce)
        require(MessageDigest.isEqual(reply.hostNonce, host.nonce))
        require(!MessageDigest.isEqual(host.pin, reply.pin))
        require(!MessageDigest.isEqual(reply.nonce, host.nonce))
    }

    private fun digest(host: Host, reply: Reply): ByteArray =
        MessageDigest.getInstance("SHA-256").apply {
            update("eden-comms/wifi-direct/qr-preparation/v1".toByteArray(StandardCharsets.US_ASCII))
            update(host.pin)
            update(reply.pin)
            update(host.nonce)
            update(reply.nonce)
            update(host.issuedAt.toString().toByteArray(StandardCharsets.US_ASCII))
        }.digest()

    private fun freshNonce(random: SecureRandom): ByteArray {
        val out = ByteArray(NONCE_SIZE)
        do { random.nextBytes(out) } while (out.all { it == 0.toByte() })
        return out
    }

    private fun checkPin(pin: ByteArray) =
        require(pin.size == PIN_SIZE && pin.any { it != 0.toByte() })

    private fun checkNonce(nonce: ByteArray) =
        require(nonce.size == NONCE_SIZE && nonce.any { it != 0.toByte() })

    private fun split(value: String, tag: String, expected: Int): List<String> {
        require(value.length in 1..MAX_LENGTH && value.all { it.code in 33..126 })
        return value.split('|').also { require(it.size == expected && it[0] == tag) }
    }

    private fun parseTime(value: String): Long {
        require(value.isNotEmpty() && value.all { it in '0'..'9' } &&
            (value.length == 1 || value[0] != '0'))
        return value.toLongOrNull() ?: throw IllegalArgumentException("Bad timestamp")
    }

    private fun hex(bytes: ByteArray): String = buildString(bytes.size * 2) {
        for (b in bytes) {
            val v = b.toInt() and 255
            append(DIGITS[v ushr 4])
            append(DIGITS[v and 15])
        }
    }

    private fun unhex(value: String, size: Int): ByteArray {
        require(value.length == size * 2)
        return ByteArray(size) { index ->
            val left = DIGITS.indexOf(value[index * 2])
            val right = DIGITS.indexOf(value[index * 2 + 1])
            require(left >= 0 && right >= 0) { "Invalid canonical hexadecimal" }
            ((left shl 4) or right).toByte()
        }
    }
}
