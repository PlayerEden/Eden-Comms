package com.edenpractical.comms.transport

import com.edenpractical.comms.protocol.FrameFormatException
import com.edenpractical.comms.protocol.TestPacketException

/**
 * Public-safe, fixed classifications of an experimental LAN session.
 *
 * A progress event describes which operation the app has *started*, NOT a
 * completed proof or production authorization. No exception messages, QR
 * payloads, addresses, certificate bytes, or user text may enter this model.
 */
internal object LanDemoStatus {
    enum class Progress(val display: String) {
        TLS_NEGOTIATING("Negotiating the local TLS connection…"),
        PEER_IDENTITY("Checking the other phone's certificate identity…"),
        QR_PROOF("Checking both phones' QR session proof…"),
        CHANNEL_MATCH("Comparing this TLS connection's channel binding with the other phone…"),
        BOTH_READY("Waiting for both phones to confirm test readiness…")
    }

    enum class EndCode(val display: String) {
        PEER_ENDED("The other phone ended the test."),
        PEER_DECLINED("The other person declined the debug test connection."),
        APPROVAL_TIMED_OUT("The debug approvals and final session confirmations did not finish in time."),
        WIFI_CHANGED("Wi-Fi changed. The test connection was closed."),
        SESSION_LIMIT("The 30-minute test session limit was reached."),
        PEER_SILENT("No response from the other phone. The test was closed."),
        CLOCK_INVALID("The session timer was inconsistent, so the test was closed."),
        LINK_INTERRUPTED("The local connection was interrupted."),
        PROTOCOL_REJECTED("The test received an invalid or unexpected packet and closed."),
        MONITOR_FAILED("Connection monitoring stopped unexpectedly.")
    }

    /** A narrow exception-type check. Never emit the exception's text. */
    fun activeFailure(error: Throwable): EndCode {
        var current: Throwable? = error
        repeat(8) {
            when (current) {
                is FrameFormatException, is TestPacketException -> return EndCode.PROTOCOL_REJECTED
            }
            val cause = current?.cause
            if (cause == null || cause === current) return EndCode.LINK_INTERRUPTED
            current = cause
        }
        return EndCode.LINK_INTERRUPTED
    }

    fun livenessEnd(reason: DemoSessionLivenessPolicy.EndReason): EndCode =
        when (reason) {
            DemoSessionLivenessPolicy.EndReason.SESSION_LIMIT -> EndCode.SESSION_LIMIT
            DemoSessionLivenessPolicy.EndReason.PEER_SILENT -> EndCode.PEER_SILENT
            DemoSessionLivenessPolicy.EndReason.INVALID_TIMING -> EndCode.CLOCK_INVALID
        }
}
