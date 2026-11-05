package com.edenpractical.comms.transport

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanConnectRetryPolicyTest {
    @Test fun refusesUntilPresenterIsReadyWithinBoundedWindow() {
        assertTrue(LanConnectRetryPolicy.shouldRetry(ConnectException(), 0, true))
        assertTrue(LanConnectRetryPolicy.shouldRetry(
            SocketTimeoutException(), LanConnectRetryPolicy.MAX_WAIT_MS - 1, true))
    }

    @Test fun neverRetriesBeyondTimeWindowOrInManualDiagnostic() {
        assertFalse(LanConnectRetryPolicy.shouldRetry(
            ConnectException(), LanConnectRetryPolicy.MAX_WAIT_MS, true))
        assertFalse(LanConnectRetryPolicy.shouldRetry(ConnectException(), 0, false))
        assertFalse(LanConnectRetryPolicy.shouldRetry(ConnectException(), -1, true))
    }

    @Test fun retryDelayNeverExtendsPastDeadline() {
        val p = LanConnectRetryPolicy
        assertEquals(700L, p.delayBeforeRetryMs(0L))
        assertEquals(700L, p.delayBeforeRetryMs(p.MAX_WAIT_MS - 700L))
        assertEquals(1L, p.delayBeforeRetryMs(p.MAX_WAIT_MS - 1L))
        assertEquals(0L, p.delayBeforeRetryMs(p.MAX_WAIT_MS))
        assertEquals(0L, p.delayBeforeRetryMs(p.MAX_WAIT_MS + 1L))
        assertEquals(0L, p.delayBeforeRetryMs(-1L))
    }

    @Test fun neverRetriesTlsOrRoutingOrUnexpectedFailures() {
        assertFalse(LanConnectRetryPolicy.shouldRetry(SSLHandshakeException("pin rejected"), 1, true))
        assertFalse(LanConnectRetryPolicy.shouldRetry(NoRouteToHostException(), 1, true))
        assertFalse(LanConnectRetryPolicy.shouldRetry(IllegalStateException("wifi changed"), 1, true))
    }
}
