package com.edenpractical.comms.transport

import java.io.IOException

/** Fixed exception type: do not attach IP, SSID, network handle or raw message. */
internal class LanWifiChangedException : IOException()

/**
 * Fail closed when a foreground test moves to a different Wi-Fi network,
 * IPv4 address, or prefix. Does not require internet or an Android API.
 *
 * The Android caller supplies Network and Inet4Address identity objects;
 * JVM tests use non-sensitive stand-ins. No peer/device metadata is stored.
 */
internal object WifiLinkContinuity {
    fun <N : Any, A : Any> unchanged(
        expectedNetwork: N,
        expectedAddress: A,
        expectedPrefix: Int,
        actualNetwork: N?,
        actualAddress: A?,
        actualPrefix: Int?
    ): Boolean =
        expectedPrefix in 1..31 &&
            actualPrefix == expectedPrefix &&
            expectedNetwork == actualNetwork &&
            expectedAddress == actualAddress
}

/**
 * Graceful CLOSE is best-effort: a stalled socket writer must never prevent
 * cancellation from closing test sockets. The caller schedules a forced close
 * on a different executor before it tries to enqueue CLOSE.
 */
internal object LanGracefulStop {
    const val FORCE_CLOSE_AFTER_MS = 1_200L

    fun begin(
        scheduleHardClose: (Long, () -> Unit) -> Unit,
        enqueueClose: () -> Unit,
        hardClose: () -> Unit
    ) {
        try {
            scheduleHardClose(FORCE_CLOSE_AFTER_MS, hardClose)
        } catch (_: RuntimeException) {
            hardClose()
            return
        }
        try {
            enqueueClose()
        } catch (_: RuntimeException) {
            hardClose()
        }
    }
}
