package com.edenpractical.comms.pairing

/**
 * F4-I: one-shot foreground microphone-send commit gate.
 *
 * The AudioRecord worker may finish on a different thread just as a user
 * cancels, hides the chat or backgrounds the app. Both actions synchronize
 * on this object so a cancel that wins BEFORE submit prevents all subsequent
 * send callbacks. Once submit wins, already-queued audio cannot be recalled.
 *
 * No PCM, keys, network connection, account or persistent data live here.
 */
internal class DebugTapVoiceSendGate {
    private enum class State { CAPTURING, COMMITTED, CANCELLED }
    private var state = State.CAPTURING

    @Synchronized
    fun <T> submitIfActive(submit: () -> T): T? {
        if (state != State.CAPTURING) return null
        // Mark committed *before* calling the real send. The main thread's
        // cancellation cannot slip between this decision and queueing audio.
        state = State.COMMITTED
        return submit()
    }

    /** True iff no send committed and this cancellation prevented future send. */
    @Synchronized
    fun cancel(): Boolean {
        if (state != State.CAPTURING) return false
        state = State.CANCELLED
        return true
    }

    @get:Synchronized
    val isCancelled: Boolean get() = state == State.CANCELLED
}
