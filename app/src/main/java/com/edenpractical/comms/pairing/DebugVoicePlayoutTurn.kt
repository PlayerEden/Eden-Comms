package com.edenpractical.comms.pairing

/**
 * F4-Q: one remote live voice turn's local speaker evidence.
 *
 * Only emits one positive AudioTrack-start observation and one local
 * failed/enqueue-refused observation per turn. A START event and validated
 * TLS PCM/END are NOT evidence that Android played audible sound.
 *
 * Pure in-memory flags: no speech, message content, peer identity or clock.
 * Thread safe because the approved LAN reader and Android speaker callback
 * can report a result concurrently.
 */
internal class DebugVoicePlayoutTurn {
    enum class Observation { NONE, SPEAKER_STARTED, LOCAL_PLAYBACK_FAILED }

    private var speakerStarted = false
    private var playbackFailed = false

    @Synchronized
    fun speakerResult(started: Boolean): Observation {
        if (!started) return rejectedFrame()
        if (speakerStarted) return Observation.NONE
        speakerStarted = true
        return Observation.SPEAKER_STARTED
    }

    @Synchronized
    fun hasPlaybackProblem(): Boolean = playbackFailed

    @Synchronized
    fun rejectedFrame(): Observation {
        if (playbackFailed) return Observation.NONE
        playbackFailed = true
        return Observation.LOCAL_PLAYBACK_FAILED
    }
}
