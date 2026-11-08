package com.edenpractical.comms.bluetooth

import javax.net.ssl.SSLEngineResult
import javax.net.ssl.SSLException
import org.junit.Assert.assertThrows
import org.junit.Test

class DebugTlsProviderReadinessTest {
    private val idle = SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING

    private fun check(
        protocol: String = "TLSv1.3",
        handshake: SSLEngineResult.HandshakeStatus = idle,
        inboundDone: Boolean = false,
        outboundDone: Boolean = false,
        packetBytes: Int = 17_000,
        applicationBytes: Int = 17_000
    ) = DebugTlsProviderReadiness.requireControlState(
        protocol, handshake, inboundDone, outboundDone, packetBytes,
        applicationBytes
    )

    @Test fun onlyLiveNegotiatedBoundedTls13CanCarryDebugControls() {
        check()
        check(packetBytes = 1, applicationBytes = 1)
        check(packetBytes = 32_768, applicationBytes = 32_768)
        for (protocol in listOf("NONE", "TLSv1.2", "TLSv1.1", "TLSv1.4", "")) {
            assertThrows(SSLException::class.java) { check(protocol = protocol) }
        }
        for (status in SSLEngineResult.HandshakeStatus.values()) {
            if (status != idle) {
                assertThrows(SSLException::class.java) { check(handshake = status) }
            }
        }
    }

    @Test fun halfClosedEngineOrOversizedProviderBuffersFailClosed() {
        for ((incoming, outgoing) in listOf(
            true to false, false to true, true to true
        )) {
            assertThrows(SSLException::class.java) {
                check(inboundDone = incoming, outboundDone = outgoing)
            }
        }
        for (bad in listOf(-1, 0, 32_769, Int.MAX_VALUE)) {
            assertThrows(SSLException::class.java) { check(packetBytes = bad) }
            assertThrows(SSLException::class.java) { check(applicationBytes = bad) }
        }
    }

    @Test fun tlsOnlyRecordCanFinishTransientlyButNeverCarryControlPayload() {
        val idle = SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING
        val finished = SSLEngineResult.HandshakeStatus.FINISHED
        for (state in listOf(idle, finished)) {
            val empty = SSLEngineResult(SSLEngineResult.Status.OK, state, 1031, 0)
            DebugTlsProviderReadiness.requireEmptyTlsOnlyResult(empty, consumed = 1031)
            if (state == finished) {
                assertThrows(SSLException::class.java) {
                    DebugTlsProviderReadiness.requireExactControlResult(empty, 1031, 0)
                }
            }
        }
        for (state in SSLEngineResult.HandshakeStatus.values()) {
            if (state != idle && state != finished) {
                val disallowed = SSLEngineResult(SSLEngineResult.Status.OK, state, 1031, 0)
                assertThrows(SSLException::class.java) {
                    DebugTlsProviderReadiness.requireEmptyTlsOnlyResult(disallowed, 1031)
                }
            }
        }
        for (bad in listOf(
            SSLEngineResult(SSLEngineResult.Status.OK, finished, 1031, 1),
            SSLEngineResult(SSLEngineResult.Status.OK, finished, 1030, 0),
            SSLEngineResult(SSLEngineResult.Status.BUFFER_OVERFLOW, finished, 0, 0),
            SSLEngineResult(SSLEngineResult.Status.CLOSED, finished, 1031, 0)
        )) {
            assertThrows(SSLException::class.java) {
                DebugTlsProviderReadiness.requireEmptyTlsOnlyResult(bad, 1031)
            }
        }
    }

    @Test fun engineResultRequiresExactLengthsAndNoNewHandshake() {
        fun result(
            status: SSLEngineResult.Status = SSLEngineResult.Status.OK,
            handshake: SSLEngineResult.HandshakeStatus = idle,
            read: Int = 39,
            written: Int = 39
        ) = SSLEngineResult(status, handshake, read, written)

        DebugTlsProviderReadiness.requireExactControlResult(
            result(), consumed = 39, produced = 39
        )
        for (status in SSLEngineResult.Status.values()) {
            if (status != SSLEngineResult.Status.OK) {
                assertThrows(SSLException::class.java) {
                    DebugTlsProviderReadiness.requireExactControlResult(
                        result(status = status), 39, 39
                    )
                }
            }
        }
        for (handshake in SSLEngineResult.HandshakeStatus.values()) {
            if (handshake != idle) {
                assertThrows(SSLException::class.java) {
                    DebugTlsProviderReadiness.requireExactControlResult(
                        result(handshake = handshake), 39, 39
                    )
                }
            }
        }
        assertThrows(SSLException::class.java) {
            DebugTlsProviderReadiness.requireExactControlResult(result(read = 38), 39, 39)
        }
        assertThrows(SSLException::class.java) {
            DebugTlsProviderReadiness.requireExactControlResult(result(written = 38), 39, 39)
        }
    }
}
