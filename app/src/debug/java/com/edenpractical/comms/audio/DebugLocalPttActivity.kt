package com.edenpractical.comms.audio

import android.Manifest
import android.app.Activity
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * DEBUG APK ONLY, manual one-phone microphone -> speaker check.
 *
 * Does not create a socket, share audio with peers, save sound to disk, or
 * connect to the unreviewed pairing protocol. No background capture.
 * PCM is discarded on pause/finish; this is not secure RAM erasure.
 */
internal class DebugLocalPttActivity : Activity() {
    private val debugBuild: Boolean
        get() = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    private val currentGeneration = AtomicInteger()
    private val capturing = AtomicBoolean(false)
    private val playing = AtomicBoolean(false)
    private val audioGuard = Any()

    @Volatile private var foreground = false
    @Volatile private var activeRecorder: AudioRecord? = null
    @Volatile private var activeTrack: AudioTrack? = null
    private var recordingThread: Thread? = null
    private var playbackThread: Thread? = null
    private var audioClip: ByteArray? = null

    private lateinit var status: TextView
    private lateinit var hold: Button
    private lateinit var start: Button
    private lateinit var stop: Button
    private lateinit var playback: Button
    private lateinit var stopPlayback: Button
    private lateinit var discard: Button

    companion object {
        private const val AUDIO_REQUEST = 816
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!debugBuild) {
            finish()
            return
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(28))
        }
        content.addView(TextView(this).apply {
            text = "Microphone and speaker check"
            textSize = 22f
        })
        content.addView(TextView(this).apply {
            text = "ONE PHONE ONLY • DEVELOPMENT TEST\n" +
                "This audio never goes to another phone. Normal Voice / PTT " +
                "and production messaging remain disabled."
            textSize = 15f
            setPadding(0, dp(12), 0, dp(12))
        })
        status = TextView(this).apply {
            text = "Ready. A tap can request microphone permission; " +
                "after granting it, press Record again."
            textSize = 16f
            setPadding(0, dp(8), 0, dp(12))
            accessibilityLiveRegion = TextView.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        content.addView(status)
        hold = addButton(content, "HOLD TO RECORD (MAX 5 SECONDS)") {
            if (capturing.get()) stopRecording() else startRecording()
        }
        hold.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startRecording()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopRecording()
                    true
                }
                else -> true
            }
        }
        content.addView(TextView(this).apply {
            text = "For screen readers or without holding: use Start and Stop. " +
                "Release the hold control to stop. Nothing sends automatically."
            setPadding(0, dp(8), 0, dp(10))
        })
        start = addButton(content, "Start recording") { startRecording() }
        stop = addButton(content, "Stop recording") { stopRecording() }
        playback = addButton(content, "Play on THIS phone") { playLocally() }
        stopPlayback = addButton(content, "Stop playback") { stopPlayback() }
        discard = addButton(content, "Discard recording") { discardClip() }
        content.addView(TextView(this).apply {
            text = "Microphone access is requested only when you tap Record. " +
                "Audio lasts at most five seconds and is held in app memory. " +
                "Leaving this screen stops capture/playback and clears the " +
                "recording as best effort. The OS and audio hardware have " +
                "independent buffers; guaranteed erasure is not claimed."
            setPadding(0, dp(16), 0, 0)
        })
        setContentView(ScrollView(this).apply { addView(content) })
        refresh()
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        if (::status.isInitialized) refresh()
    }

    override fun onPause() {
        foreground = false
        terminateAndDiscard()
        super.onPause()
    }

    override fun onDestroy() {
        terminateAndDiscard()
        super.onDestroy()
    }

    private fun startRecording() {
        if (!debugBuild || !foreground || capturing.get() || playing.get() ||
            recordingThread?.isAlive == true || playbackThread?.isAlive == true
        ) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            status.text = "Android microphone permission is required. " +
                "After the permission decision, tap Record again."
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), AUDIO_REQUEST)
            return
        }
        if (!DebugLocalPttPolicy.mayRecord(
                debugBuild, foreground, true, capturing.get(), playing.get()
            )) return
        discardClip()
        val token = currentGeneration.incrementAndGet()
        capturing.set(true)
        status.text = "Recording locally… Release or tap Stop. Maximum five seconds."
        refresh()
        recordingThread = thread(name = "eden-debug-local-audio-record", isDaemon = true) {
            captureWorker(token)
        }
    }

    private fun captureWorker(token: Int) {
        val buffer = ByteArray(DebugLocalPttPolicy.MAX_PCM_BYTES)
        var written = 0
        var recorder: AudioRecord? = null
        var hadError = false
        try {
            val minimum = AudioRecord.getMinBufferSize(
                DebugLocalPttPolicy.SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            if (minimum <= 0) throw IllegalStateException("Microphone format not supported")
            val created = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                DebugLocalPttPolicy.SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minimum, 4_096)
            )
            recorder = created
            if (created.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("Microphone unavailable")
            }
            synchronized(audioGuard) {
                if (!capturing.get() || token != currentGeneration.get() || !foreground) {
                    return
                }
                activeRecorder = created
                created.startRecording()
            }
            val startedAt = SystemClock.elapsedRealtime()
            while (capturing.get() && foreground &&
                token == currentGeneration.get() &&
                written < buffer.size &&
                SystemClock.elapsedRealtime() - startedAt <
                    DebugLocalPttPolicy.MAX_SECONDS * 1_000L
            ) {
                val n = created.read(
                    buffer, written,
                    minOf(4_096, buffer.size - written),
                    AudioRecord.READ_NON_BLOCKING
                )
                when {
                    n < 0 -> throw IllegalStateException("Microphone read failed")
                    n == 0 -> Thread.sleep(DebugLocalPttPlaybackBudget.RETRY_PAUSE_MILLIS)
                    else -> written += n
                }
            }
        } catch (_: Exception) {
            hadError = true
        } finally {
            synchronized(audioGuard) {
                if (activeRecorder === recorder) activeRecorder = null
                try { recorder?.stop() } catch (_: Exception) {}
                try { recorder?.release() } catch (_: Exception) {}
            }
            if (token == currentGeneration.get()) capturing.set(false)
            val clip = if (!hadError &&
                DebugLocalPttPolicy.acceptWorkerResult(
                    token, currentGeneration.get(), foreground
                ) &&
                written >= DebugLocalPttPolicy.MIN_PCM_BYTES
            ) buffer.copyOf(written - written % 2) else null
            buffer.fill(0)
            val worker = Thread.currentThread()
            runOnUiThread {
                if (recordingThread === worker) recordingThread = null
                if (DebugLocalPttPolicy.acceptWorkerResult(
                        token, currentGeneration.get(), foreground
                    )
                ) {
                    audioClip?.fill(0)
                    audioClip = clip
                    status.text = when {
                        hadError -> "Microphone unavailable. Try again or check Android audio settings."
                        clip == null -> "No usable recording. Hold Record a little longer."
                        else -> "Recording ready. Tap Play to hear it on this phone."
                    }
                } else {
                    clip?.fill(0)
                }
                if (foreground && ::status.isInitialized) refresh()
            }
        }
    }

    private fun stopRecording() {
        if (!capturing.getAndSet(false)) return
        synchronized(audioGuard) {
            try { activeRecorder?.stop() } catch (_: Exception) {}
        }
        status.text = "Stopping microphone…"
        refresh()
    }

    private fun playLocally() {
        val original = audioClip ?: return
        if (!DebugLocalPttPolicy.mayPlay(
                debugBuild, foreground, capturing.get(), playing.get(), original.size
            )) return
        // An old output worker must finish teardown before another one can start.
        if (recordingThread?.isAlive == true || playbackThread?.isAlive == true) return
        val packets = DebugLocalPttFrames.fromClip(original) ?: return
        val token = currentGeneration.incrementAndGet()
        playing.set(true)
        status.text = "Playing ordered 20 ms audio frames on THIS phone only…"
        refresh()
        playbackThread = thread(name = "eden-debug-local-audio-play", isDaemon = true) {
            var track: AudioTrack? = null
            var failed = false
            var complete = false
            val budget = DebugLocalPttPlaybackBudget(SystemClock.elapsedRealtime())
            try {
                val minimum = AudioTrack.getMinBufferSize(
                    DebugLocalPttPolicy.SAMPLE_RATE_HZ,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
                )
                if (minimum <= 0) error("Speaker format unavailable")
                val created = AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    DebugLocalPttPolicy.SAMPLE_RATE_HZ,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minimum, DebugLocalPttFrames.FRAME_BYTES * 4),
                    AudioTrack.MODE_STREAM
                )
                track = created
                if (created.state != AudioTrack.STATE_INITIALIZED) {
                    throw IllegalStateException("Audio output unavailable")
                }
                synchronized(audioGuard) {
                    if (!playing.get() || !foreground ||
                        token != currentGeneration.get()
                    ) return@thread
                    activeTrack = created
                    created.play()
                }
                var sentSamples = 0
                var allFramesWritten = true
                // Non-blocking native writes let STOP/background promptly invalidate
                // the attempt even when the output route is stalled.
                while (stillPlaying(token, budget)) {
                    val frame = packets.poll() ?: break
                    val pcm = frame.pcm
                    try {
                        var offset = 0
                        while (offset < pcm.size && stillPlaying(token, budget)) {
                            val n = created.write(
                                pcm, offset, pcm.size - offset,
                                AudioTrack.WRITE_NON_BLOCKING
                            )
                            if (n < 0 || n % 2 != 0) {
                                throw IllegalStateException("Invalid audio output write")
                            }
                            if (n == 0) {
                                Thread.sleep(DebugLocalPttPlaybackBudget.RETRY_PAUSE_MILLIS)
                            } else {
                                offset += n
                            }
                        }
                        if (offset != pcm.size) {
                            allFramesWritten = false
                            break
                        }
                        sentSamples += pcm.size / 2
                    } finally {
                        pcm.fill(0)
                    }
                }
                if (allFramesWritten && packets.queuedCount == 0 &&
                    stillPlaying(token, budget)
                ) {
                    while (created.playbackHeadPosition < sentSamples &&
                        stillPlaying(token, budget)
                    ) {
                        Thread.sleep(DebugLocalPttPlaybackBudget.RETRY_PAUSE_MILLIS)
                    }
                    complete = created.playbackHeadPosition >= sentSamples &&
                        stillPlaying(token, budget)
                }
            } catch (_: Exception) {
                if (playing.get() && token == currentGeneration.get()) failed = true
            } finally {
                budget.close()
                packets.close()
                synchronized(audioGuard) {
                    if (activeTrack === track) activeTrack = null
                    try { track?.stop() } catch (_: Exception) {}
                    try { track?.release() } catch (_: Exception) {}
                }
                if (token == currentGeneration.get()) playing.set(false)
                val worker = Thread.currentThread()
                runOnUiThread {
                    if (playbackThread === worker) playbackThread = null
                    if (DebugLocalPttPolicy.acceptWorkerResult(
                            token, currentGeneration.get(), foreground
                        )
                    ) {
                        status.text = when {
                            failed -> "Couldn't play sound on this phone."
                            complete -> "Local frame playback finished. Nothing transmitted."
                            else -> "Playback stopped or output timed out. Nothing transmitted."
                        }
                    }
                    if (foreground && ::status.isInitialized) refresh()
                }
            }
        }
    }

    private fun stillPlaying(
        token: Int, budget: DebugLocalPttPlaybackBudget
    ): Boolean = playing.get() && foreground &&
        token == currentGeneration.get() &&
        budget.mayContinue(SystemClock.elapsedRealtime())

    private fun stopPlayback() {
        if (!playing.getAndSet(false)) return
        // Cancel any pending worker status; "Stopped" must not turn into "Finished".
        currentGeneration.incrementAndGet()
        synchronized(audioGuard) {
            try { activeTrack?.stop() } catch (_: Exception) {}
        }
        status.text = "Playback stopped."
        refresh()
    }

    private fun discardClip() {
        audioClip?.fill(0)
        audioClip = null
        if (::status.isInitialized) {
            status.text = "Local recording discarded."
            refresh()
        }
    }

    private fun terminateAndDiscard() {
        currentGeneration.incrementAndGet()
        capturing.set(false)
        playing.set(false)
        synchronized(audioGuard) {
            try { activeRecorder?.stop() } catch (_: Exception) {}
            try { activeTrack?.stop() } catch (_: Exception) {}
        }
        audioClip?.fill(0)
        audioClip = null
        if (::status.isInitialized) {
            status.text = "Test stopped. Audio cleared as best effort."
            refresh()
        }
    }

    private fun refresh() {
        if (!::hold.isInitialized) return
        start.isEnabled = foreground && !capturing.get() && !playing.get() &&
            recordingThread?.isAlive != true && playbackThread?.isAlive != true
        stop.isEnabled = foreground && capturing.get()
        hold.isEnabled = foreground && !playing.get() &&
            playbackThread?.isAlive != true
        playback.isEnabled = foreground && !capturing.get() && !playing.get() &&
            recordingThread?.isAlive != true && playbackThread?.isAlive != true &&
            audioClip != null
        stopPlayback.isEnabled = foreground && playing.get()
        discard.isEnabled = foreground && !capturing.get() &&
            !playing.get() && playbackThread?.isAlive != true && audioClip != null
    }

    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == AUDIO_REQUEST && ::status.isInitialized && foreground) {
            val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
            status.text = if (granted) {
                "Microphone allowed. Press Record again; permission never auto-starts capture."
            } else {
                "Microphone not allowed. No recording started."
            }
        }
    }

    private fun addButton(parent: LinearLayout, label: String, action: () -> Unit): Button {
        return Button(this).also { button ->
            button.text = label
            button.minHeight = dp(56)
            button.setOnClickListener { action() }
            parent.addView(button)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()
}
