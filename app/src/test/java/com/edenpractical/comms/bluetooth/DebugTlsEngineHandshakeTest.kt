package com.edenpractical.comms.bluetooth

import com.edenpractical.comms.security.Tls13PeerContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.net.ssl.SSLException
import javax.net.ssl.X509KeyManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugTlsEngineHandshakeTest {
    private val minimalLocal = object : X509KeyManager {
        override fun getClientAliases(type: String?, issuers: Array<out Principal>?) = emptyArray<String>()
        override fun chooseClientAlias(types: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?): String? = null
        override fun getServerAliases(type: String?, issuers: Array<out Principal>?) = emptyArray<String>()
        override fun chooseServerAlias(type: String?, issuers: Array<out Principal>?, socket: Socket?): String? = null
        override fun getCertificateChain(alias: String?): Array<X509Certificate>? = null
        override fun getPrivateKey(alias: String?): PrivateKey? = null
    }

    private fun record(type: Int, payload: ByteArray): ByteArray =
        byteArrayOf(
            type.toByte(), 3, 3,
            (payload.size ushr 8).toByte(), payload.size.toByte()
        ) + payload

    @Test fun acceptsOnlyOneBoundedTlsRecordWithoutScanningForMagic() {
        val body = byteArrayOf(5, 9, 10)
        val one = record(22, body)
        assertArrayEquals(one, DebugTlsEngineHandshake.readRecord(ByteArrayInputStream(one)))
        assertArrayEquals(
            record(23, byteArrayOf(1)),
            DebugTlsEngineHandshake.readRecord(
                ByteArrayInputStream(record(23, byteArrayOf(1)))
            )
        )
        val two = one + record(23, byteArrayOf(3))
        val input = ByteArrayInputStream(two)
        assertArrayEquals(one, DebugTlsEngineHandshake.readRecord(input))
        assertArrayEquals(record(23, byteArrayOf(3)), DebugTlsEngineHandshake.readRecord(input))
        assertEquals(0, input.available())
    }

    @Test fun fragmentedByteReadsStillRequireExactlyTheAdvertisedLength() {
        val bytes = record(22, ByteArray(150) { 0x6D })
        val fragmented = object : FilterInputStream(ByteArrayInputStream(bytes)) {
            override fun read(b: ByteArray, off: Int, len: Int): Int =
                super.read(b, off, minOf(len, 1))
        }
        assertArrayEquals(bytes, DebugTlsEngineHandshake.readRecord(fragmented))
    }

    @Test fun invalidVersionTypeZeroLengthAndOversizeAreRejected() {
        val inputs = listOf(
            byteArrayOf(0, 3, 3, 0, 1, 1),
            byteArrayOf(24, 3, 3, 0, 1, 1),
            byteArrayOf(22, 2, 3, 0, 1, 1),
            byteArrayOf(22, 3, 4, 0, 1, 1),
            byteArrayOf(22, 3, 3, 0, 0),
            byteArrayOf(22, 3, 3, 0x48, 0x01), // 18,433 > 18,432
        )
        inputs.forEach {
            assertThrows(SSLException::class.java) {
                DebugTlsEngineHandshake.readRecord(ByteArrayInputStream(it))
            }
        }
    }

    @Test fun malformedAndTruncatedTlsRecordsFailClosed() {
        for (bytes in listOf(
            byteArrayOf(),
            byteArrayOf(22),
            byteArrayOf(22, 3, 3, 0),
            byteArrayOf(22, 3, 3, 0, 3, 1),
        )) {
            assertThrows(EOFException::class.java) {
                DebugTlsEngineHandshake.readRecord(ByteArrayInputStream(bytes))
            }
        }
        val stalled = object : InputStream() {
            override fun read(): Int = 0
            override fun read(b: ByteArray, off: Int, len: Int): Int = 0
        }
        assertThrows(IOException::class.java) {
            DebugTlsEngineHandshake.readRecord(stalled)
        }
    }

    @Test fun realEngineWillNotApproveInitialHandshakeWithoutRemoteTlsPeer() {
        val engine = Tls13PeerContext(minimalLocal, ByteArray(32) { 0x31 })
            .unconnectedEngine(client = true)
        assertArrayEquals(arrayOf("TLSv1.3"), engine.enabledProtocols)
        val written = ByteArrayOutputStream()
        assertThrows(IOException::class.java) {
            DebugTlsEngineHandshake.negotiate(
                engine,
                ByteArrayInputStream(byteArrayOf()),
                written
            )
        }
        assertTrue(written.size() > 0) // only a real TLS ClientHello was emitted
    }
}
