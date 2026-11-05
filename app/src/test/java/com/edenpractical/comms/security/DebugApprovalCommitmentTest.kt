package com.edenpractical.comms.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugApprovalCommitmentTest {
    private fun qr(
        hostPin: Byte = 1, joinPin: Byte = 2,
        hostNonce: Byte = 3, joinNonce: Byte = 4
    ) = SessionProofBinding(
        ByteArray(32) { hostPin },
        ByteArray(32) { joinPin },
        ByteArray(16) { hostNonce },
        ByteArray(16) { joinNonce }
    )
    private fun proof(host: Byte = 5, join: Byte = 6) =
        SessionProofChallenges(ByteArray(16) { host }, ByteArray(16) { join })
    private fun exporter(value: Byte = 7) = ByteArray(32) { value }

    @Test fun identicalPresenterFirstEvidenceHasMatchingCommitment() {
        val a = DebugApprovalCommitment.derive(qr(), proof(), exporter())
        val b = DebugApprovalCommitment.derive(qr(), proof(), exporter())
        assertEquals32(a)
        assertArrayEquals(a, b)
        assertTrue(DebugApprovalCommitment.matches(a, b))
        assertFalse(com.edenpractical.comms.model.ReleaseCapabilities.CAN_SEND_MESSAGES)
    }

    @Test fun anyDifferentFingerprintNonceChallengeOrExporterChangesCommitment() {
        val original = DebugApprovalCommitment.derive(qr(), proof(), exporter())
        val others = listOf(
            DebugApprovalCommitment.derive(qr(hostPin = 8), proof(), exporter()),
            DebugApprovalCommitment.derive(qr(joinPin = 8), proof(), exporter()),
            DebugApprovalCommitment.derive(qr(hostNonce = 8), proof(), exporter()),
            DebugApprovalCommitment.derive(qr(joinNonce = 8), proof(), exporter()),
            DebugApprovalCommitment.derive(qr(), proof(host = 8), exporter()),
            DebugApprovalCommitment.derive(qr(), proof(join = 8), exporter()),
            DebugApprovalCommitment.derive(qr(), proof(), exporter(8))
        )
        others.forEach { changed ->
            assertFalse(DebugApprovalCommitment.matches(original, changed))
            assertFalse(original.contentEquals(changed))
        }
    }

    @Test fun swappedPresenterJoinerIdentityOrChallengesNeverMatch() {
        val original = DebugApprovalCommitment.derive(qr(), proof(), exporter())
        assertFalse(DebugApprovalCommitment.matches(
            original, DebugApprovalCommitment.derive(
                qr(hostPin = 2, joinPin = 1, hostNonce = 4, joinNonce = 3),
                proof(host = 6, join = 5), exporter()
            )
        ))
    }

    @Test fun malformedOrErasedEvidenceMustNotProduceAuthorization() {
        assertThrows(DebugApprovalCommitmentException::class.java) {
            DebugApprovalCommitment.derive(qr(), proof(), ByteArray(32))
        }
        assertThrows(DebugApprovalCommitmentException::class.java) {
            DebugApprovalCommitment.derive(qr(), proof(host = 5, join = 5), exporter())
        }
        assertThrows(DebugApprovalCommitmentException::class.java) {
            DebugApprovalCommitment.derive(qr(hostNonce = 3, joinNonce = 3), proof(), exporter())
        }
        assertThrows(DebugApprovalCommitmentException::class.java) {
            DebugApprovalCommitment.derive(qr(), proof(), ByteArray(31) { 7 })
        }
        assertFalse(DebugApprovalCommitment.matches(ByteArray(32), exporter()))
        assertFalse(DebugApprovalCommitment.matches(exporter(), ByteArray(31)))
        assertFalse(DebugApprovalCommitment.matches(exporter(), ByteArray(32)))
    }

    @Test fun clearedChallengesCannotBeReusedAcrossSessions() {
        val challenges = proof()
        val first = DebugApprovalCommitment.derive(qr(), challenges, exporter())
        challenges.clear()
        assertThrows(DebugApprovalCommitmentException::class.java) {
            DebugApprovalCommitment.derive(qr(), challenges, exporter())
        }
        assertEquals32(first)
    }

    private fun assertEquals32(value: ByteArray) {
        org.junit.Assert.assertEquals(32, value.size)
    }
}
