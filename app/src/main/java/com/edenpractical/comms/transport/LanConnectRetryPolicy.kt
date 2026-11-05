package com.edenpractical.comms.transport

import java.net.ConnectException
import java.net.SocketTimeoutException

/**
 * Reattempt only a TCP-level refusal/timeout before TLS starts, because the
 * presenter must scan the reciprocal QR before opening the listener.
 * A certificate mismatch, TLS failure, different network or cancellation is
 * never silently retried or downgraded.
 */
internal object LanConnectRetryPolicy {
    const val MAX_WAIT_MS: Long = 120_000L
    const val RETRY_DELAY_MS: Long = 700L
    const val CONNECT_TIMEOUT_MS: Int = 1_500

    fun shouldRetry(error: Exception, elapsedMs: Long, enabled: Boolean): Boolean =
        enabled && elapsedMs in 0 until MAX_WAIT_MS &&
            (error is ConnectException || error is SocketTimeoutException)
}
