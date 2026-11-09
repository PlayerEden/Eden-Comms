package com.edenpractical.comms.bluetooth

import com.edenpractical.comms.security.PinnedTlsPeer
import com.edenpractical.comms.security.Tls13PeerContext
import java.io.Closeable
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509KeyManager
import org.junit.AfterClass
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * F2-D: REAL JVM TLS 1.3 engines and two distinct, ephemeral X.509
 * identities over blocking, split-read-capable, in-memory byte pipes.
 *
 * Only a CI JVM simulation, not Android TLS provider/RFCOMM, hardware,
 * independent security verification, approval, or message authorization.
 * Keys are generated inside a temporary test directory by the JDK keytool.
 * No test certificates or private keys are committed to the repository.
 */
class DebugPairedTlsEngineInteropTest {
    companion object {
        private const val TEST_PASSWORD = "ephemeral-test-only"
        private lateinit var directory: Path
        private lateinit var hostIdentity: Identity
        private lateinit var joinIdentity: Identity

        private data class Identity(val manager: X509KeyManager, val pin: ByteArray)

        @BeforeClass @JvmStatic
        fun makeEphemeralIdentities() {
            directory = Files.createTempDirectory("eden-f2d-test-only-")
            hostIdentity = createIdentity("host")
            joinIdentity = createIdentity("join")
            assertTrue(!hostIdentity.pin.contentEquals(joinIdentity.pin))
        }

        @AfterClass @JvmStatic
        fun wipeEphemeralFiles() {
            if (!::directory.isInitialized) return
            for (alias in listOf("host", "join")) {
                Files.deleteIfExists(directory.resolve("${alias}.p12"))
            }
            Files.deleteIfExists(directory)
        }

        private fun createIdentity(alias: String): Identity {
            val storePath = directory.resolve("${alias}.p12")
            val keytool = Path.of(
                System.getProperty("java.home"), "bin",
                if (System.getProperty("os.name").startsWith("Windows")) "keytool.exe" else "keytool"
            ).toString()
            val command = listOf(
                keytool, "-genkeypair", "-noprompt", "-alias", alias,
                "-keyalg", "EC", "-keysize", "256", "-validity", "2",
                "-dname", "CN=Eden Comms F2-D ${alias} TEST ONLY",
                "-keystore", storePath.toString(), "-storetype", "PKCS12",
                "-storepass", TEST_PASSWORD, "-keypass", TEST_PASSWORD
            )
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                throw AssertionError("Temporary JVM test keytool invocation timed out")
            }
            assertEquals("JVM test keytool failed", 0, process.exitValue())
            val store = KeyStore.getInstance("PKCS12")
            Files.newInputStream(storePath).use {
                store.load(it, TEST_PASSWORD.toCharArray())
            }
            val factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            factory.init(store, TEST_PASSWORD.toCharArray())
            val km = factory.keyManagers.filterIsInstance<X509KeyManager>().single()
            val certificate = store.getCertificate(alias) as X509Certificate
            return Identity(km, PinnedTlsPeer.fingerprint(certificate))
        }
    }

    private class PairPipes : Closeable {
        private val towardJoin = PipedInputStream(65_536)
        private val towardHost = PipedInputStream(65_536)
        val hostInput: PipedInputStream = towardHost
        val hostOutput = PipedOutputStream(towardJoin)
        val joinInput: PipedInputStream = towardJoin
        val joinOutput = PipedOutputStream(towardHost)

        override fun close() {
            for (stream in listOf(hostOutput, joinOutput, hostInput, joinInput)) {
                try { stream.close() } catch (_: Exception) { }
            }
        }
    }

    private fun connect(
        hostExpected: ByteArray = joinIdentity.pin,
        joinExpected: ByteArray = hostIdentity.pin
    ): Pair<SSLEngine, SSLEngine> = Pair(
        Tls13PeerContext(hostIdentity.manager, hostExpected).unconnectedEngine(client = false),
        Tls13PeerContext(joinIdentity.manager, joinExpected).unconnectedEngine(client = true)
    )

    private fun <T> await(future: Future<T>): T = future.get(15, TimeUnit.SECONDS)

    @Test
    fun realJvmEnginesMutuallyPinTls13AndExchangeAllFixedEncryptedControlLengths() {
        val (host, join) = connect()
        val pipe = PairPipes()
        val workers = Executors.newFixedThreadPool(2) { runnable ->
            Thread(runnable, "EC-f2d-jvm-test-only").apply { isDaemon = true }
        }
        try {
            val a = workers.submit(Callable {
                DebugTlsEngineHandshake.negotiate(host, pipe.hostInput, pipe.hostOutput)
                PinnedTlsPeer.requirePinnedTls13(host.session, joinIdentity.pin)
                DebugTlsProviderReadiness.requireControlReady(host)
            })
            val b = workers.submit(Callable {
                DebugTlsEngineHandshake.negotiate(join, pipe.joinInput, pipe.joinOutput)
                PinnedTlsPeer.requirePinnedTls13(join.session, hostIdentity.pin)
                DebugTlsProviderReadiness.requireControlReady(join)
            })
            await(a)
            await(b)
            assertEquals("TLSv1.3", host.session.protocol)
            assertEquals("TLSv1.3", join.session.protocol)

            val h = DebugTlsControlTransport(host, pipe.hostInput, pipe.hostOutput)
            val j = DebugTlsControlTransport(join, pipe.joinInput, pipe.joinOutput)
            val phases = DebugTlsControlTransport.Phase.values()
            for (i in phases.indices) {
                val phase = phases[i]
                if (i > 0) {
                    h.advance(phases[i - 1], phase)
                    j.advance(phases[i - 1], phase)
                }
                val outgoingHost = ByteArray(phase.bytes) { k -> (k + i + 17).toByte() }
                val incomingJoin = ByteArray(phase.bytes)
                h.output.write(outgoingHost)
                assertEquals(phase.bytes, j.input.read(incomingJoin))
                assertArrayEquals(outgoingHost, incomingJoin)

                val outgoingJoin = ByteArray(phase.bytes) { k -> (k + i + 53).toByte() }
                val incomingHost = ByteArray(phase.bytes)
                j.output.write(outgoingJoin)
                assertEquals(phase.bytes, h.input.read(incomingHost))
                assertArrayEquals(outgoingJoin, incomingHost)
            }
            h.clear()
            j.clear()
            assertThrows(javax.net.ssl.SSLException::class.java) {
                h.output.write(ByteArray(39))
            }
        } finally {
            pipe.close()
            workers.shutdownNow()
            try { host.closeOutbound() } catch (_: Exception) { }
            try { join.closeOutbound() } catch (_: Exception) { }
        }
    }

    /**
     * F2-G: actual JVM TLS 1.3 encryption in both directions, reusing the
     * real mutual certificate pin/handshake fixture from F2-D. Synthetic
     * test traffic only: no QR, exporter, human consent, Android or radio.
     */
    @Test
    fun pairedTlsEnginesExchangeEncryptedTextAndPeerAppAckBothDirections() {
        val (host, join) = connect()
        val pipe = PairPipes()
        val workers = Executors.newFixedThreadPool(2) { runnable ->
            Thread(runnable, "EC-f2g-jvm-test-only").apply { isDaemon = true }
        }
        try {
            val a = workers.submit(Callable {
                DebugTlsEngineHandshake.negotiate(host, pipe.hostInput, pipe.hostOutput)
                PinnedTlsPeer.requirePinnedTls13(host.session, joinIdentity.pin)
                DebugTlsProviderReadiness.requireControlReady(host)
            })
            val b = workers.submit(Callable {
                DebugTlsEngineHandshake.negotiate(join, pipe.joinInput, pipe.joinOutput)
                PinnedTlsPeer.requirePinnedTls13(join.session, hostIdentity.pin)
                DebugTlsProviderReadiness.requireControlReady(join)
            })
            await(a)
            await(b)

            val hostLab = DebugBluetoothFutureTlsTextLab(host, pipe.hostInput, pipe.hostOutput)
            val joinLab = DebugBluetoothFutureTlsTextLab(join, pipe.joinInput, pipe.joinOutput)
            val hostMessages = DebugBluetoothTextLedger()
            val joinMessages = DebugBluetoothTextLedger()
            try {
                val fromHost = hostMessages.prepareText("Host says hello 👋")
                try {
                    hostLab.transmit(fromHost)
                } finally {
                    fromHost.fill(0)
                }
                val receivedOnJoin = joinLab.receive()
                val accepted = try {
                    joinMessages.acceptText(receivedOnJoin)
                } finally {
                    receivedOnJoin.fill(0)
                }
                assertEquals("Host says hello 👋", accepted.text)
                assertEquals(1, hostMessages.outstanding())
                try {
                    joinLab.transmit(accepted.ack)
                } finally {
                    accepted.ack.fill(0)
                }
                val hostAck = hostLab.receive()
                try {
                    assertEquals(1L, hostMessages.acceptAck(hostAck))
                } finally {
                    hostAck.fill(0)
                }
                assertEquals(0, hostMessages.outstanding())

                val fromJoin = joinMessages.prepareText("Join replies — hej!")
                try {
                    joinLab.transmit(fromJoin)
                } finally {
                    fromJoin.fill(0)
                }
                val receivedOnHost = hostLab.receive()
                val acceptedOnHost = try {
                    hostMessages.acceptText(receivedOnHost)
                } finally {
                    receivedOnHost.fill(0)
                }
                assertEquals("Join replies — hej!", acceptedOnHost.text)
                try {
                    hostLab.transmit(acceptedOnHost.ack)
                } finally {
                    acceptedOnHost.ack.fill(0)
                }
                val joinAck = joinLab.receive()
                try {
                    assertEquals(1L, joinMessages.acceptAck(joinAck))
                } finally {
                    joinAck.fill(0)
                }
                assertEquals(0, joinMessages.outstanding())

                // A valid TLS engine is not authorization for malformed
                // plaintext or for continued use of a failed lab instance.
                assertThrows(IllegalArgumentException::class.java) {
                    hostLab.transmit(byteArrayOf(1, 2, 3))
                }
                assertThrows(javax.net.ssl.SSLException::class.java) {
                    hostLab.transmit(DebugBluetoothTextFrameCodec.acknowledgment(1))
                }
                joinLab.clear()
                assertThrows(javax.net.ssl.SSLException::class.java) {
                    joinLab.transmit(DebugBluetoothTextFrameCodec.acknowledgment(1))
                }
            } finally {
                hostLab.clear()
                joinLab.clear()
                hostMessages.close()
                joinMessages.close()
            }
        } finally {
            pipe.close()
            workers.shutdownNow()
            try { host.closeOutbound() } catch (_: Exception) { }
            try { join.closeOutbound() } catch (_: Exception) { }
        }
    }

    @Test
    fun pairedTlsLabRejectsMalformedEncryptedRecordAndClosesItself() {
        val (host, join) = connect()
        val pipe = PairPipes()
        val workers = Executors.newFixedThreadPool(2) { runnable ->
            Thread(runnable, "EC-f2g-negative-jvm-test-only").apply { isDaemon = true }
        }
        try {
            val a = workers.submit(Callable {
                DebugTlsEngineHandshake.negotiate(host, pipe.hostInput, pipe.hostOutput)
                PinnedTlsPeer.requirePinnedTls13(host.session, joinIdentity.pin)
            })
            val b = workers.submit(Callable {
                DebugTlsEngineHandshake.negotiate(join, pipe.joinInput, pipe.joinOutput)
                PinnedTlsPeer.requirePinnedTls13(join.session, hostIdentity.pin)
            })
            await(a)
            await(b)
            val joinLab = DebugBluetoothFutureTlsTextLab(join, pipe.joinInput, pipe.joinOutput)
            try {
                // Valid TLS record envelope, but forged/invalid encrypted body.
                pipe.hostOutput.write(byteArrayOf(23, 3, 3, 0, 1, 0))
                pipe.hostOutput.flush()
                assertThrows(javax.net.ssl.SSLException::class.java) { joinLab.receive() }
                assertThrows(javax.net.ssl.SSLException::class.java) { joinLab.receive() }
            } finally {
                joinLab.clear()
            }
        } finally {
            pipe.close()
            workers.shutdownNow()
            try { host.closeOutbound() } catch (_: Exception) { }
            try { join.closeOutbound() } catch (_: Exception) { }
        }
    }

    /**
     * F2-H: a synthetic future owner actually closes its owned encrypted
     * JVM lab streams and message state on deadline. It grants no radio/chat
     * authority and supplies no cryptographic evidence for human consent.
     */
    @Test
    fun futureSessionOwnerExpiresAndClosesPairedTlsLabResources() {
        val (host, join) = connect()
        val pipe = PairPipes()
        val workers = Executors.newFixedThreadPool(2) { runnable ->
            Thread(runnable, "EC-f2h-future-owner-only").apply { isDaemon = true }
        }
        try {
            val hostHandshake = workers.submit(Callable {
                DebugTlsEngineHandshake.negotiate(host, pipe.hostInput, pipe.hostOutput)
                PinnedTlsPeer.requirePinnedTls13(host.session, joinIdentity.pin)
            })
            val joinHandshake = workers.submit(Callable {
                DebugTlsEngineHandshake.negotiate(join, pipe.joinInput, pipe.joinOutput)
                PinnedTlsPeer.requirePinnedTls13(join.session, hostIdentity.pin)
            })
            await(hostHandshake)
            await(joinHandshake)
            val h = DebugBluetoothFutureTlsTextLab(host, pipe.hostInput, pipe.hostOutput)
            val j = DebugBluetoothFutureTlsTextLab(join, pipe.joinInput, pipe.joinOutput)
            val sent = DebugBluetoothTextLedger()
            val received = DebugBluetoothTextLedger()
            val owner = DebugBluetoothFutureOwnedSession(
                DebugBluetoothContinuationBudget(maxLifetimeMs = 5)
            )
            val cleanup = Closeable {
                h.clear()
                j.clear()
                sent.close()
                received.close()
                pipe.close()
            }
            val observations = DebugBluetoothContinuationBudget.Observations(
                peerPinChecked = true,
                qrChallengeChecked = true,
                exporterChecked = true,
                bilateralApprovals = true,
                bilateralConfirmations = true,
                foreground = true,
                connectPermission = true
            )
            // Correlation-only fixture: these bytes do NOT assert that the
            // synthetic JVM peers completed real Bluetooth QR/consent.
            val reviewContext = DebugBluetoothFutureSessionContext(
                hostRole = true,
                expectedPeerPin = joinIdentity.pin,
                transcriptCommitment = ByteArray(32) { 0x45 }
            )
            try {
                assertTrue(owner.begin(42, 100, observations, cleanup, reviewContext))
                assertTrue(owner.observe(42, 104, true, true, reviewContext))
                val data = sent.prepareText("One encrypted text before expiry")
                try {
                    h.transmit(data)
                } finally {
                    data.fill(0)
                }
                val decoded = j.receive()
                try {
                    assertEquals(
                        "One encrypted text before expiry",
                        received.acceptText(decoded).text
                    )
                } finally {
                    decoded.fill(0)
                }
                assertEquals(1, sent.outstanding())
                assertTrue(!owner.observe(42, 105, true, true, reviewContext))
                assertEquals(0, sent.outstanding())
                assertThrows(javax.net.ssl.SSLException::class.java) {
                    h.transmit(DebugBluetoothTextFrameCodec.acknowledgment(1))
                }
                assertThrows(java.io.IOException::class.java) {
                    pipe.hostOutput.write(1)
                }
                assertTrue(!owner.stop(42))
            } finally {
                owner.close()
                reviewContext.close()
            }
        } finally {
            pipe.close()
            workers.shutdownNow()
            try { host.closeOutbound() } catch (_: Exception) { }
            try { join.closeOutbound() } catch (_: Exception) { }
        }
    }


    /**
     * F2-N: REAL JDK TLS 1.3 engines and ONE pair of continuous byte pipes
     * survive the transition from encrypted fixed-width control records to
     * separately encoded encrypted TEXT / peer-app ACK frames.
     *
     * Control bytes here are synthetic. This DOES NOT run the real QR proof,
     * exporter, transcript agreement, human approval or Android RFCOMM path;
     * neither these test booleans nor this transfer authorize messaging.
     */
    @Test
    fun sameTlsEnginesSurviveControlToTextAckThenFutureOwnersTearDown() {
        val (host, join) = connect()
        val pipe = PairPipes()
        val workers = Executors.newFixedThreadPool(2) { runnable ->
            Thread(runnable, "EC-f2n-same-engine-jvm-only").apply { isDaemon = true }
        }
        try {
            val hostHandshake = workers.submit(Callable {
                DebugTlsEngineHandshake.negotiate(host, pipe.hostInput, pipe.hostOutput)
                PinnedTlsPeer.requirePinnedTls13(host.session, joinIdentity.pin)
                DebugTlsProviderReadiness.requireControlReady(host)
            })
            val joinHandshake = workers.submit(Callable {
                DebugTlsEngineHandshake.negotiate(join, pipe.joinInput, pipe.joinOutput)
                PinnedTlsPeer.requirePinnedTls13(join.session, hostIdentity.pin)
                DebugTlsProviderReadiness.requireControlReady(join)
            })
            await(hostHandshake)
            await(joinHandshake)

            // This reproduces all five fixed encrypted RECORD SIZES, not
            // human approval or semantics of the real QR challenge frames.
            val hostControls = DebugTlsControlTransport(host, pipe.hostInput, pipe.hostOutput)
            val joinControls = DebugTlsControlTransport(join, pipe.joinInput, pipe.joinOutput)
            val phases = DebugTlsControlTransport.Phase.values()
            for (index in phases.indices) {
                val phase = phases[index]
                if (index > 0) {
                    hostControls.advance(phases[index - 1], phase)
                    joinControls.advance(phases[index - 1], phase)
                }
                val fromHost = ByteArray(phase.bytes) { (it + index + 17).toByte() }
                val onJoin = ByteArray(phase.bytes)
                try {
                    hostControls.output.write(fromHost)
                    assertEquals(phase.bytes, joinControls.input.read(onJoin))
                    assertArrayEquals(fromHost, onJoin)
                } finally {
                    fromHost.fill(0)
                    onJoin.fill(0)
                }
                val fromJoin = ByteArray(phase.bytes) { (it + index + 53).toByte() }
                val onHost = ByteArray(phase.bytes)
                try {
                    joinControls.output.write(fromJoin)
                    assertEquals(phase.bytes, hostControls.input.read(onHost))
                    assertArrayEquals(fromJoin, onHost)
                } finally {
                    fromJoin.fill(0)
                    onHost.fill(0)
                }
            }

            // The old control adapters are made inert. There is NO new
            // handshake, engine, pin, byte stream or implicit permission.
            hostControls.clear()
            joinControls.clear()
            val hostLab = DebugBluetoothFutureTlsTextLab(host, pipe.hostInput, pipe.hostOutput)
            val joinLab = DebugBluetoothFutureTlsTextLab(join, pipe.joinInput, pipe.joinOutput)
            val hostLedger = DebugBluetoothTextLedger()
            val joinLedger = DebugBluetoothTextLedger()
            val hostBundle = DebugBluetoothFutureChannelBundle(
                Closeable { pipe.hostOutput.close(); pipe.hostInput.close() },
                host,
                AutoCloseable { hostLab.clear(); hostLedger.close(); hostControls.clear() }
            )
            val joinBundle = DebugBluetoothFutureChannelBundle(
                Closeable { pipe.joinOutput.close(); pipe.joinInput.close() },
                join,
                AutoCloseable { joinLab.clear(); joinLedger.close(); joinControls.clear() }
            )
            val hostOwner = DebugBluetoothFutureOwnedSession(
                DebugBluetoothContinuationBudget(maxLifetimeMs = 12)
            )
            val joinOwner = DebugBluetoothFutureOwnedSession(
                DebugBluetoothContinuationBudget(maxLifetimeMs = 12)
            )
            val claimed = DebugBluetoothContinuationBudget.Observations(
                peerPinChecked = true, qrChallengeChecked = true, exporterChecked = true,
                bilateralApprovals = true, bilateralConfirmations = true,
                foreground = true, connectPermission = true
            )
            // Synthetic and caller-constructible: not a verified QR consent
            // or a transport-issued cryptographic capability.
            val hostContext = DebugBluetoothFutureSessionContext(
                true, joinIdentity.pin, ByteArray(32) { 0x61 }
            )
            val joinContext = DebugBluetoothFutureSessionContext(
                false, hostIdentity.pin, ByteArray(32) { 0x61 }
            )
            try {
                assertTrue(hostBundle.matchesCandidates(
                    Closeable { }, host
                ).not()) // A different resource is never the same socket.
                assertTrue(hostOwner.begin(71, 100, claimed, hostBundle, hostContext))
                assertTrue(joinOwner.begin(72, 100, claimed, joinBundle, joinContext))
                val msg = hostLedger.prepareText("Same engine after controls")
                try { hostLab.transmit(msg) } finally { msg.fill(0) }
                val got = joinLab.receive()
                val accepted = try { joinLedger.acceptText(got) } finally { got.fill(0) }
                assertEquals("Same engine after controls", accepted.text)
                try { joinLab.transmit(accepted.ack) } finally { accepted.ack.fill(0) }
                val reply = hostLab.receive()
                try { assertEquals(1L, hostLedger.acceptAck(reply)) } finally { reply.fill(0) }
                assertEquals(0, hostLedger.outstanding())

                // And the reverse direction on the same engine/byte pipes.
                val reverse = joinLedger.prepareText("Reverse on same TLS session")
                try { joinLab.transmit(reverse) } finally { reverse.fill(0) }
                val readBack = hostLab.receive()
                val result = try { hostLedger.acceptText(readBack) } finally { readBack.fill(0) }
                assertEquals("Reverse on same TLS session", result.text)
                try { hostLab.transmit(result.ack) } finally { result.ack.fill(0) }
                val finalAck = joinLab.receive()
                try { assertEquals(1L, joinLedger.acceptAck(finalAck)) }
                finally { finalAck.fill(0) }
                assertEquals(0, joinLedger.outstanding())

                // This expires the test-only owner. The bundle must
                // request TLS outbound closure, close the underlying pipes
                // and prevent reuse. Hard radio loss is NOT close_notify.
                assertTrue(!hostOwner.observe(71, 112, true, true, hostContext))
                assertTrue(hostBundle.isClosed())
                // On an established TLS engine, closeOutbound() may still
                // require emitting a close_notify. A forced radio teardown
                // cannot promise isOutboundDone immediately.
                assertThrows(java.io.IOException::class.java) {
                    pipe.hostOutput.write(1)
                }
                assertThrows(javax.net.ssl.SSLException::class.java) {
                    hostLab.transmit(DebugBluetoothTextFrameCodec.acknowledgment(2))
                }
                assertTrue(joinOwner.stop(72))
                assertTrue(joinBundle.isClosed())
                assertThrows(java.io.IOException::class.java) {
                    pipe.joinOutput.write(1)
                }
                assertThrows(javax.net.ssl.SSLException::class.java) {
                    joinLab.transmit(DebugBluetoothTextFrameCodec.acknowledgment(2))
                }
            } finally {
                hostOwner.close()
                joinOwner.close()
                hostContext.close()
                joinContext.close()
                hostBundle.close()
                joinBundle.close()
            }
        } finally {
            pipe.close()
            workers.shutdownNow()
            try { host.closeOutbound() } catch (_: Exception) { }
            try { join.closeOutbound() } catch (_: Exception) { }
        }
    }

    /**
     * A control frame left in the encrypted stream must never be reinterpreted
     * as a legal TEXT/ACK. No app-level magic-byte scanning or resynchronizing.
     */
    @Test
    fun leftoverEncryptedControlRecordCannotBecomeFutureText() {
        val (host, join) = connect()
        val pipe = PairPipes()
        val workers = Executors.newFixedThreadPool(2) { runnable ->
            Thread(runnable, "EC-f2n-leftover-control-jvm-only").apply { isDaemon = true }
        }
        try {
            val h = workers.submit(Callable {
                DebugTlsEngineHandshake.negotiate(host, pipe.hostInput, pipe.hostOutput)
                PinnedTlsPeer.requirePinnedTls13(host.session, joinIdentity.pin)
            })
            val j = workers.submit(Callable {
                DebugTlsEngineHandshake.negotiate(join, pipe.joinInput, pipe.joinOutput)
                PinnedTlsPeer.requirePinnedTls13(join.session, hostIdentity.pin)
            })
            await(h)
            await(j)
            val oldControl = DebugTlsControlTransport(host, pipe.hostInput, pipe.hostOutput)
            // Fixed-size TLS ciphertext is authentic but its *plaintext*
            // is NOT a valid ECBT application frame.
            val extra = ByteArray(DebugTlsControlTransport.PROOF_BYTES) { 0x55 }
            try { oldControl.output.write(extra) } finally { extra.fill(0) }
            oldControl.clear()
            val future = DebugBluetoothFutureTlsTextLab(join, pipe.joinInput, pipe.joinOutput)
            try {
                assertThrows(IllegalArgumentException::class.java) { future.receive() }
                assertThrows(javax.net.ssl.SSLException::class.java) { future.receive() }
            } finally { future.clear() }
        } finally {
            pipe.close()
            workers.shutdownNow()
            try { host.closeOutbound() } catch (_: Exception) { }
            try { join.closeOutbound() } catch (_: Exception) { }
        }
    }

    @Test
    fun wrongOutOfBandPeerPinCannotCompleteMutualTlsHandshake() {
        val incorrect = hostIdentity.pin.copyOf().also { it[0] = (it[0].toInt() xor 0x01).toByte() }
        val (host, join) = connect(joinExpected = incorrect)
        val pipe = PairPipes()
        val workers = Executors.newFixedThreadPool(2) { runnable ->
            Thread(runnable, "EC-f2d-negative-test-only").apply { isDaemon = true }
        }
        try {
            val a = workers.submit(Callable {
                DebugTlsEngineHandshake.negotiate(host, pipe.hostInput, pipe.hostOutput)
                PinnedTlsPeer.requirePinnedTls13(host.session, joinIdentity.pin)
            })
            val b = workers.submit(Callable {
                DebugTlsEngineHandshake.negotiate(join, pipe.joinInput, pipe.joinOutput)
                PinnedTlsPeer.requirePinnedTls13(join.session, incorrect)
            })
            // Joiner must reject the host's certificate before it can call
            // the successful mutual pin check. The other thread may remain
            // blocked until the simulated byte pipes close.
            assertThrows(ExecutionException::class.java) { await(b) }
            pipe.close()
            try { await(a) } catch (_: Exception) { }
        } finally {
            incorrect.fill(0)
            pipe.close()
            workers.shutdownNow()
            try { host.closeOutbound() } catch (_: Exception) { }
            try { join.closeOutbound() } catch (_: Exception) { }
        }
    }
}
