package com.edenpractical.comms.transport

import com.edenpractical.comms.protocol.TestPacketCodec
import com.edenpractical.comms.security.SessionProofBinding
import com.edenpractical.comms.security.SessionProofExchange
import java.io.IOException
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two simulated endpoints using JVM pipes. This exercises real debug-session
 * QR proof and packet codecs WITHOUT pretending JVM streams are Android TLS,
 * Wi-Fi, Keystore or a physical two-phone security test.
 */
class DebugTwoEndpointTranscriptTest {
    private val codec = TestPacketCodec

    private fun binding(joinFingerprint: Byte = 0x22) = SessionProofBinding(
        ByteArray(32) { 0x11 },
        ByteArray(32) { joinFingerprint },
        ByteArray(16) { 0x33 },
        ByteArray(16) { 0x44 }
    )

    private data class Outcome(
        val hostReceived: List<TestPacketCodec.Packet>,
        val joinReceived: List<TestPacketCodec.Packet>,
        val hostSucceeded: Boolean,
        val joinSucceeded: Boolean
    )

    private fun exchange(
        hostBinding: SessionProofBinding,
        joinBinding: SessionProofBinding,
        joinSendsInvalidReady: Boolean = false
    ): Outcome {
        val hostToJoin = PipedOutputStream()
        val joinInput = PipedInputStream(hostToJoin, 2048)
        val joinToHost = PipedOutputStream()
        val hostInput = PipedInputStream(joinToHost, 2048)
        val pool = Executors.newFixedThreadPool(2)
        return try {
            val host = pool.submit<Pair<Boolean, List<TestPacketCodec.Packet>>> {
                val received = mutableListOf<TestPacketCodec.Packet>()
                try {
                    SessionProofExchange.runHost(hostInput, hostToJoin, hostBinding)
                    codec.write(hostToJoin, codec.ready())
                    if (codec.read(hostInput) != codec.ready()) throw IOException("Not ready")
                    for (id in 1..4) codec.write(hostToJoin, codec.preset(id))
                    repeat(4) { received += codec.read(hostInput) }
                    codec.write(hostToJoin, codec.close())
                    true to received
                } catch (_: Exception) {
                    false to received
                } finally {
                    hostToJoin.close()
                    hostInput.close()
                }
            }
            val join = pool.submit<Pair<Boolean, List<TestPacketCodec.Packet>>> {
                val received = mutableListOf<TestPacketCodec.Packet>()
                try {
                    SessionProofExchange.runJoin(joinInput, joinToHost, joinBinding)
                    if (codec.read(joinInput) != codec.ready()) throw IOException("Not ready")
                    codec.write(joinToHost,
                        if (joinSendsInvalidReady) codec.preset(1) else codec.ready())
                    if (joinSendsInvalidReady) return@submit true to received
                    repeat(4) { received += codec.read(joinInput) }
                    for (id in 4 downTo 1) codec.write(joinToHost, codec.preset(id))
                    if (codec.read(joinInput) != codec.close()) throw IOException("No close")
                    true to received
                } catch (_: Exception) {
                    false to received
                } finally {
                    joinToHost.close()
                    joinInput.close()
                }
            }
            val h = host.get(5, TimeUnit.SECONDS)
            val j = join.get(5, TimeUnit.SECONDS)
            Outcome(h.second, j.second, h.first, j.first)
        } finally {
            try { hostToJoin.close() } catch (_: IOException) {}
            try { joinToHost.close() } catch (_: IOException) {}
            try { hostInput.close() } catch (_: IOException) {}
            try { joinInput.close() } catch (_: IOException) {}
            pool.shutdownNow()
        }
    }

    @Test fun eachSideReceivesFourRemotePhrasesOnlyAfterProofAndReady() {
        val outcome = exchange(binding(), binding())
        assertTrue("Host failed simulated transcript", outcome.hostSucceeded)
        assertTrue("Joiner failed simulated transcript", outcome.joinSucceeded)
        assertEquals((4 downTo 1).map(codec::preset), outcome.hostReceived)
        assertEquals((1..4).map(codec::preset), outcome.joinReceived)
    }

    @Test fun wrongPeerFingerprintNeverReachesTestPhraseExchange() {
        val outcome = exchange(binding(), binding(joinFingerprint = 0x55))
        assertFalse(outcome.hostSucceeded)
        assertFalse(outcome.joinSucceeded)
        assertTrue(outcome.hostReceived.isEmpty())
        assertTrue(outcome.joinReceived.isEmpty())
    }

    @Test fun unexpectedEarlyPresetCannotBecomeReadyHandshake() {
        val outcome = exchange(binding(), binding(), joinSendsInvalidReady = true)
        assertFalse(outcome.hostSucceeded)
        assertTrue(outcome.hostReceived.isEmpty())
        assertTrue(outcome.joinReceived.isEmpty())
    }
}
