package com.edenpractical.comms.transport

import com.edenpractical.comms.protocol.DebugLanVoiceFloorCodec
import java.util.concurrent.Executor

/**
 * F4-H: A tiny, bounded lane for grant/request/deny/cancel control frames.
 *
 * The audio writer is allowed to stream for the full three-second speech
 * window. Floor responses MUST NOT wait for that audio writer to finish.
 * Delivery still uses LanDemoSession's exact same outputLock, authenticated
 * TLS socket, mutual approval gate and serialized frame encoder. This class
 * never owns or opens a socket.
 *
 * A full or stopped queue is a fail-closed session error, not permission
 * to silently lose a REQUEST, CANCEL, GRANT or DENY.
 */
internal class DebugLanFloorControlQueue(
    private val executor: Executor,
    private val write: (DebugLanVoiceFloorCodec.Frame) -> Unit,
    private val onFailure: () -> Unit,
    maxPending: Int = 4
) {
    private val budget = DemoSendBudget(maxPending)

    fun offer(frame: DebugLanVoiceFloorCodec.Frame): Boolean {
        if (!budget.tryAcquire()) return false
        return try {
            executor.execute {
                try {
                    write(frame)
                } catch (_: Exception) {
                    onFailure()
                } finally {
                    budget.release()
                }
            }
            true
        } catch (_: RuntimeException) {
            budget.release()
            false
        }
    }

    val pending: Int get() = budget.pending
}
