package com.edenpractical.comms.pairing

import com.edenpractical.comms.transport.LocalIpv4Policy
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Offline, two-QR exchange for a one-shot mutual TLS test.
 *
 * QR 1: the host's local IPv4 address, exact SHA-256 leaf certificate pin,
 *        a fresh 128-bit session nonce and creation time.
 * QR 2: joiner's exact certificate pin and a fresh nonce, echoing host nonce.
 *
 * Neither QR contains a private key, name, phone number, account, location,
 * history or an Internet address. QR exchange is local, ephemeral and must
 * happen in person. Both devices MUST pin certificates before a TLS probe.
 *
 * These invitations are *not* a cryptographic handshake or session
 * authorization by themselves. Message transmission remains disabled.
 */
internal object LocalPairingQrCodec {
    private const val HOST_TAG = "EC2H"
    private const val REPLY_TAG = "EC2J"
    private const val FINGERPRINT_SIZE = 32
    private const val NONCE_SIZE = 16
    private const val MAX_QR_LENGTH = 170
    const val MAX_AGE_SECONDS = 300L
    private const val ALLOWED_FUTURE_SKEW_SECONDS = 45L
    private val HEX = "0123456789ABCDEF".toCharArray()

    data class HostInvite(
        val wifiIp: String,
        val certificateSha256: ByteArray,
        val nonce: ByteArray,
        val issuedAtSeconds: Long
    )

    data class JoinReply(
        val hostNonce: ByteArray,
        val certificateSha256: ByteArray,
        val nonce: ByteArray
    )

    fun newHostInvite(
        localIpv4: String,
        ownFingerprint: ByteArray,
        nowSeconds: Long = System.currentTimeMillis() / 1000,
        random: SecureRandom = SecureRandom()
    ): HostInvite {
        val nonce = ByteArray(NONCE_SIZE).also(random::nextBytes)
        return HostInvite(localIpv4, ownFingerprint.copyOf(), nonce, nowSeconds)
            .also(::validateHostFields)
    }

    fun encodeHost(invite: HostInvite): String {
        validateHostFields(invite)
        return listOf(
            HOST_TAG,
            invite.wifiIp,
            encodeHex(invite.certificateSha256),
            encodeHex(invite.nonce),
            invite.issuedAtSeconds.toString()
        ).joinToString("|")
    }

    fun decodeHost(payload: String, nowSeconds: Long = System.currentTimeMillis() / 1000): HostInvite {
        val segments = splitStrict(payload, HOST_TAG, 5)
        val ip = segments[1]
        val fingerprint = decodeHex(segments[2], FINGERPRINT_SIZE)
        val nonce = decodeHex(segments[3], NONCE_SIZE)
        val issuedText = segments[4]
        require(issuedText.isNotEmpty() && issuedText.all(Char::isDigit) &&
            (issuedText.length == 1 || issuedText[0] != '0')) { "Noncanonical time" }
        val issued = issuedText.toLongOrNull() ?: throw IllegalArgumentException("Invalid time")
        val invite = HostInvite(ip, fingerprint, nonce, issued)
        validateHostFields(invite)
        requireFresh(invite, nowSeconds)
        return invite
    }

    fun newJoinReply(
        host: HostInvite,
        localFingerprint: ByteArray,
        nowSeconds: Long = System.currentTimeMillis() / 1000,
        random: SecureRandom = SecureRandom()
    ): JoinReply {
        requireFresh(host, nowSeconds)
        require(localFingerprint.size == FINGERPRINT_SIZE) { "Peer fingerprint must be complete" }
        require(!MessageDigest.isEqual(host.certificateSha256, localFingerprint)) {
            "Cannot pair a device with itself"
        }
        return JoinReply(host.nonce.copyOf(), localFingerprint.copyOf(),
            ByteArray(NONCE_SIZE).also(random::nextBytes))
    }

    fun encodeJoin(reply: JoinReply): String {
        validateReplyFields(reply)
        return listOf(
            REPLY_TAG, encodeHex(reply.hostNonce),
            encodeHex(reply.certificateSha256), encodeHex(reply.nonce)
        ).joinToString("|")
    }

    fun decodeJoin(
        payload: String,
        expectedHost: HostInvite,
        nowSeconds: Long = System.currentTimeMillis() / 1000
    ): JoinReply {
        requireFresh(expectedHost, nowSeconds)
        val segments = splitStrict(payload, REPLY_TAG, 4)
        val reply = JoinReply(
            decodeHex(segments[1], NONCE_SIZE),
            decodeHex(segments[2], FINGERPRINT_SIZE),
            decodeHex(segments[3], NONCE_SIZE)
        )
        validateReplyFields(reply)
        require(MessageDigest.isEqual(reply.hostNonce, expectedHost.nonce)) {
            "Reply is for a different or expired pairing session"
        }
        require(!MessageDigest.isEqual(reply.certificateSha256, expectedHost.certificateSha256)) {
            "Cannot pair with this phone"
        }
        return reply
    }

    fun requireFresh(host: HostInvite, nowSeconds: Long) {
        val age = nowSeconds - host.issuedAtSeconds
        require(age in -ALLOWED_FUTURE_SKEW_SECONDS..MAX_AGE_SECONDS) {
            "Pairing QR expired. Start a new connection."
        }
    }

    private fun validateHostFields(invite: HostInvite) {
        val address = LocalIpv4Policy.parseNumeric(invite.wifiIp)
        require(address.isSiteLocalAddress || address.isLinkLocalAddress) {
            "QR must contain a local Wi-Fi IP address"
        }
        require(invite.certificateSha256.size == FINGERPRINT_SIZE &&
            invite.nonce.size == NONCE_SIZE && invite.issuedAtSeconds > 0) {
            "Invalid pairing identity or session nonce"
        }
    }

    private fun validateReplyFields(reply: JoinReply) {
        require(reply.hostNonce.size == NONCE_SIZE &&
            reply.certificateSha256.size == FINGERPRINT_SIZE &&
            reply.nonce.size == NONCE_SIZE) { "Invalid reply identity or nonce" }
    }

    private fun splitStrict(text: String, tag: String, expectedCount: Int): List<String> {
        require(text.length in 1..MAX_QR_LENGTH && text.all { it.code in 33..126 }) {
            "Invalid pairing QR length or characters"
        }
        val segments = text.split('|')
        require(segments.size == expectedCount && segments[0] == tag) {
            "Unsupported pairing QR format"
        }
        return segments
    }

    private fun encodeHex(bytes: ByteArray): String = buildString(bytes.size * 2) {
        for (byte in bytes) {
            val v = byte.toInt() and 255
            append(HEX[v ushr 4])
            append(HEX[v and 15])
        }
    }

    private fun decodeHex(s: String, expectedBytes: Int): ByteArray {
        require(s.length == expectedBytes * 2) { "Wrong fingerprint or nonce length" }
        return ByteArray(expectedBytes) { i ->
            val hi = parseNibble(s[i * 2])
            val lo = parseNibble(s[i * 2 + 1])
            ((hi shl 4) or lo).toByte()
        }
    }

    private fun parseNibble(c: Char): Int = when (c) {
        in '0'..'9' -> c.code - '0'.code
        in 'A'..'F' -> c.code - 'A'.code + 10
        else -> throw IllegalArgumentException("Only uppercase hex is accepted")
    }
}
