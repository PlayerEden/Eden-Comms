package com.edenpractical.comms.bluetooth

import com.edenpractical.comms.security.Tls13PeerContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.net.ssl.SSLException
import javax.net.ssl.X509KeyManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DebugTlsControlTransportTest {
    private val localCredentials = object : X509KeyManager {
        override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) = emptyArray<String>()
        override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?): String? = null
        override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?) = emptyArray<String>()
        override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? = null
        override fun getCertificateChain(alias: String?): Array<X509Certificate>? = null
        override fun getPrivateKey(alias: String?): PrivateKey? = null
    }

    private fun fresh(): Pair<DebugTlsControlTransport, ByteArrayOutputStream> {
        val engine = Tls13PeerContext(localCredentials, ByteArray(32) { 0x12 })
            .unconnectedEngine(client = true)
        val output = ByteArrayOutputStream()
        return DebugTlsControlTransport(engine, ByteArrayInputStream(byteArrayOf()), output) to output
    }

    @Test fun exactlyThreeForwardOnlyProtocolPhasesAndBoundedLengths() {
        val (transport, _) = fresh()
        assertEquals(134, DebugTlsControlTransport.Phase.QR_PROOF.bytes)
        assertEquals(38, DebugTlsControlTransport.Phase.EXPORTER.bytes)
        assertEquals(38, DebugTlsControlTransport.Phase.COMMITMENT.bytes)
        assertThrows(SSLException::class.java) {
            transport.advance(
                DebugTlsControlTransport.Phase.EXPORTER,
                DebugTlsControlTransport.Phase.COMMITMENT
            )
        }
        assertThrows(SSLException::class.java) {
            transport.advance(
                DebugTlsControlTransport.Phase.QR_PROOF,
                DebugTlsControlTransport.Phase.COMMITMENT
            )
        }
        transport.advance(
            DebugTlsControlTransport.Phase.QR_PROOF,
            DebugTlsControlTransport.Phase.EXPORTER
        )
        transport.advance(
            DebugTlsControlTransport.Phase.EXPORTER,
            DebugTlsControlTransport.Phase.COMMITMENT
        )
        assertThrows(SSLException::class.java) {
            transport.advance(
                DebugTlsControlTransport.Phase.COMMITMENT,
                DebugTlsControlTransport.Phase.QR_PROOF
            )
        }
        transport.clear()
        assertThrows(SSLException::class.java) {
            transport.output.write(ByteArray(38))
        }
    }

    @Test fun anUnhandshakenEngineCannotReadOrWriteEvenCorrectLengthControls() {
        val (transport, rawOutput) = fresh()
        assertThrows(SSLException::class.java) {
            transport.output.write(ByteArray(DebugTlsControlTransport.PROOF_BYTES))
        }
        assertEquals(0, rawOutput.size())
        assertThrows(SSLException::class.java) {
            transport.input.read(ByteArray(DebugTlsControlTransport.PROOF_BYTES))
        }
    }

    @Test fun invalidLengthsSingleByteCallsAndArrayBoundsNeverReachWire() {
        val (transport, rawOutput) = fresh()
        assertThrows(SSLException::class.java) { transport.output.write(5) }
        assertThrows(SSLException::class.java) { transport.input.read() }
        assertThrows(SSLException::class.java) { transport.output.write(ByteArray(1)) }
        assertThrows(SSLException::class.java) {
            transport.input.read(ByteArray(1))
        }
        assertThrows(IndexOutOfBoundsException::class.java) {
            transport.output.write(ByteArray(200), 199, 5)
        }
        assertThrows(IndexOutOfBoundsException::class.java) {
            transport.input.read(ByteArray(200), -1, 134)
        }
        assertEquals(0, rawOutput.size())
    }
}
