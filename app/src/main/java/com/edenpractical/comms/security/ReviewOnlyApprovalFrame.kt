package com.edenpractical.comms.security

import java.io.IOException
import java.security.MessageDigest

/**
 * REVIEW-ONLY wire-envelope proposal for future authenticated user approval.
 *
 * This class contains no networking, cryptography or approval authority.
 * Accepting an APPROVE packet does NOT establish a verified TLS session,
 * prove it came from the intended peer, or enable any release capability.
 *
 * A reviewed trusted-channel adapter MUST independently verify exact peer
 * certificate, fresh two-QR session and TLS channel binding BEFORE it may
 * interpret a packet; the user's intent must also be established independently.
 *
 * Version 1 draft format (39 bytes):
 *   ASCII "ECAP" (4), version (1), decision (1), sender role (1),
 *   channel+QR transcript commitment (32).
 *
 * Protocol and message ordering require external security review before use.
 * DO NOT wire this to LanDemoSession or a live socket.
 */
internal object ReviewOnlyApprovalFrame {
    private val MAGIC = byteArrayOf(0x45, 0x43, 0x41, 0x50) // ECAP
    private const val VERSION = 1
    private const val COMMITMENT_BYTES = 32
    const val WIRE_BYTES = 4 + 1 + 1 + 1 + COMMITMENT_BYTES

    enum class Decision(val wire: Int) { APPROVE(1), DECLINE(2) }
    enum class Role(val wire: Int) { PRESENTER(1), JOINER(2) }

    class Packet internal constructor(
        val decision: Decision,
        val role: Role,
        commitment: ByteArray
    ) {
        private val copy = commitment.copyOf()

        val commitment: ByteArray get() = copy.copyOf()

        fun matchesExpected(
            expectedRemoteRole: Role,
            expectedChannelCommitment: ByteArray
        ): Boolean = role == expectedRemoteRole &&
            expectedChannelCommitment.size == COMMITMENT_BYTES &&
            MessageDigest.isEqual(copy, expectedChannelCommitment)
    }

    fun packet(
        decision: Decision,
        role: Role,
        channelCommitment: ByteArray
    ): Packet {
        if (channelCommitment.size != COMMITMENT_BYTES ||
            channelCommitment.all { it == 0.toByte() }
        ) throw InvalidApprovalFrame()
        return Packet(decision, role, channelCommitment)
    }

    fun encode(packet: Packet): ByteArray =
        MAGIC + byteArrayOf(
            VERSION.toByte(), packet.decision.wire.toByte(), packet.role.wire.toByte()
        ) + packet.commitment

    fun decode(raw: ByteArray): Packet {
        if (raw.size != WIRE_BYTES ||
            !MessageDigest.isEqual(raw.copyOfRange(0, MAGIC.size), MAGIC) ||
            (raw[4].toInt() and 0xFF) != VERSION
        ) throw InvalidApprovalFrame()

        val decision = Decision.entries.firstOrNull {
            it.wire == (raw[5].toInt() and 0xFF)
        } ?: throw InvalidApprovalFrame()
        val role = Role.entries.firstOrNull {
            it.wire == (raw[6].toInt() and 0xFF)
        } ?: throw InvalidApprovalFrame()

        return packet(decision, role, raw.copyOfRange(7, WIRE_BYTES))
    }
}

internal class InvalidApprovalFrame :
    IOException("Unrecognized review-only approval frame")
