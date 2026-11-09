package com.edenpractical.comms.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.edenpractical.comms.pairing.DebugLanVoiceAudio

/**
 * F4-U: actual microphone -> bounded PCM copy -> caller's APPROVED encrypted
 * channel -> remote AudioTrack. Foreground/debug only, no voice storage, service,
 * raw radio, cloud or automatic microphone permission prompts.
 *
 * First step is a short 3-second half-duplex-style BURST AFTER RELEASE, not
 * real-time streaming or a negotiated voice floor. Sending and playback are
 * gated by the live caller-provided authenticated-session predicate.
 */
internal class DebugNearbyVoiceOwner(context: Context) {
    private val audio = DebugLanVoiceAudio(context.applicationContext)
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var version = 0
    @Volatile private var active = false
    @Volatile private var sender: ((ByteArray) -> Boolean)? = null
    @Volatile private var allowed: (() -> Boolean)? = null

    var capturing by mutableStateOf(false)
        private set
    var notice by mutableStateOf(
        "Voice is off until both people approve the encrypted test session."
    )
        private set

    fun activate(canContinue: () -> Boolean, offer: (ByteArray) -> Boolean) {
        disable()
        allowed = canContinue
        sender = offer
        active = true
        notice = "3-second test voice bursts available. Audio sends after release."
    }

    fun beginCapture(): Boolean {
        if (!active || capturing || allowed?.invoke() != true) return false
        val ticket = version
        val started = audio.startCapture(
            onNoAudio = {
                handler.post {
                    if (current(ticket)) {
                        capturing = false
                        notice = "No complete microphone audio captured; nothing sent."
                    }
                }
            },
            onComplete = { pcm ->
                // The recording worker wipes its PCM immediately after this
                // callback; the verified channel synchronously copies it.
                val sent = current(ticket) && allowed?.invoke() == true &&
                    (sender?.invoke(pcm) == true)
                handler.post {
                    if (current(ticket)) {
                        capturing = false
                        notice = if (sent)
                            "Short voice burst queued on the approved encrypted link."
                        else "Voice not queued. Link expired, busy, or unavailable."
                    }
                }
            }
        )
        if (started) {
            capturing = true
            notice = "Recording on this phone, max 3s. Release to send over TLS."
        }
        return started
    }

    fun release() {
        if (!active || !capturing) return
        capturing = false
        audio.finishCapture()
        notice = "Finishing microphone capture; will send only if still connected."
    }

    fun cancelCapture() {
        version++
        capturing = false
        audio.cancelAll()
        if (active) notice = "Voice capture cancelled. No unsent audio retained."
    }

    fun accessibilityTap() {
        if (capturing) release() else beginCapture()
    }

    /** Synchronous from the MAIN-thread verified-session callback. */
    fun receiveEncryptedVoice(pcm: ByteArray) {
        if (!active || allowed?.invoke() != true) return
        val ticket = version
        val started = audio.playReceived(pcm) { playbackStarted ->
            handler.post {
                if (current(ticket)) {
                    notice = if (playbackStarted)
                        "Playing a received encrypted test voice burst on this phone."
                    else "Received voice, but audio output could not start."
                }
            }
        }
        if (!started) notice = "Received voice could not play while microphone/speaker busy."
    }

    fun voiceWritten(frames: Int) {
        if (active) notice = "Encrypted voice burst written (${frames * 20} ms). " +
            "No confirmation a person heard it."
    }

    fun disable() {
        version++
        active = false
        capturing = false
        allowed = null
        sender = null
        audio.cancelAll()
        notice = "Voice stopped. No active approved encrypted session."
    }

    fun isActive(): Boolean = active
    private fun current(ticket: Int): Boolean = active && version == ticket
}
