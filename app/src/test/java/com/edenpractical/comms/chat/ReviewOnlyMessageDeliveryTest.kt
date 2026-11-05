package com.edenpractical.comms.chat

import com.edenpractical.comms.model.ReleaseCapabilities
import com.edenpractical.comms.chat.ConversationStatusGuide.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewOnlyMessageDeliveryTest {
    @Test fun noFutureStatusCanAppearAsLiveInTheCurrentBuild() {
        assertEquals(setOf(Step.LOCAL_PREVIEW), ConversationStatusGuide.currentLiveSteps)
        assertTrue(ConversationStatusGuide.mayShowAsActualStatus(Step.LOCAL_PREVIEW))
        assertTrue(ConversationStatusGuide.futureSteps.isNotEmpty())
        for (stage in ConversationStatusGuide.futureSteps) {
            assertFalse(stage.availableToday)
            assertFalse(ConversationStatusGuide.mayShowAsActualStatus(stage))
        }
        assertFalse(ReleaseCapabilities.CAN_CONNECT)
        assertFalse(ReleaseCapabilities.CAN_SEND_MESSAGES)
        assertFalse(ReleaseCapabilities.CAN_TRANSMIT_VOICE)
    }

    @Test fun everyFutureStatusHasClearTruthfulSemantics() {
        for (stage in Step.values()) {
            assertTrue(stage.label.isNotBlank())
            assertTrue(stage.explanation.isNotBlank())
        }
        assertTrue(Step.WRITTEN_TO_CHANNEL.explanation.contains("does not prove"))
        assertTrue(Step.PEER_APP_ACKNOWLEDGED.explanation.contains("never means"))
        assertTrue(Step.PEER_APP_ACKNOWLEDGED.explanation.contains("authenticated"))
        assertTrue(Step.QUEUED.explanation.contains("not written"))
        assertTrue(Step.AWAITING_APPROVAL.explanation.contains("both people"))
    }

    @Test fun noMessageCanQueueBeforeSimulatedConsent() {
        val model = ReviewOnlyMessageDelivery()
        assertEquals(ReviewOnlyMessageDelivery.Phase.IDLE, model.currentPhase())
        val token = requireNotNull(model.begin())
        assertEquals(ReviewOnlyMessageDelivery.Phase.WAITING_FOR_APPROVAL,
            model.currentPhase())
        assertNull(model.queueFakeMessageId(token))
        assertFalse(model.injectSimulatedPeerAckForTest(token, 1))
        assertFalse(model.injectSimulatedLocalWriteForTest(token, 1))
        assertTrue(model.injectSimulatedBothApprovalsForTest(token))
        assertFalse(model.injectSimulatedBothApprovalsForTest(token))
        assertNotNull(model.queueFakeMessageId(token))
        // Simulated readiness NEVER enables the release communication flags.
        assertFalse(ReleaseCapabilities.CAN_SEND_MESSAGES)
    }

    @Test fun aWriteIsNotAnAppReceiptAndAnAppReceiptIsNotReadProof() {
        val model = ReviewOnlyMessageDelivery()
        val token = requireNotNull(model.begin())
        assertTrue(model.injectSimulatedBothApprovalsForTest(token))
        val id = requireNotNull(model.queueFakeMessageId(token))
        assertEquals(Step.QUEUED, model.status(token, id))
        assertFalse(model.injectSimulatedPeerAckForTest(token, id))
        assertEquals(Step.QUEUED, model.status(token, id))
        assertTrue(model.injectSimulatedLocalWriteForTest(token, id))
        assertEquals(Step.WRITTEN_TO_CHANNEL, model.status(token, id))
        assertFalse(model.injectSimulatedLocalWriteForTest(token, id))
        assertTrue(model.injectSimulatedPeerAckForTest(token, id))
        assertEquals(Step.PEER_APP_ACKNOWLEDGED, model.status(token, id))
        assertFalse(model.injectSimulatedPeerAckForTest(token, id))
        assertFalse(model.markSimulatedFailure(token, id))
    }

    @Test fun failureIsTerminalForMessageUnlessNewMessageIdIsCreated() {
        val model = ReviewOnlyMessageDelivery()
        val token = requireNotNull(model.begin())
        model.injectSimulatedBothApprovalsForTest(token)
        val id = requireNotNull(model.queueFakeMessageId(token))
        assertTrue(model.markSimulatedFailure(token, id))
        assertEquals(Step.FAILED, model.status(token, id))
        assertFalse(model.injectSimulatedLocalWriteForTest(token, id))
        assertFalse(model.injectSimulatedPeerAckForTest(token, id))
        val next = requireNotNull(model.queueFakeMessageId(token))
        assertTrue(next > id)
        assertEquals(Step.QUEUED, model.status(token, next))
    }

    @Test fun fabricatedOrUnknownAckCannotPromoteAnyOtherMessage() {
        val model = ReviewOnlyMessageDelivery()
        val token = requireNotNull(model.begin())
        model.injectSimulatedBothApprovalsForTest(token)
        val a = requireNotNull(model.queueFakeMessageId(token))
        val b = requireNotNull(model.queueFakeMessageId(token))
        model.injectSimulatedLocalWriteForTest(token, a)
        assertFalse(model.injectSimulatedPeerAckForTest(token, b))
        assertFalse(model.injectSimulatedPeerAckForTest(token, -1))
        assertFalse(model.injectSimulatedPeerAckForTest(token, Long.MAX_VALUE))
        assertEquals(Step.QUEUED, model.status(token, b))
        assertEquals(Step.WRITTEN_TO_CHANNEL, model.status(token, a))
    }

    @Test fun attemptsAreComparedByObjectIdentityNotForgedNumericGeneration() {
        val first = ReviewOnlyMessageDelivery()
        val second = ReviewOnlyMessageDelivery()
        val a = requireNotNull(first.begin())
        val b = requireNotNull(second.begin())
        assertEquals(a.number, b.number)
        assertFalse(first.injectSimulatedBothApprovalsForTest(b))
        assertFalse(first.close(b))
        assertNull(first.queueFakeMessageId(b))
        assertEquals(ReviewOnlyMessageDelivery.Phase.WAITING_FOR_APPROVAL,
            first.currentPhase())
        assertTrue(first.injectSimulatedBothApprovalsForTest(a))
    }

    @Test fun sessionEndDropsStatesAndStaleCallbacksCannotReopenNewSession() {
        val model = ReviewOnlyMessageDelivery()
        val old = requireNotNull(model.begin())
        model.injectSimulatedBothApprovalsForTest(old)
        val oldId = requireNotNull(model.queueFakeMessageId(old))
        model.injectSimulatedLocalWriteForTest(old, oldId)
        assertTrue(model.close(old))
        assertEquals(ReviewOnlyMessageDelivery.Phase.CLOSED, model.currentPhase())
        assertNull(model.status(old, oldId))
        assertFalse(model.injectSimulatedPeerAckForTest(old, oldId))
        assertFalse(model.close(old))
        assertNull(model.begin())
        assertTrue(model.reset())
        val fresh = requireNotNull(model.begin())
        assertEquals(old.number + 1, fresh.number)
        assertFalse(model.injectSimulatedBothApprovalsForTest(old))
        assertFalse(model.close(old))
        assertNull(model.queueFakeMessageId(old))
        assertNull(model.status(fresh, oldId))
        assertTrue(model.injectSimulatedBothApprovalsForTest(fresh))
        val next = requireNotNull(model.queueFakeMessageId(fresh))
        assertTrue(next > oldId)
        assertEquals(Step.QUEUED, model.status(fresh, next))
    }

    @Test fun boundedMetadataQueueHasNoUserTextAndNoAutoRetry() {
        val model = ReviewOnlyMessageDelivery()
        val token = requireNotNull(model.begin())
        model.injectSimulatedBothApprovalsForTest(token)
        val ids = (1..ReviewOnlyMessageDelivery.MAX_PENDING).map {
            requireNotNull(model.queueFakeMessageId(token))
        }
        assertEquals(ReviewOnlyMessageDelivery.MAX_PENDING, ids.size)
        assertNull(model.queueFakeMessageId(token))
        assertTrue(model.markSimulatedFailure(token, ids[0]))
        assertNull(model.queueFakeMessageId(token)) // no silent retry / unbounded queue
        assertFalse(ReviewOnlyMessageDelivery::class.java.declaredFields.any {
            val lower = it.name.lowercase()
            lower.contains("body") || lower.contains("payload") ||
                lower.contains("text") || lower.contains("peer")
        })
    }
}
