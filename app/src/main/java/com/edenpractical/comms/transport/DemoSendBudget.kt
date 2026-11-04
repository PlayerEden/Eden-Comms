package com.edenpractical.comms.transport

import java.util.concurrent.atomic.AtomicInteger

/** Caps queued/in-flight preset sends so rapid tapping cannot flood the writer. */
internal class DemoSendBudget(private val maxPending: Int = 2) {
    private val count = AtomicInteger(0)

    init { require(maxPending in 1..8) { "Test send queue must be bounded" } }

    fun tryAcquire(): Boolean {
        while (true) {
            val current = count.get()
            if (current >= maxPending) return false
            if (count.compareAndSet(current, current + 1)) return true
        }
    }

    fun release() {
        while (true) {
            val current = count.get()
            if (current <= 0) return
            if (count.compareAndSet(current, current - 1)) return
        }
    }

    val pending: Int get() = count.get()
}
