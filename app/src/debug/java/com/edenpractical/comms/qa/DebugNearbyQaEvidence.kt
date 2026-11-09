package com.edenpractical.comms.qa

import com.edenpractical.comms.bluetooth.DebugBluetoothApprovedTextChannel
import com.edenpractical.comms.protocol.DebugLanVoiceWireCodec

/**
 * M6-B: RAM-only, locally requested redacted QA counts for an already
 * approved Bluetooth/Wi-Fi Direct test conversation. NEVER records peer
 * identity, QR, TLS bytes, message text, PCM, contact info or IP addresses.
 *
 * Counters are local observations, not evidence that the remote person saw
 * text, heard speech, or that this protocol is independently secure.
 * No network/telemetry, files, clipboard or Android permissions here.
 */
internal class DebugNearbyQaEvidence(private val method: String) {
    init { require(method in setOf("Bluetooth", "Wi-Fi Direct • host", "Wi-Fi Direct • join")) }

    private var started = false
    private var active = false
    private var textWritten = 0
    private var textReceived = 0
    private var peerAckObserved = 0
    private var floorGrants = 0
    private var floorDenials = 0
    private var sentPcmFrames = 0
    private var liveVoiceStarts = 0
    private var liveVoiceFrames = 0
    private var liveVoiceEnds = 0

    /** Called only for events from the current authenticated session owner. */
    @Synchronized
    fun observe(event: DebugBluetoothApprovedTextChannel.Event) {
        when (event) {
            DebugBluetoothApprovedTextChannel.Event.Ready -> {
                reset()
                started = true
                active = true
            }
            DebugBluetoothApprovedTextChannel.Event.Failed -> active = false
            else -> {
                if (!active) return
                when (event) {
                    is DebugBluetoothApprovedTextChannel.Event.Written -> textWritten++
                    is DebugBluetoothApprovedTextChannel.Event.Received -> textReceived++
                    is DebugBluetoothApprovedTextChannel.Event.Acknowledged -> peerAckObserved++
                    DebugBluetoothApprovedTextChannel.Event.FloorGranted -> floorGrants++
                    DebugBluetoothApprovedTextChannel.Event.FloorDenied -> floorDenials++
                    is DebugBluetoothApprovedTextChannel.Event.VoiceWritten ->
                        sentPcmFrames += event.frames.coerceIn(0, 150)
                    is DebugBluetoothApprovedTextChannel.Event.LiveVoiceFrame ->
                        when (event.kind) {
                            DebugLanVoiceWireCodec.Kind.START -> liveVoiceStarts++
                            DebugLanVoiceWireCodec.Kind.DATA -> liveVoiceFrames++
                            DebugLanVoiceWireCodec.Kind.END -> liveVoiceEnds++
                        }
                    else -> Unit
                }
            }
        }
    }

    @Synchronized
    fun end() { active = false }

    @Synchronized
    fun hasObservation(): Boolean = started

    /** No text bodies or PCM are concatenated; all values are numbers/status. */
    @Synchronized
    fun summary(): String = buildString {
        appendLine("Eden Comms — local debug communication test")
        appendLine("Method: $method")
        appendLine("Verified-channel event seen: ${if (started) "yes" else "no"}")
        appendLine("Channel still active locally: ${if (active) "yes" else "no"}")
        appendLine("Text written to TLS here: $textWritten")
        appendLine("Text received here: $textReceived")
        appendLine("Peer-app ACK observed here: $peerAckObserved")
        appendLine("Voice floor grants observed here: $floorGrants")
        appendLine("Voice floor denials observed here: $floorDenials")
        appendLine("Voice PCM frames written to TLS here: $sentPcmFrames")
        appendLine("Inbound voice START frames: $liveVoiceStarts")
        appendLine("Inbound voice DATA frames: $liveVoiceFrames")
        appendLine("Inbound voice END frames: $liveVoiceEnds")
        append("LOCAL OBSERVATIONS ONLY: not a delivery/read receipt, audible speech " +
            "confirmation, phone interoperability certification, or security audit.")
    }

    private fun reset() {
        started = false
        active = false
        textWritten = 0
        textReceived = 0
        peerAckObserved = 0
        floorGrants = 0
        floorDenials = 0
        sentPcmFrames = 0
        liveVoiceStarts = 0
        liveVoiceFrames = 0
        liveVoiceEnds = 0
    }
}
