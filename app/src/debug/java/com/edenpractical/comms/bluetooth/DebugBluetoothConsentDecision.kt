package com.edenpractical.comms.bluetooth

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * One user's one explicit decision for ONE active diagnostic RFCOMM socket.
 * Pure Kotlin, with no radio, saved trust, auto-approval, or production action.
 *
 * The worker thread waits outside the UI thread. Cancellation/foreground loss
 * closes this gate, wakes any waiter and invalidates a previously tapped value.
 * A new socket ALWAYS uses a new gate instance.
 */
internal class DebugBluetoothConsentDecision {
    enum class Decision { APPROVE, DECLINE, CANCELED, TIMEOUT }

    private val decisionLatch = CountDownLatch(1)
    @Volatile private var chosen: Decision? = null
    @Volatile private var closed = false

    @Synchronized
    fun approve(): Boolean = choose(Decision.APPROVE)

    @Synchronized
    fun decline(): Boolean = choose(Decision.DECLINE)

    private fun choose(decision: Decision): Boolean {
        if (closed || chosen != null) return false
        chosen = decision
        decisionLatch.countDown()
        return true
    }

    fun await(maxWaitMs: Long): Decision {
        require(maxWaitMs in 1L..30_000L) { "Consent wait must be bounded" }
        val answered = decisionLatch.await(maxWaitMs, TimeUnit.MILLISECONDS)
        return if (closed) Decision.CANCELED
            else if (!answered) Decision.TIMEOUT
            else chosen ?: Decision.CANCELED
    }

    @Synchronized
    fun close() {
        closed = true
        chosen = Decision.CANCELED
        decisionLatch.countDown()
    }
}
