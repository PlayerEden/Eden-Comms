package com.edenpractical.comms.bluetooth

import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult
import javax.net.ssl.SSLException

/**
 * F2-C: strict provider-state preflight for the existing DEBUG-ONLY fixed-width
 * RFCOMM TLS control diagnostic. This does not authorize user data or extend
 * the current 45-second, auto-closing Bluetooth socket.
 *
 * Android/JVM SSLEngine may process handshake or closure state in wrap/unwrap.
 * A peer can also request TLS 1.3 post-handshake operations such as KeyUpdate.
 * Our diagnostic does not implement those transitions and must fail closed.
 */
internal object DebugTlsProviderReadiness {
    fun requireControlReady(engine: SSLEngine) {
        val session = engine.session
        requireControlState(
            protocol = session.protocol,
            handshake = engine.handshakeStatus,
            inboundDone = engine.isInboundDone,
            outboundDone = engine.isOutboundDone,
            packetBytes = session.packetBufferSize,
            applicationBytes = session.applicationBufferSize
        )
    }

    /** Pure input form for deterministic JVM permutations; not trust evidence. */
    fun requireControlState(
        protocol: String,
        handshake: SSLEngineResult.HandshakeStatus,
        inboundDone: Boolean,
        outboundDone: Boolean,
        packetBytes: Int,
        applicationBytes: Int
    ) {
        if (protocol != "TLSv1.3" ||
            handshake != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING ||
            inboundDone || outboundDone ||
            packetBytes !in 1..DebugTlsEngineHandshake.MAX_ENGINE_PACKET ||
            applicationBytes !in 1..DebugTlsEngineHandshake.MAX_ENGINE_PACKET
        ) throw SSLException("Unsupported Bluetooth TLS control provider state")
    }

    fun requireExactControlResult(
        result: SSLEngineResult,
        consumed: Int,
        produced: Int
    ) {
        if (result.status != SSLEngineResult.Status.OK ||
            result.handshakeStatus != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING ||
            result.bytesConsumed() != consumed ||
            result.bytesProduced() != produced
        ) throw SSLException("Unsupported Bluetooth TLS control engine result")
    }
}
