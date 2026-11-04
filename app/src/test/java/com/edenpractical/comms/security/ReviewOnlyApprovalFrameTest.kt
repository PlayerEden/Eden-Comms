package com.edenpractical.comms.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewOnlyApprovalFrameTest {
    private val frame = ReviewOnlyApprovalFrame
    private val sessionA = ByteArray(32) { 0x12.toByte() }
    private val sessionB = ByteArray(32) { 0x21.toByte() }

    @Test fun allDecisionsAndRolesHaveExactBoundedCanonicalEncoding() {
        ReviewOnlyApprovalFrame.Decision.entries.forEach { decision ->
            ReviewOnlyApprovalFrame.Role.entries.forEach { role ->
                val written = frame.encode(frame.packet(decision, role, sessionA))
                assertEquals(frame.WIRE_BYTES, written.size)
                val parsed = frame.decode(written)
                assertEquals(decision, parsed.decision)
                assertEquals(role, parsed.role)
                assertArrayEquals(sessionA, parsed.commitment)
                assertArrayEquals(written, frame.encode(parsed))
            }
        }
    }

    @Test fun invalidTypeVersionMagicLengthAndAllZeroCommitmentFailClosed() {
        val valid = frame.encode(frame.packet(ReviewOnlyApprovalFrame.Decision.APPROVE, ReviewOnlyApprovalFrame.Role.PRESENTER, sessionA))
        val variants = listOf(
            byteArrayOf(), valid.copyOf(valid.size - 1), valid + byteArrayOf(0),
            valid.copyOf().apply { this[0] = 0 },
            valid.copyOf().apply { this[4] = 2 },
            valid.copyOf().apply { this[5] = 99.toByte() },
            valid.copyOf().apply { this[6] = 99.toByte() },
            valid.copyOf().apply { fill(0, 7, size) }
        )
        variants.forEach { invalid ->
            assertThrows(InvalidApprovalFrame::class.java) { frame.decode(invalid) }
        }
        assertThrows(InvalidApprovalFrame::class.java) {
            frame.packet(ReviewOnlyApprovalFrame.Decision.APPROVE, ReviewOnlyApprovalFrame.Role.PRESENTER, ByteArray(16))
        }
    }

    @Test fun senderRoleAndChannelTranscriptMustBothMatch() {
        val packet = frame.packet(ReviewOnlyApprovalFrame.Decision.APPROVE, ReviewOnlyApprovalFrame.Role.JOINER, sessionA)
        assertTrue(packet.matchesExpectedApprovalProposal(ReviewOnlyApprovalFrame.Role.JOINER, sessionA))
        assertFalse(packet.matchesExpectedApprovalProposal(ReviewOnlyApprovalFrame.Role.PRESENTER, sessionA))
        assertFalse(packet.matchesExpectedApprovalProposal(ReviewOnlyApprovalFrame.Role.JOINER, sessionB))
        assertFalse(packet.matchesExpectedApprovalProposal(ReviewOnlyApprovalFrame.Role.JOINER, ByteArray(1)))
    }

    @Test fun inputAndReturnedCommitmentArraysCannotModifyPacket() {
        val userInput = sessionA.copyOf()
        val packet = frame.packet(ReviewOnlyApprovalFrame.Decision.APPROVE, ReviewOnlyApprovalFrame.Role.PRESENTER, userInput)
        userInput.fill(0)
        val returned = packet.commitment
        returned.fill(0)
        assertTrue(packet.matchesExpectedApprovalProposal(ReviewOnlyApprovalFrame.Role.PRESENTER, sessionA))
        assertArrayEquals(sessionA, frame.decode(frame.encode(packet)).commitment)
    }

    @Test fun declineIsDistinctFromApprovalAndCanNeverBeTreatedAsConsent() {
        val decline = frame.packet(ReviewOnlyApprovalFrame.Decision.DECLINE, ReviewOnlyApprovalFrame.Role.JOINER, sessionA)
        assertEquals(ReviewOnlyApprovalFrame.Decision.DECLINE, frame.decode(frame.encode(decline)).decision)
        assertFalse(frame.decode(frame.encode(decline)).decision == ReviewOnlyApprovalFrame.Decision.APPROVE)
        assertFalse(decline.matchesExpectedApprovalProposal(ReviewOnlyApprovalFrame.Role.JOINER, sessionA))
    }
}
