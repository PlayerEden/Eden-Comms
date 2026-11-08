package com.edenpractical.comms.audio

/**
 * D1 local audio-only development test. This does not grant permission to
 * send audio over LAN, Bluetooth, Wi-Fi Direct, or any production session.
 */
internal object DebugLocalPttPolicy {
    const val SAMPLE_RATE_HZ = 16_000
    const val MAX_SECONDS = 5
    const val MAX_PCM_BYTES = SAMPLE_RATE_HZ * MAX_SECONDS * 2
    const val MIN_PCM_BYTES = 1_600

    fun mayRecord(
        debugBuild: Boolean,
        foreground: Boolean,
        permissionGranted: Boolean,
        recording: Boolean,
        playing: Boolean
    ): Boolean =
        debugBuild && foreground && permissionGranted && !recording && !playing

    fun mayPlay(
        debugBuild: Boolean,
        foreground: Boolean,
        recording: Boolean,
        playing: Boolean,
        clipBytes: Int
    ): Boolean =
        debugBuild && foreground && !recording && !playing &&
            clipBytes in MIN_PCM_BYTES..MAX_PCM_BYTES && clipBytes % 2 == 0

    fun acceptWorkerResult(
        workerGeneration: Int,
        currentGeneration: Int,
        foreground: Boolean
    ): Boolean = workerGeneration == currentGeneration && foreground
}
