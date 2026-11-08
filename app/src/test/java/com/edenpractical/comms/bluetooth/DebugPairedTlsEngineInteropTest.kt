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
