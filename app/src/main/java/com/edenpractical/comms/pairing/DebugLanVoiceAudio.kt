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

    private fun permitted(): Boolean =
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    @Suppress("MissingPermission")
    fun startCapture(onComplete: (ByteArray) -> Unit): Boolean {
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
                if (token == generation && used >= DebugLanVoiceWireCodec.PCM_BYTES) {
                    val clip = recording.copyOf(used)
                    try { onComplete(clip) } finally { clip.fill(0) }
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
    fun playReceived(incoming: ByteArray): Boolean {
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
                val endAt = SystemClock.elapsedRealtime() +
                    copy.size / (RATE * 2 / 1000) + 120
                while (token == generation && playing &&
                    SystemClock.elapsedRealtime() < endAt
                ) Thread.sleep(20)
            } catch (_: Exception) {
                // Device-specific playback errors never save sound or enable
                // a different transport.
            } finally {
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

    /** Called on session teardown, background, navigation or new QR attempt. */
    fun cancelAll() {
        synchronized(lock) {
            generation += 1
            capturing = false
            playing = false
        }
        try { recorder?.stop() } catch (_: Exception) { }
        try { track?.stop() } catch (_: Exception) { }
        // Workers own final release/byte wiping; no callback survives token.
    }

    companion object {
        private const val RATE = 16_000
        private const val MAX_MS = 3_000L
    }
}
