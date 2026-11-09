package com.edenpractical.comms.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.edenpractical.comms.pairing.DebugLanVoiceAudio
import com.edenpractical.comms.protocol.DebugLanVoiceWireCodec
import com.edenpractical.comms.transport.DebugLanVoiceFloor

/**
 * F4-V: live 640-byte/20ms foreground microphone -> current approved TLS
 * writer -> remote streaming AudioTrack, AFTER exact host-granted voice floor.
 *
 * Never grants authorization, stores audio, chooses a peer, opens a network,
 * or starts the mic for a mere unverified discovery/QR/connection. The joiner
 * cannot begin capture until a correlated encrypted GRANT is observed.
 * Release/cancel while requesting sends CANCEL; releasing a live capture
 * stops mic and queues an ordered END and RELEASE only after final PCM.
 */
internal class DebugNearbyVoiceOwner(context: Context) {
    private val audio = DebugLanVoiceAudio(context.applicationContext)
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var version = 0
    @Volatile private var active = false
    @Volatile private var pressed = false
    @Volatile private var pendingFloor = false
    @Volatile private var allowed: (() -> Boolean)? = null
    @Volatile private var requestFloor: (() -> DebugLanVoiceFloor.Ask)? = null
    @Volatile private var cancelFloor: (() -> Unit)? = null
    @Volatile private var beginStream: (() -> Boolean)? = null
    @Volatile private var offerFrame: ((ByteArray) -> Boolean)? = null
    @Volatile private var finishStream: (() -> Boolean)? = null

    var capturing by mutableStateOf(false)
        private set
    var finalizing by mutableStateOf(false)
        private set
    var notice by mutableStateOf(
        "Live voice is off until both people approve this encrypted session."
    )
        private set

    fun activate(
        canContinue: () -> Boolean,
        requestFloor: () -> DebugLanVoiceFloor.Ask,
        cancelFloor: () -> Unit,
        beginStream: () -> Boolean,
        offerFrame: (ByteArray) -> Boolean,
        finishStream: () -> Boolean
    ) {
        disable()
        allowed = canContinue
        this.requestFloor = requestFloor
        this.cancelFloor = cancelFloor
        this.beginStream = beginStream
        this.offerFrame = offerFrame
        this.finishStream = finishStream
        active = true
        notice = "Hold to request the talk channel, then send live 20ms test audio."
    }

    /** Called only by the explicit foreground touch/accessibility action. */
    fun beginCapture(): Boolean {
        if (!active || pressed || capturing || finalizing ||
            allowed?.invoke() != true
        ) return false
        pressed = true
        return when (requestFloor?.invoke() ?: DebugLanVoiceFloor.Ask.BUSY) {
            DebugLanVoiceFloor.Ask.GRANTED -> startGrantedMicrophone()
            DebugLanVoiceFloor.Ask.PENDING -> {
                pendingFloor = true
                notice = "Requesting encrypted talk floor. Microphone is OFF."
                true
            }
            DebugLanVoiceFloor.Ask.BUSY -> {
                pressed = false
                notice = "Talk channel busy. Wait until the other phone finishes."
                false
            }
        }
    }

    /** Joiner receives this only for its current authenticated REQUEST. */
    fun floorGranted() {
        if (!active || !pendingFloor) return
        pendingFloor = false
        if (!pressed || allowed?.invoke() != true) {
            cancelFloor?.invoke()
            notice = "Talk request released before mic started."
            return
        }
        startGrantedMicrophone()
    }

    fun floorDenied() {
        if (!active || !pendingFloor) return
        pendingFloor = false
        pressed = false
        capturing = false
        notice = "Other phone is speaking. Talk floor denied; mic remained OFF."
    }

    private fun startGrantedMicrophone(): Boolean {
        if (!active || !pressed || allowed?.invoke() != true ||
            beginStream?.invoke() != true
        ) {
            pressed = false
            cancelFloor?.invoke()
            notice = "Verified talk channel unavailable. Mic remains OFF."
            return false
        }
        val ticket = version
        val started = audio.startLiveCapture(
            onFrame = { pcm ->
                current(ticket) && allowed?.invoke() == true &&
                    offerFrame?.invoke(pcm) == true
            },
            onStopped = { hadPcm ->
                // The audio worker has stopped offering 20ms frames.
                // END/RELEASE must follow its last queued frame in FIFO TLS.
                val finished = if (current(ticket)) {
                    finishStream?.invoke() == true
                } else false
                handler.post {
                    if (current(ticket)) {
                        capturing = false
                        finalizing = false
                        pressed = false
                        notice = when {
                            !finished -> "Live voice stopped or link interrupted."
                            hadPcm -> "Live frames queued while held. " +
                                "No proof the other person heard the audio."
                            else -> "No full microphone frame; talk floor released."
                        }
                    }
                }
            }
        )
        if (!started) {
            // A granted floor MUST be released even if OEM microphone
            // initialization failed or local speaker is still playing.
            finishStream?.invoke()
            pressed = false
            notice = "Microphone or speaker busy; talk floor released."
            return false
        }
        capturing = true
        finalizing = false
        notice = "Sending live encrypted frames while held; max 3 seconds."
        return true
    }

    fun release() {
        if (!active) return
        if (pendingFloor) {
            pendingFloor = false
            pressed = false
            cancelFloor?.invoke()
            notice = "Talk request canceled before microphone started."
            return
        }
        if (!capturing) return
        pressed = false
        capturing = false
        finalizing = true
        audio.finishCapture()
        notice = "Stopping microphone; queuing final END and releasing floor."
    }

    fun cancelCapture() {
        if (pendingFloor) {
            pendingFloor = false
            pressed = false
            cancelFloor?.invoke()
            notice = "Talk request canceled. Mic never started."
        } else if (capturing) {
            // Earlier PCM has ALREADY traveled. Stop future mic frames and
            // send a valid END/RELEASE from the audio worker.
            release()
        }
    }

    fun accessibilityTap() {
        if (capturing || pendingFloor) release()
        else beginCapture()
    }

    /** Accepted only from the same authenticated TLS receive callback. */
    fun receiveLiveFrame(kind: DebugLanVoiceWireCodec.Kind, pcm: ByteArray) {
        if (!active || allowed?.invoke() != true) return
        val ticket = version
        val accepted = audio.acceptLiveFrame(kind, pcm) { playbackStarted ->
            handler.post {
                if (current(ticket)) {
                    notice = if (playbackStarted)
                        "Playing live encrypted test audio received from other phone."
                    else "Received live audio but speaker could not start."
                }
            }
        }
        if (!accepted && kind == DebugLanVoiceWireCodec.Kind.START)
            notice = "Cannot play remote voice while microphone/speaker is busy."
    }

    fun voiceWritten(frames: Int) {
        if (active) notice = "Wrote ${frames * 20} ms of encrypted live audio. " +
            "Playback on the other phone is not confirmed."
    }

    fun disable() {
        version++
        active = false
        pressed = false
        pendingFloor = false
        capturing = false
        finalizing = false
        allowed = null
        requestFloor = null
        cancelFloor = null
        beginStream = null
        offerFrame = null
        finishStream = null
        audio.cancelAll()
        notice = "Voice stopped. No approved foreground TLS session."
    }

    fun isActive(): Boolean = active
    private fun current(ticket: Int): Boolean = active && ticket == version
}
