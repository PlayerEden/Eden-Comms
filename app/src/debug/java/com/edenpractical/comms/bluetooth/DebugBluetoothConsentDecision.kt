package com.edenpractical.comms.bluetooth

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * One user's one explicit decision for ONE active diagnostic RFCOMM socket.
 * Pure Kotlin, with no radio, saved trust, auto-approval, or production action.
 *
 * F2-P: a timed-out or interrupted wait is terminal. A delayed UI tap must
 * never look accepted after the real worker has stopped waiting.
 * The monotonic clock additionally rejects a tap after the configured
 * deadline even if the waiting thread has not yet resumed.
 *
 * The caller still checks active socket token, foreground and permission.
 * Real debug Bluetooth ALWAYS closes after consent: NO messaging authority.
 */
internal class DebugBluetoothConsentDecision(
    private val monotonicNanos: () -> Long = System::nanoTime
) {
    enum class Decision { APPROVE, DECLINE, CANCELED, TIMEOUT }

    private val decisionLatch = CountDownLatch(1)
    private var chosen: Decision? = null
    private var closed = false
    private var awaitConsumed = false
    private var startedNanos: Long? = null
    private var maxWaitNanos = 0L

    @Synchronized
    fun approve(): Boolean = choose(Decision.APPROVE)

    @Synchronized
    fun decline(): Boolean = choose(Decision.DECLINE)

    private fun choose(decision: Decision): Boolean {
        if (closed || chosen != null) return false
        val start = startedNanos
        if (start != null) {
            val now = monotonicNanos()
            val elapsed = now - start
            if (now < start || elapsed < 0L || elapsed >= maxWaitNanos) {
                // Fail closed even if the waiting worker is late resuming.
                chosen = Decision.TIMEOUT
                decisionLatch.countDown()
                return false
            }
        }
        chosen = decision
        decisionLatch.countDown()
        return true
    }

    /**
     * A single bounded worker wait. No second waiter can recover a consumed
     * decision. Programmatic tests can supply a clock, but such a clock does
     * NOT establish real Bluetooth permission or human consent.
     */
    fun await(maxWaitMs: Long): Decision {
        require(maxWaitMs in 1L..30_000L) { "Consent wait must be bounded" }
        synchronized(this) {
            if (awaitConsumed || closed) return Decision.CANCELED
            awaitConsumed = true
            startedNanos = monotonicNanos()
            maxWaitNanos = TimeUnit.MILLISECONDS.toNanos(maxWaitMs)
        }
        val answered = try {
            decisionLatch.await(maxWaitMs, TimeUnit.MILLISECONDS)
        } catch (interrupted: InterruptedException) {
            close()
            Thread.currentThread().interrupt()
            throw interrupted
        }
        return synchronized(this) {
            if (closed) return@synchronized Decision.CANCELED
            if (!answered) {
                // Even if a tap raced the worker's timed-out await(), the
                // final outcome is timeout and no subsequent tap is accepted.
                chosen = Decision.TIMEOUT
                decisionLatch.countDown()
                Decision.TIMEOUT
            } else chosen ?: Decision.CANCELED
        }
    }

    @Synchronized
    fun close() {
        closed = true
        chosen = Decision.CANCELED
        decisionLatch.countDown()
    }
}
