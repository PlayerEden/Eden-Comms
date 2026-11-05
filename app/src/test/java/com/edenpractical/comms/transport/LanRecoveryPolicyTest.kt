package com.edenpractical.comms.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger

class LanRecoveryPolicyTest {
    @Test fun sameWifiNetworkAddressAndPrefixStayValidEvenWithoutWan() {
        assertTrue(WifiLinkContinuity.unchanged(
            "wifi-token-A", "192.168.10.3", 24,
            "wifi-token-A", "192.168.10.3", 24
        ))
    }

    @Test fun roamingOrNetworkLossFailsClosedEvenWithSameAddress() {
        val testCases = listOf(
            Triple<String?, String?, Int?>("wifi-token-B", "192.168.10.3", 24),
            Triple("wifi-token-A", "192.168.10.4", 24),
            Triple("wifi-token-A", "192.168.10.3", 23),
            Triple(null, null, null),
            Triple("wifi-token-A", null, 24),
            Triple(null, "192.168.10.3", 24)
        )
        for ((network, address, prefix) in testCases) {
            assertFalse(WifiLinkContinuity.unchanged(
                "wifi-token-A", "192.168.10.3", 24,
                network, address, prefix
            ))
        }
    }

    @Test fun nonsensicalOriginalPrefixCannotBeAccepted() {
        for (prefix in listOf(-1, 0, 32, Int.MAX_VALUE)) {
            assertFalse(WifiLinkContinuity.unchanged(
                17, "10.0.0.1", prefix, 17, "10.0.0.1", prefix
            ))
        }
    }

    @Test fun scheduledHardCloseIsArmedBeforeGracefulWrite() {
        val events = mutableListOf<String>()
        var hardStop: (() -> Unit)? = null
        LanGracefulStop.begin(
            scheduleHardClose = { delay, action ->
                assertEquals(LanGracefulStop.FORCE_CLOSE_AFTER_MS, delay)
                events += "timer armed"
                hardStop = action
            },
            enqueueClose = {
                events += "CLOSE queued"
            },
            hardClose = { events += "socket closed" }
        )
        assertEquals(listOf("timer armed", "CLOSE queued"), events)
        requireNotNull(hardStop).invoke()
        assertEquals(listOf("timer armed", "CLOSE queued", "socket closed"), events)
    }

    @Test fun failureToArmSafetyTimerClosesImmediatelyWithoutEnqueue() {
        val closed = AtomicInteger()
        var queued = false
        LanGracefulStop.begin(
            scheduleHardClose = { _, _ -> throw RejectedExecutionException("no timer") },
            enqueueClose = { queued = true },
            hardClose = { closed.incrementAndGet() }
        )
        assertEquals(1, closed.get())
        assertFalse(queued)
    }

    @Test fun failureToEnqueueCloseClosesImmediately() {
        val closed = AtomicInteger()
        var wasArmed = false
        LanGracefulStop.begin(
            scheduleHardClose = { _, _ -> wasArmed = true },
            enqueueClose = { throw RejectedExecutionException("writer unavailable") },
            hardClose = { closed.incrementAndGet() }
        )
        assertTrue(wasArmed)
        assertEquals(1, closed.get())
    }

    @Test fun noPrematureCloseWhileHealthyCloseIsQueued() {
        val closed = AtomicInteger()
        LanGracefulStop.begin(
            scheduleHardClose = { _, _ -> Unit },
            enqueueClose = { Unit },
            hardClose = { closed.incrementAndGet() }
        )
        assertEquals(0, closed.get())
    }
}
