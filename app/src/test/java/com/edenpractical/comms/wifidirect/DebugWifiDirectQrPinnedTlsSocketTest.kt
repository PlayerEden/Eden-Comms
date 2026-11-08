package com.edenpractical.comms.wifidirect

import com.edenpractical.comms.security.PinnedTlsPeer
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.X509KeyManager
import org.junit.AfterClass
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test

/**
 * Actual JVM TCP mutual TLS, after canonical three-QR metadata; NOT actual
 * Android P2P, live radio, approval, encrypted chat or security sign-off.
 */
class DebugWifiDirectQrPinnedTlsSocketTest {
    companion object {
        private const val PASSWORD = "f3b-ephemeral-test"
        private lateinit var directory: Path
        private lateinit var hostIdentity: Identity
        private lateinit var joinIdentity: Identity
        private data class Identity(val manager: X509KeyManager, val pin: ByteArray)

        @BeforeClass @JvmStatic fun createTemporaryIdentities() {
            directory = Files.createTempDirectory("ec-f3b-jvm-")
            hostIdentity = createIdentity("host")
            joinIdentity = createIdentity("join")
            assertFalse(hostIdentity.pin.contentEquals(joinIdentity.pin))
        }

        @AfterClass @JvmStatic fun eraseTemporaryIdentities() {
            if (!::directory.isInitialized) return
            for (alias in listOf("host", "join"))
                Files.deleteIfExists(directory.resolve("${alias}.p12"))
            Files.deleteIfExists(directory)
        }

        private fun createIdentity(alias: String): Identity {
            val p = directory.resolve("${alias}.p12")
            val keytool = Path.of(System.getProperty("java.home"), "bin",
                if (System.getProperty("os.name").startsWith("Windows"))
                    "keytool.exe" else "keytool").toString()
            val proc = ProcessBuilder(
                keytool, "-genkeypair", "-noprompt", "-alias", alias,
                "-keyalg", "EC", "-keysize", "256", "-validity", "2",
                "-dname", "CN=Eden Comms F3B ${alias} JVM TEST ONLY",
                "-keystore", p.toString(), "-storetype", "PKCS12",
                "-storepass", PASSWORD, "-keypass", PASSWORD
            ).redirectErrorStream(true).start()
            if (!proc.waitFor(15, TimeUnit.SECONDS)) {
                proc.destroyForcibly()
                throw AssertionError("Ephemeral test keytool timeout")
            }
            assertEquals(0, proc.exitValue())
            val store = KeyStore.getInstance("PKCS12")
            Files.newInputStream(p).use { store.load(it, PASSWORD.toCharArray()) }
            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            kmf.init(store, PASSWORD.toCharArray())
            val manager = kmf.keyManagers.filterIsInstance<X509KeyManager>().single()
            val certificate = store.getCertificate(alias) as X509Certificate
            return Identity(manager, PinnedTlsPeer.fingerprint(certificate))
        }
    }

    private val loopback = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
    private val now = 1_800_000_000L
    private fun unusedPort() = ServerSocket(0, 1, loopback).use { it.localPort }

    private class Qrs(
        val host: DebugWifiDirectQrCodec.Host,
        val reply: DebugWifiDirectQrCodec.Reply,
        val ack: DebugWifiDirectQrCodec.Ack
    ) : AutoCloseable {
        override fun close() { host.clear(); reply.clear(); ack.clear() }
    }

    private fun exchange(hostPin: ByteArray = hostIdentity.pin): Qrs {
        val first = DebugWifiDirectQrCodec.newHost(hostPin, now)
        val scannedHost = DebugWifiDirectQrCodec.decodeHost(
            DebugWifiDirectQrCodec.encodeHost(first), now + 1)
        val reply = DebugWifiDirectQrCodec.newReply(scannedHost, joinIdentity.pin, now + 2)
        val hostScanned = DebugWifiDirectQrCodec.decodeReply(
            DebugWifiDirectQrCodec.encodeReply(reply), first, now + 3)
        val ack = DebugWifiDirectQrCodec.makeAck(first, hostScanned, now + 4)
        val scannedAck = DebugWifiDirectQrCodec.decodeAck(
            DebugWifiDirectQrCodec.encodeAck(ack),
            scannedHost, reply, joinIdentity.pin, now + 5)
        first.clear()
        hostScanned.clear()
        ack.clear()
        return Qrs(scannedHost, reply, scannedAck)
    }

    private fun host(qrs: Qrs) = DebugWifiDirectFutureQrTlsExpectation.host(
        qrs.host, qrs.reply, qrs.ack, hostIdentity.pin, now + 5)

    private fun join(qrs: Qrs) = DebugWifiDirectFutureQrTlsExpectation.join(
        qrs.host, qrs.reply, qrs.ack, joinIdentity.pin, now + 5)

    @Test fun threeFreshQrsThenTwoRealTcpTlsPeersMutuallyAuthenticateAndClose() {
        exchange().use { qrs ->
            host(qrs).use { hostExpectation ->
                join(qrs).use { joinExpectation ->
                    assertArrayEquals(joinIdentity.pin, hostExpectation.copyPeerPinForTls())
                    assertArrayEquals(hostIdentity.pin, joinExpectation.copyPeerPinForTls())
                    assertArrayEquals(
                        hostExpectation.copyTranscriptForReview(),
                        joinExpectation.copyTranscriptForReview())
                    val port = unusedPort()
                    val ready = CountDownLatch(1)
                    val pool = Executors.newFixedThreadPool(2)
                    try {
                        val server = pool.submit(Callable {
                            DebugWifiDirectFutureTlsSocketLab.hostOnce(
                                loopback, port, hostIdentity.manager, hostExpectation,
                                onListening = { ready.countDown() })
                        })
                        assertTrue(ready.await(3, TimeUnit.SECONDS))
                        val client = pool.submit(Callable {
                            DebugWifiDirectFutureTlsSocketLab.joinOnce(
                                loopback, port, joinIdentity.manager, joinExpectation)
                        })
                        server.get(10, TimeUnit.SECONDS)
                        client.get(10, TimeUnit.SECONDS)
                    } finally { pool.shutdownNow() }
                }
            }
        }
    }

    @Test fun wrongPeerFingerprintInScannedQrFailsRealMutualTls() {
        val falsePin = hostIdentity.pin.copyOf().also {
            it[0] = (it[0].toInt() xor 0x33).toByte()
        }
        exchange(hostPin = falsePin).use { falsifiedQr ->
            join(falsifiedQr).use { joinWithWrongHostPin ->
                exchange().use { correctQr ->
                    host(correctQr).use { actualHost ->
                        val port = unusedPort()
                        val ready = CountDownLatch(1)
                        val pool = Executors.newFixedThreadPool(2)
                        try {
                            val server = pool.submit(Callable {
                                DebugWifiDirectFutureTlsSocketLab.hostOnce(
                                    loopback, port, hostIdentity.manager, actualHost,
                                    onListening = { ready.countDown() })
                            })
                            assertTrue(ready.await(3, TimeUnit.SECONDS))
                            val client = pool.submit(Callable {
                                DebugWifiDirectFutureTlsSocketLab.joinOnce(
                                    loopback, port, joinIdentity.manager, joinWithWrongHostPin)
                            })
                            assertThrows(ExecutionException::class.java) {
                                client.get(10, TimeUnit.SECONDS)
                            }
                            try { server.get(10, TimeUnit.SECONDS) }
                            catch (_: ExecutionException) { /* client rejects wrong pin */ }
                        } finally { pool.shutdownNow() }
                    }
                }
            }
        }
    }

    @Test fun alteredAckWrongOwnPinAndExpiredQrFailBeforeTcp() {
        exchange().use { qrs ->
            val altered = DebugWifiDirectQrCodec.Ack(
                qrs.ack.hostNonce, qrs.ack.replyNonce,
                qrs.ack.transcript.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() })
            try {
                assertThrows(IllegalArgumentException::class.java) {
                    DebugWifiDirectFutureQrTlsExpectation.host(
                        qrs.host, qrs.reply, altered, hostIdentity.pin, now + 5)
                }
                assertThrows(IllegalArgumentException::class.java) {
                    DebugWifiDirectFutureQrTlsExpectation.join(
                        qrs.host, qrs.reply, altered, joinIdentity.pin, now + 5)
                }
            } finally { altered.clear() }
            assertThrows(IllegalArgumentException::class.java) {
                DebugWifiDirectFutureQrTlsExpectation.host(
                    qrs.host, qrs.reply, qrs.ack, joinIdentity.pin, now + 5)
            }
            assertThrows(IllegalArgumentException::class.java) {
                DebugWifiDirectFutureQrTlsExpectation.join(
                    qrs.host, qrs.reply, qrs.ack, joinIdentity.pin, now + 181)
            }
        }
    }

    @Test fun tlsLabRejectsRealRadioAddressesWrongRolesAndClosedContext() {
        exchange().use { qrs ->
            host(qrs).use { expected ->
                val privateIp = InetAddress.getByAddress(
                    byteArrayOf(192.toByte(), 168.toByte(), 49, 1))
                assertThrows(IllegalArgumentException::class.java) {
                    DebugWifiDirectFutureTlsSocketLab.hostOnce(
                        privateIp, 39671, hostIdentity.manager, expected)
                }
                assertThrows(IllegalArgumentException::class.java) {
                    DebugWifiDirectFutureTlsSocketLab.joinOnce(
                        loopback, 39671, joinIdentity.manager, expected)
                }
                expected.close()
                assertThrows(IllegalStateException::class.java) {
                    expected.copyPeerPinForTls()
                }
            }
        }
    }
}
