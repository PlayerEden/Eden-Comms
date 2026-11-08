package com.edenpractical.comms.pairing

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.SystemClock
import com.edenpractical.comms.protocol.DebugLanVoiceWireCodec
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * DEVELOPMENT TEST ONLY: volatile microphone capture and local speaker output.
 * No networking, peer identity, permission request, disk storage, services or
 * background continuation. Only the foreground approved LAN test UI invokes
 * this. RECORD_AUDIO is present solely in the DEBUG manifest.
 *
 * Records a 16-kHz, mono, 16-bit PCM burst up to 3s; every complete 20ms
 * frame is counted. A caller owns and zeroes the returned clip. Received
 * sound is played on this handset's speaker, never auto-recorded or saved.
 */
internal class DebugLanVoiceAudio(private val context: Context) {
    private val lock = Any()
    @Volatile private var generation = 0
    @Volatile private var capturing = false
    @Volatile private var playing = false
    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var track: AudioTrack? = null
    @Volatile private var livePlayback: LivePlayback? = null

    private class LivePlayback(val token: Int) {
        val frames = ArrayBlockingQueue<ByteArray>(12) // 240ms max
        @Volatile var ended = false
        @Volatile var cancelled = false

        fun offer(pcm: ByteArray): Boolean {
            if (ended || cancelled ||
                pcm.size != DebugLanVoiceWireCodec.PCM_BYTES) return false
            val copy = pcm.copyOf()
            if (frames.offer(copy)) return true
            copy.fill(0)
            cancel()
            return false
        }
        fun finish() { ended = true }
        fun cancel() {
            cancelled = true
            ended = true
            while (true) {
                val next = frames.poll() ?: break
                next.fill(0)
            }
        }
    }

    private fun permitted(): Boolean =
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    @Suppress("MissingPermission")
    fun startCapture(
        onNoAudio: () -> Unit = {},
        onComplete: (ByteArray) -> Unit
    ): Boolean {
        if (!permitted() ||
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
        ) return false
        val token: Int
        synchronized(lock) {
            if (capturing || playing) return false
            generation += 1
            token = generation
            capturing = true
        }
        thread(name = "eden-debug-lan-voice-capture", isDaemon = true) {
            var own: AudioRecord? = null
            val recording = ByteArray(DebugLanVoiceWireCodec.MAX_PCM_BYTES)
            val chunk = ByteArray(DebugLanVoiceWireCodec.PCM_BYTES)
            var used = 0
            try {
                val min = AudioRecord.getMinBufferSize(
                    RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
                )
                if (min < 0) return@thread
                val source = AudioRecord(
                    MediaRecorder.AudioSource.MIC, RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(min, DebugLanVoiceWireCodec.PCM_BYTES * 4)
                )
                own = source
                if (source.state != AudioRecord.STATE_INITIALIZED) return@thread
                synchronized(lock) {
                    if (token != generation || !capturing) return@thread
                    recorder = source
                }
                source.startRecording()
                val started = SystemClock.elapsedRealtime()
                while (token == generation && capturing &&
                    used + chunk.size <= recording.size &&
                    SystemClock.elapsedRealtime() - started < MAX_MS
                ) {
                    var taken = 0
                    while (taken < chunk.size && token == generation && capturing) {
                        val amount = source.read(
                            chunk, taken, chunk.size - taken, AudioRecord.READ_BLOCKING
                        )
                        if (amount <= 0) break
                        taken += amount
                    }
                    if (taken != chunk.size) break
                    chunk.copyInto(recording, used)
                    chunk.fill(0)
                    used += DebugLanVoiceWireCodec.PCM_BYTES
                }
            } catch (_: Exception) {
                // Lack of microphone/device support fails quietly. Never
                // fake a valid clip or continue capture in the background.
            } finally {
                try { own?.stop() } catch (_: Exception) { }
                try { own?.release() } catch (_: Exception) { }
                synchronized(lock) {
                    if (recorder === own) recorder = null
                    if (token == generation) capturing = false
                }
                if (token == generation) {
                    if (used >= DebugLanVoiceWireCodec.PCM_BYTES) {
                        val clip = recording.copyOf(used)
                        try { onComplete(clip) }
                        catch (_: Exception) {
                            // Callback failure must also release the UI's
                            // pending state; never retain or retry PCM.
                            try { onNoAudio() } catch (_: Exception) { }
                        } finally { clip.fill(0) }
                    } else {
                        // Even an immediate release, absent mic or failed start
                        // must complete the UI's capture state. No fake packet.
                        try { onNoAudio() } catch (_: Exception) { }
                    }
                }
                chunk.fill(0)
                recording.fill(0)
            }
        }
        return true
    }

    fun finishCapture() {
        capturing = false
        try { recorder?.stop() } catch (_: Exception) { }
    }

    /** Copy one bounded received clip before caller zeroes the source frame. */
    fun playReceived(
        incoming: ByteArray,
        onPlaybackStarted: (Boolean) -> Unit = {}
    ): Boolean {
        if (!permitted() || incoming.isEmpty() ||
            incoming.size > DebugLanVoiceWireCodec.MAX_PCM_BYTES ||
            incoming.size % DebugLanVoiceWireCodec.PCM_BYTES != 0
        ) return false
        val token: Int
        synchronized(lock) {
            if (playing || capturing) return false
            generation += 1
            token = generation
            playing = true
        }
        val copy = incoming.copyOf()
        thread(name = "eden-debug-lan-voice-playback", isDaemon = true) {
            var own: AudioTrack? = null
            var started = false
            try {
                val min = AudioTrack.getMinBufferSize(
                    RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
                )
                if (min < 0) return@thread
                val speaker = AudioTrack(
                    AudioManager.STREAM_MUSIC, RATE,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(min, copy.size), AudioTrack.MODE_STATIC
                )
                own = speaker
                if (speaker.state != AudioTrack.STATE_INITIALIZED) return@thread
                synchronized(lock) {
                    if (token != generation || !playing) return@thread
                    track = speaker
                }
                if (speaker.write(copy, 0, copy.size) != copy.size) return@thread
                if (token != generation || !playing) return@thread
                speaker.play()
                started = true
                if (token == generation && playing) {
                    // Only say the local AudioTrack actually started, never
                    // claim someone heard the audio on the other handset.
                    try { onPlaybackStarted(true) } catch (_: Exception) { }
                }
                val endAt = SystemClock.elapsedRealtime() +
                    copy.size / (RATE * 2 / 1000) + 120
                while (token == generation && playing &&
                    SystemClock.elapsedRealtime() < endAt
                ) Thread.sleep(20)
            } catch (_: Exception) {
                // Device-specific playback errors never save sound or enable
                // a different transport.
            } finally {
                if (!started && token == generation && playing) {
                    try { onPlaybackStarted(false) } catch (_: Exception) { }
                }
                try { own?.stop() } catch (_: Exception) { }
                try { own?.release() } catch (_: Exception) { }
                synchronized(lock) {
                    if (track === own) track = null
                    if (token == generation) playing = false
                }
                copy.fill(0)
            }
        }
        return true
    }

    /**
     * F4-D: foreground live microphone capture. A 640-byte / 20ms frame
     * is delivered immediately to the caller's bounded TLS queue, NOT saved
     * as a full clip. No recording persists and no audio transport is opened.
     *
     * onStopped is called after a normal release or the three-second cap.
     * A cancelled/generation-invalidated recording never reports success.
     */
    @Suppress("MissingPermission")
    fun startLiveCapture(
        onFrame: (ByteArray) -> Boolean,
        onStopped: (Boolean) -> Unit
    ): Boolean {
        if (!permitted() || context.checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED) return false
        val token: Int
        synchronized(lock) {
            if (capturing || playing) return false
            generation += 1
            token = generation
            capturing = true
        }
        thread(name = "eden-debug-live-ptt-capture", isDaemon = true) {
            var own: AudioRecord? = null
            val frame = ByteArray(DebugLanVoiceWireCodec.PCM_BYTES)
            var sent = 0
            try {
                val min = AudioRecord.getMinBufferSize(
                    RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
                )
                if (min < 0) return@thread
                val source = AudioRecord(
                    MediaRecorder.AudioSource.MIC, RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(min, DebugLanVoiceWireCodec.PCM_BYTES * 4)
                )
                own = source
                if (source.state != AudioRecord.STATE_INITIALIZED) return@thread
                synchronized(lock) {
                    if (token != generation || !capturing) return@thread
                    recorder = source
                }
                source.startRecording()
                val started = SystemClock.elapsedRealtime()
                while (token == generation && capturing &&
                    sent < DebugLanVoiceWireCodec.MAX_FRAMES &&
                    SystemClock.elapsedRealtime() - started < MAX_MS
                ) {
                    var taken = 0
                    while (taken < frame.size && token == generation && capturing) {
                        val bytes = source.read(
                            frame, taken, frame.size - taken, AudioRecord.READ_BLOCKING
                        )
                        if (bytes <= 0) break
                        taken += bytes
                    }
                    if (taken != frame.size) break
                    val accepted = try { onFrame(frame) } catch (_: Exception) { false }
                    frame.fill(0)
                    if (!accepted) break
                    sent++
                }
            } catch (_: Exception) {
                // Mic permissions, devices or lifecycle can fail at any time.
                // Stop cleanly and never fallback to a different transport.
            } finally {
                try { own?.stop() } catch (_: Exception) { }
                try { own?.release() } catch (_: Exception) { }
                synchronized(lock) {
                    if (recorder === own) recorder = null
                    if (token == generation) capturing = false
                }
                frame.fill(0)
                if (token == generation) {
                    try { onStopped(sent > 0) } catch (_: Exception) { }
                }
            }
        }
        return true
    }

    /**
     * F4-D: non-blocking handoff of already validated remote TLS PCM frames.
     * The LAN reader never waits for AudioTrack or audio hardware. Only one
     * remote turn can play at a time and a local mic has priority over play.
     *
     * A returned true means a frame was accepted, NOT that a human heard it.
     */
    fun acceptLiveFrame(
        kind: DebugLanVoiceWireCodec.Kind,
        pcm: ByteArray,
        onPlaybackStarted: (Boolean) -> Unit = {}
    ): Boolean = when (kind) {
        DebugLanVoiceWireCodec.Kind.START -> startLivePlayback(onPlaybackStarted)
        DebugLanVoiceWireCodec.Kind.DATA -> {
            val state = livePlayback
            state != null && state.offer(pcm)
        }
        DebugLanVoiceWireCodec.Kind.END -> {
            livePlayback?.finish()
            true
        }
    }

    private fun startLivePlayback(onPlaybackStarted: (Boolean) -> Unit): Boolean {
        if (!permitted()) return false
        val state: LivePlayback
        synchronized(lock) {
            if (playing || capturing) return false
            generation += 1
            state = LivePlayback(generation)
            livePlayback = state
            playing = true
        }
        thread(name = "eden-debug-live-ptt-speaker", isDaemon = true) {
            var own: AudioTrack? = null
            var started = false
            try {
                val min = AudioTrack.getMinBufferSize(
                    RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
                )
                if (min < 0) return@thread
                val speaker = AudioTrack(
                    AudioManager.STREAM_MUSIC, RATE,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(min, DebugLanVoiceWireCodec.PCM_BYTES * 8),
                    AudioTrack.MODE_STREAM
                )
                own = speaker
                if (speaker.state != AudioTrack.STATE_INITIALIZED) return@thread
                synchronized(lock) {
                    if (state.token != generation || state.cancelled) return@thread
                    track = speaker
                }
                val deadline = SystemClock.elapsedRealtime() + MAX_MS + 1_000L
                while (state.token == generation && !state.cancelled &&
                    SystemClock.elapsedRealtime() < deadline
                ) {
                    val chunk = state.frames.poll(45, TimeUnit.MILLISECONDS)
                    if (chunk != null) {
                        try {
                            if (!started) {
                                speaker.play()
                                started = true
                                if (state.token == generation && !state.cancelled)
                                    try { onPlaybackStarted(true) } catch (_: Exception) { }
                            }
                            val written = speaker.write(chunk, 0, chunk.size)
                            if (written != chunk.size) break
                        } finally { chunk.fill(0) }
                    } else if (state.ended && state.frames.isEmpty()) break
                }
                // Allow a tiny bounded tail to leave AudioTrack after the
                // final queued frame rather than cutting speech on END.
                if (started && !state.cancelled && state.token == generation)
                    Thread.sleep(100)
            } catch (_: Exception) {
                // Device failure is not a transport approval or fake playback.
            } finally {
                if (!started && !state.cancelled && state.token == generation)
                    try { onPlaybackStarted(false) } catch (_: Exception) { }
                try { own?.stop() } catch (_: Exception) { }
                try { own?.release() } catch (_: Exception) { }
                state.cancel()
                synchronized(lock) {
                    if (track === own) track = null
                    if (livePlayback === state) livePlayback = null
                    if (state.token == generation) playing = false
                }
            }
        }
        return true
    }

    /** Called on session teardown, background, navigation or new QR attempt. */
    fun cancelAll() {
        synchronized(lock) {
            generation += 1
            capturing = false
            playing = false
        }
        livePlayback?.cancel()
        livePlayback = null
        try { recorder?.stop() } catch (_: Exception) { }
        try { track?.stop() } catch (_: Exception) { }
        // Workers own final release/byte wiping; no callback survives token.
    }

    companion object {
        private const val RATE = 16_000
        private const val MAX_MS = 3_000L
    }
}
