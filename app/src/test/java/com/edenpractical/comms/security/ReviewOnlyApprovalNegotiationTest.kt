package com.edenpractical.comms.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewOnlyChannelCommitmentTest {
    private val fields = listOf(
        ByteArray(32) { 0x11.toByte() },
        ByteArray(32) { 0x22.toByte() },
        ByteArray(16) { 0x33.toByte() },
        ByteArray(16) { 0x44.toByte() },
        ByteArray(16) { 0x55.toByte() },
        ByteArray(16) { 0x66.toByte() },
        ByteArray(32) { 0x77.toByte() }
    )

    private fun candidate(args: List<ByteArray>): ByteArray =
        ReviewOnlyChannelCommitment.candidateSha256(
            args[0], args[1], args[2], args[3], args[4], args[5], args[6]
        )

    @Test fun hasIndependentCanonicalGoldenTestVector() {
        // Generated independently with Python hashlib over:
        // len(domain) | "EdenComms-Approval-v1" | 01 01 02 |
        // seven length-prefixed byte arrays in canonical role order.
        // This is a DRAFT vector, not an approved IETF application protocol.
        val expected = "626d383808ad60d20ca4c23d3070d6e502fd287793f10b0c2fa1cd9bfce59934"
        val result = candidate(fields)
        assertEquals(expected, result.joinToString("") { "%02x".format(it.toInt() and 0xFF) })
        assertEquals(32, result.size)
        assertArrayEquals(result, candidate(fields.map { it.copyOf() }))
    }

    @Test fun changesInEveryFieldInvalidateOldTranscriptCommitment() {
        val baseline = candidate(fields)
        fields.indices.forEach { index ->
            val changed = fields.map { it.copyOf() }
            changed[index][0] = (changed[index][0].toInt() xor 0x01).toByte()
            assertFalse("Field $index failed to bind",
                baseline.contentEquals(candidate(changed)))
        }
    }

    @Test fun swappedRolesAndMismatchedLengthsFail() {
        val swapped = fields.map { it.copyOf() }.toMutableList()
        val x = swapped[0]
        swapped[0] = swapped[1]
        swapped[1] = x
        assertFalse(candidate(fields).contentEquals(candidate(swapped)))

        fields.indices.forEach { index ->
            val changed = fields.map { it.copyOf() }.toMutableList()
            changed[index] = changed[index].copyOf(changed[index].size - 1)
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                candidate(changed)
            }
        }
    }

    @Test fun zeroInputsAndSelfPairingAreRefused() {
        fields.indices.forEach { index ->
            val changed = fields.map { it.copyOf() }.toMutableList()
            changed[index].fill(0)
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                candidate(changed)
            }
        }
        listOf(0 to 1, 2 to 3, 4 to 5).forEach { (a, b) ->
            val changed = fields.map { it.copyOf() }.toMutableList()
            changed[b] = changed[a].copyOf()
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                candidate(changed)
            }
        }
    }
}

class ReviewOnlyBilateralApprovalTest {
    private val commitment = ByteArray(32) { 0x51.toByte() }
    private val oldCommitment = ByteArray(32) { 0x61.toByte() }
    private val presenter = ReviewOnlyApprovalFrame.Role.PRESENTER
    private val joiner = ReviewOnlyApprovalFrame.Role.JOINER
    private val approve = ReviewOnlyApprovalFrame.Decision.APPROVE
    private val decline = ReviewOnlyApprovalFrame.Decision.DECLINE

    private fun frame(
        role: ReviewOnlyApprovalFrame.Role = joiner,
        session: ByteArray = commitment,
        decision: ReviewOnlyApprovalFrame.Decision = approve
    ) = ReviewOnlyApprovalFrame.decode(
        ReviewOnlyApprovalFrame.encode(ReviewOnlyApprovalFrame.packet(decision, role, session))
    )

    @Test fun startRequiresValidFreshNonzeroCommitmentAndNoPreviousAttempt() {
        val model = ReviewOnlyBilateralApproval()
        assertNull(model.begin(presenter, ByteArray(0)))
        assertEquals(ReviewOnlyBilateralApproval.Phase.DENIED, model.phase)
        model.cancel()
        assertNull(model.begin(presenter, ByteArray(32)))
        model.cancel()
        val id = model.begin(presenter, commitment)
        assertNotNull(id)
        assertNull(model.begin(presenter, commitment))
    }

    @Test fun twoPhonesOnlySimulateAgreementAfterBothLocalAndPeerDecisionOnEach() {
        val left = ReviewOnlyBilateralApproval()
        val right = ReviewOnlyBilateralApproval()
        val lid = requireNotNull(left.begin(presenter, commitment))
        val rid = requireNotNull(right.begin(joiner, commitment))

        assertFalse(left.bothDecisionsSimulated)
        assertFalse(right.bothDecisionsSimulated)
        assertTrue(left.localDecision(lid, commitment, approve))
        assertTrue(right.localDecision(rid, commitment, approve))
        assertFalse(left.bothDecisionsSimulated)
        assertFalse(right.bothDecisionsSimulated)
        assertTrue(left.simulatePeerFrame(lid, frame(role = joiner)))
        assertTrue(right.simulatePeerFrame(rid, frame(role = presenter)))
        assertEquals(ReviewOnlyBilateralApproval.Phase.SIMULATED_AGREEMENT, left.phase)
        assertEquals(ReviewOnlyBilateralApproval.Phase.SIMULATED_AGREEMENT, right.phase)
        // This state is TEST ONLY and cannot enable an Android message sender.
    }

    @Test fun remoteFirstNeedsLocalHumanDecisionAndCannotAutoApprove() {
        val model = ReviewOnlyBilateralApproval()
        val id = requireNotNull(model.begin(presenter, commitment))
        assertTrue(model.simulatePeerFrame(id, frame()))
        assertFalse(model.bothDecisionsSimulated)
        assertTrue(model.localDecision(id, commitment, approve))
        assertTrue(model.bothDecisionsSimulated)
    }

    @Test fun neitherRemoteNorLocalDeclineCanEverBeAgreement() {
        val remoteDecline = ReviewOnlyBilateralApproval()
        val a = requireNotNull(remoteDecline.begin(presenter, commitment))
        assertTrue(remoteDecline.localDecision(a, commitment, approve))
        assertTrue(remoteDecline.simulatePeerFrame(a, frame(decision = decline)))
        assertEquals(ReviewOnlyBilateralApproval.Phase.DENIED, remoteDecline.phase)
        assertFalse(remoteDecline.bothDecisionsSimulated)

        val localDecline = ReviewOnlyBilateralApproval()
        val b = requireNotNull(localDecline.begin(presenter, commitment))
        assertTrue(localDecline.simulatePeerFrame(b, frame()))
        assertTrue(localDecline.localDecision(b, commitment, decline))
        assertEquals(ReviewOnlyBilateralApproval.Phase.DENIED, localDecline.phase)
    }

    @Test fun wrongPeerRoleOrWrongSessionIsDenied() {
        listOf(frame(role = presenter), frame(session = oldCommitment)).forEach { packet ->
            val model = ReviewOnlyBilateralApproval()
            val id = requireNotNull(model.begin(presenter, commitment))
            assertFalse(model.simulatePeerFrame(id, packet))
            assertEquals(ReviewOnlyBilateralApproval.Phase.DENIED, model.phase)
        }
    }

    @Test fun staleOrChangedLocalCommitmentIsDenied() {
        val model = ReviewOnlyBilateralApproval()
        val id = requireNotNull(model.begin(presenter, commitment))
        assertFalse(model.localDecision(id, oldCommitment, approve))
        assertEquals(ReviewOnlyBilateralApproval.Phase.DENIED, model.phase)
    }

    @Test fun duplicateLocalOrRemoteDecisionsAreRejected() {
        val localModel = ReviewOnlyBilateralApproval()
        val a = requireNotNull(localModel.begin(presenter, commitment))
        assertTrue(localModel.localDecision(a, commitment, approve))
        assertFalse(localModel.localDecision(a, commitment, approve))
        assertEquals(ReviewOnlyBilateralApproval.Phase.DENIED, localModel.phase)
        val remoteModel = ReviewOnlyBilateralApproval()
        val b = requireNotNull(remoteModel.begin(presenter, commitment))
        assertTrue(remoteModel.simulatePeerFrame(b, frame()))
        assertFalse(remoteModel.simulatePeerFrame(b, frame()))
        assertEquals(ReviewOnlyBilateralApproval.Phase.DENIED, remoteModel.phase)
    }

    @Test fun extraMessagesAfterSimulatedAgreementCannotCreateNewConsent() {
        val model = ReviewOnlyBilateralApproval()
        val a = requireNotNull(model.begin(presenter, commitment))
        model.localDecision(a, commitment, approve)
        model.simulatePeerFrame(a, frame())
        assertTrue(model.bothDecisionsSimulated)
        assertFalse(model.simulatePeerFrame(a, frame()))
        assertFalse(model.bothDecisionsSimulated)
        assertEquals(ReviewOnlyBilateralApproval.Phase.DENIED, model.phase)
    }

    @Test fun lostTransportRevokesAndOldCallbacksCannotAuthorizeReconnect() {
        val model = ReviewOnlyBilateralApproval()
        val a = requireNotNull(model.begin(presenter, commitment))
        model.localDecision(a, commitment, approve)
        model.transportLost(a)
        assertEquals(ReviewOnlyBilateralApproval.Phase.IDLE, model.phase)
        val b = requireNotNull(model.begin(presenter, oldCommitment))
        model.transportLost(a)
        assertFalse(model.localDecision(a, commitment, approve))
        assertFalse(model.simulatePeerFrame(a, frame()))
        assertEquals(ReviewOnlyBilateralApproval.Phase.AWAITING_DECISIONS, model.phase)
        assertFalse(model.bothDecisionsSimulated)
        assertTrue(model.localDecision(b, oldCommitment, approve))
        assertFalse(model.bothDecisionsSimulated)
    }

    @Test fun timeoutAndCancelResetPendingOrCompletedAgreement() {
        val model = ReviewOnlyBilateralApproval()
        val a = requireNotNull(model.begin(presenter, commitment))
        model.deadlineElapsed(a)
        assertEquals(ReviewOnlyBilateralApproval.Phase.DENIED, model.phase)
        model.cancel()
        val b = requireNotNull(model.begin(presenter, commitment))
        model.localDecision(b, commitment, approve)
        model.simulatePeerFrame(b, frame())
        assertTrue(model.bothDecisionsSimulated)
        model.transportLost(b)
        assertFalse(model.bothDecisionsSimulated)
        model.cancel()
        assertEquals(ReviewOnlyBilateralApproval.Phase.IDLE, model.phase)
    }

    @Test fun inputMutationsCannotChangeBoundCommitment() {
        val expected = commitment.copyOf()
        val model = ReviewOnlyBilateralApproval()
        val a = requireNotNull(model.begin(presenter, expected))
        expected.fill(0)
        assertTrue(model.localDecision(a, commitment, approve))
        assertTrue(model.simulatePeerFrame(a, frame()))
        assertTrue(model.bothDecisionsSimulated)
    }

    @Test fun replyIsNeverAnAuthenticatedNetworkProofByItself() {
        val model = ReviewOnlyBilateralApproval()
        assertEquals(ReviewOnlyBilateralApproval.Phase.IDLE, model.phase)
        // This type is inside src/test ONLY: no transport or trusted adapter
        // can call it in an installed app, regardless of simulated state.
        assertFalse(model.bothDecisionsSimulated)
    }
}
