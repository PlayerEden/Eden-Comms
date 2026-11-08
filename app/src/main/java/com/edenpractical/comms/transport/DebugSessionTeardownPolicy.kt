package com.edenpractical.comms.transport

import com.edenpractical.comms.protocol.TestPacketCodec

/**
 * Pure DEBUG session teardown decisions. Not a send authorization or a
 * replacement for pinned TLS, both QR proofs or two-person confirmation.
 *
 * A user's local end/decline revokes ordinary outgoing packets immediately;
 * only the already-authenticated best-effort terminal controls may still be
 * written before the forced socket close. The caller's existing output lock
 * and socket registry remain responsible for actual I/O and cancellation.
 */
internal object DebugSessionTeardownPolicy {
    fun mayWrite(ending: Boolean, type: TestPacketCodec.Type): Boolean =
        !ending || type == TestPacketCodec.Type.CLOSE ||
            type == TestPacketCodec.Type.DECLINE

    fun mayReportActivity(stopped: Boolean, terminal: Boolean, ending: Boolean): Boolean =
        !stopped && !terminal && !ending

    fun mayReportUnexpectedEnd(stopped: Boolean, ending: Boolean): Boolean =
        !stopped && !ending
}
