package com.edenpractical.comms.transport

import com.edenpractical.comms.protocol.TestPacketCodec
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Deterministic write-gate/terminal-report ordering only. Not on-device
 * socket, TLS, QR, delivery, cryptographic or UI callback timing evidence.
 */
class DebugSessionTeardownPolicyTest {
    private val policy = DebugSessionTeardownPolicy

    @Test fun allOrdinaryDebugPacketsAreRejectedAfterEndBegins() {
        for (kind in TestPacketCodec.Type.entries) {
            assertTrue("Packet usable before local shutdown: $kind",
                policy.mayWrite(false, kind))
            val expected = kind == TestPacketCodec.Type.CLOSE ||
                kind == TestPacketCodec.Type.DECLINE
            if (expected) assertTrue(policy.mayWrite(true, kind))
            else assertFalse("Ordinary write after local shutdown: $kind",
                policy.mayWrite(true, kind))
        }
    }

    @Test fun endingSuppressesStaleProgressAndUnexpectedDisconnect() {
        assertTrue(policy.mayReportActivity(false, false, false))
        assertFalse(policy.mayReportActivity(false, false, true))
        assertFalse(policy.mayReportActivity(false, true, false))
        assertFalse(policy.mayReportActivity(true, false, false))
        assertTrue(policy.mayReportUnexpectedEnd(false, false))
        assertFalse(policy.mayReportUnexpectedEnd(false, true))
        assertFalse(policy.mayReportUnexpectedEnd(true, false))
    }

    @Test fun queuedWriterRechecksAfterConcurrentUserEndInsteadOfTrustingOldCheck() {
        val ending = AtomicBoolean(false)
        val readyToWrite = CountDownLatch(1)
        val continueWriter = CountDownLatch(1)
        val allowedAfterEnd = AtomicBoolean(true)
        val runner = thread(name = "m5c-simulated-writer") {
            // This mirrors the early writer eligibility check, not network I/O.
            assertTrue(policy.mayWrite(ending.get(), TestPacketCodec.Type.PRESET))
            readyToWrite.countDown()
            if (continueWriter.await(2, TimeUnit.SECONDS)) {
                allowedAfterEnd.set(policy.mayWrite(
                    ending.get(), TestPacketCodec.Type.PRESET
                ))
            }
        }
        try {
            assertTrue(readyToWrite.await(2, TimeUnit.SECONDS))
            ending.set(true)
            continueWriter.countDown()
            runner.join(2_000)
            assertFalse(runner.isAlive)
            assertFalse("Late queued packet must be blocked", allowedAfterEnd.get())
            assertTrue(policy.mayWrite(true, TestPacketCodec.Type.CLOSE))
        } finally {
            continueWriter.countDown()
            runner.join(2_000)
        }
    }
}
