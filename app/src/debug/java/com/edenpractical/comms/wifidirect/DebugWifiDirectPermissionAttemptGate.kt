package com.edenpractical.comms.wifidirect

/**
 * Android permission callbacks lack attempt IDs. After cancel, reserve the
 * outstanding callback slot until Android returns it; never accept an old
 * callback as a new approval. No radio, peer or session API in this class.
 */
internal class DebugWifiDirectPermissionAttemptGate {
    enum class Outcome { GRANTED, DENIED, IGNORED }
    var outstanding: Boolean = false
        private set
    private var cancelled: Boolean = false

    fun begin(
        foreground: Boolean,
        featureAdvertised: Boolean,
        managerPresent: Boolean,
        permissionAlreadyGranted: Boolean
    ): Boolean {
        if (!foreground || !featureAdvertised || !managerPresent ||
            permissionAlreadyGranted || outstanding) return false
        outstanding = true
        cancelled = false
        return true
    }
    fun cancel() {
        if (outstanding) cancelled = true
    }
    fun complete(granted: Boolean, foreground: Boolean): Outcome {
        if (!outstanding) return Outcome.IGNORED
        val accept = foreground && !cancelled
        outstanding = false
        cancelled = false
        if (!accept) return Outcome.IGNORED
        return if (granted) Outcome.GRANTED else Outcome.DENIED
    }
}
