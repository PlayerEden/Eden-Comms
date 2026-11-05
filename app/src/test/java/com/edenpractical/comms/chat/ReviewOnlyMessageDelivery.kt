package com.edenpractical.comms.chat

/**
 * JVM-TEST-ONLY sketch of delivery event ordering. It NEVER handles text or
 * bytes, emits no packets, and cannot verify peer identity or consent.
 *
 * The deliberately named simulated approval and receipt callbacks must not
 * be treated as trustworthy evidence for a shipped messaging client.
 */
internal class ReviewOnlyMessageDelivery {
    enum class Phase { IDLE, WAITING_FOR_APPROVAL, SIMULATED_APPROVED, CLOSED }

    class Attempt internal constructor(val number: Long)

    private var generation = 0L
    private var active: Attempt? = null
    private var phase = Phase.IDLE
    private var nextMessageId = 1L
    private val messages = linkedMapOf<Long, ConversationStatusGuide.Step>()

    companion object {
        const val MAX_PENDING = 32
    }

    @Synchronized
    fun currentPhase(): Phase = phase

    @Synchronized
    fun begin(): Attempt? {
        if (phase != Phase.IDLE || generation == Long.MAX_VALUE) return null
        val attempt = Attempt(++generation)
        active = attempt
        phase = Phase.WAITING_FOR_APPROVAL
        return attempt
    }

    @Synchronized
    fun injectSimulatedBothApprovalsForTest(attempt: Attempt): Boolean {
        if (attempt !== active || phase != Phase.WAITING_FOR_APPROVAL) return false
        phase = Phase.SIMULATED_APPROVED
        return true
    }

    @Synchronized
    fun queueFakeMessageId(attempt: Attempt): Long? {
        if (attempt !== active || phase != Phase.SIMULATED_APPROVED ||
            messages.size >= MAX_PENDING || nextMessageId == Long.MAX_VALUE
        ) return null
        val id = nextMessageId++
        messages[id] = ConversationStatusGuide.Step.QUEUED
        return id
    }

    @Synchronized
    fun injectSimulatedLocalWriteForTest(attempt: Attempt, id: Long): Boolean =
        move(attempt, id, ConversationStatusGuide.Step.QUEUED,
            ConversationStatusGuide.Step.WRITTEN_TO_CHANNEL)

    /**
     * Only a separate event after a local channel write may represent a
     * hypothetical *peer app* acknowledgment; never a human read receipt.
     * The adapter that could authenticate such proof DOES NOT EXIST.
     */
    @Synchronized
    fun injectSimulatedPeerAckForTest(attempt: Attempt, id: Long): Boolean =
        move(attempt, id, ConversationStatusGuide.Step.WRITTEN_TO_CHANNEL,
            ConversationStatusGuide.Step.PEER_APP_ACKNOWLEDGED)

    @Synchronized
    fun markSimulatedFailure(attempt: Attempt, id: Long): Boolean {
        if (attempt !== active || phase != Phase.SIMULATED_APPROVED) return false
        val status = messages[id]
        if (status != ConversationStatusGuide.Step.QUEUED &&
            status != ConversationStatusGuide.Step.WRITTEN_TO_CHANNEL
        ) return false
        messages[id] = ConversationStatusGuide.Step.FAILED
        return true
    }

    @Synchronized
    fun status(attempt: Attempt, id: Long): ConversationStatusGuide.Step? =
        if (attempt === active && phase == Phase.SIMULATED_APPROVED) messages[id] else null

    @Synchronized
    fun close(attempt: Attempt): Boolean {
        if (attempt !== active || phase == Phase.CLOSED || phase == Phase.IDLE) return false
        phase = Phase.CLOSED
        messages.clear()
        return true
    }

    @Synchronized
    fun reset(): Boolean {
        if (phase != Phase.CLOSED) return false
        active = null
        messages.clear()
        phase = Phase.IDLE
        return true
    }

    private fun move(
        attempt: Attempt,
        id: Long,
        from: ConversationStatusGuide.Step,
        to: ConversationStatusGuide.Step
    ): Boolean {
        if (attempt !== active || phase != Phase.SIMULATED_APPROVED ||
            messages[id] != from
        ) return false
        messages[id] = to
        return true
    }
}
