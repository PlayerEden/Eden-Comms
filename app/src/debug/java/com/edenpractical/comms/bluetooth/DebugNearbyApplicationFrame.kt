package com.edenpractical.comms.bluetooth

import com.edenpractical.comms.protocol.DebugLanVoiceWireCodec
import java.nio.ByteBuffer

/**
 * F4-U: strict selector for TWO debug application frame families inside the
 * SAME previously approved TLS 1.3 session. Not a new permission or channel.
 *
 * ECBT = text/peer-app ACK; ECVP = 16-kHz PCM 20-ms START/DATA/END.
 * No speculative frame types, unknown versions, plaintext fallback or
 * raw Bluetooth/Wi-Fi Direct transport access. A malformed frame is fatal.
 */
internal object DebugNearbyApplicationFrame {
    private const val VOICE_MAGIC = 0x45435650 // ECVP
    enum class Kind { TEXT_OR_ACK, VOICE }

    val MAX_BYTES: Int = maxOf(
        DebugBluetoothTextFrameCodec.MAX_FRAME_BYTES,
        DebugLanVoiceWireCodec.HEADER_BYTES + DebugLanVoiceWireCodec.PCM_BYTES
    )

    fun inspect(bytes: ByteArray): Kind {
        require(bytes.size in DebugBluetoothTextFrameCodec.HEADER_BYTES..MAX_BYTES)
        return if (ByteBuffer.wrap(bytes).int == VOICE_MAGIC) {
            // A successful authenticated TLS record is still NOT authorization
            // to consume malformed or out-of-bound PCM data.
            DebugLanVoiceWireCodec.decode(bytes).clear()
            Kind.VOICE
        } else {
            DebugBluetoothTextFrameCodec.decode(bytes)
            Kind.TEXT_OR_ACK
        }
    }
}
