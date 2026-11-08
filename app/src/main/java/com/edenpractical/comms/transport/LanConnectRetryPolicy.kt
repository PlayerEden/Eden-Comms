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

    /**
     * Bound the next blocking TCP connect by the *remaining* pre-TLS retry
     * window. Java Socket.connect(timeout=0) waits indefinitely, so a
     * returned zero is a STOP signal and must never reach Socket.connect.
     *
     * An invalid/elapsed budget fails closed; TLS/QR and user-approval logic
     * are deliberately outside this TCP-only policy.
     */
    fun attemptTimeoutMs(elapsedMs: Long): Int =
        if (elapsedMs !in 0 until MAX_WAIT_MS) 0
        else minOf(CONNECT_TIMEOUT_MS.toLong(), MAX_WAIT_MS - elapsedMs).toInt()

    /** Never sleep past the remaining retry budget, including the last attempt. */
    fun delayBeforeRetryMs(elapsedMs: Long): Long =
        if (elapsedMs !in 0 until MAX_WAIT_MS) 0L
        else minOf(RETRY_DELAY_MS, MAX_WAIT_MS - elapsedMs)
}
