package com.edenpractical.comms.security

import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.net.ssl.X509KeyManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Tls13PeerContextTest {
    private val stubLocalCredentials = object : X509KeyManager {
        override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?): Array<String> = emptyArray()
        override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?): String? = null
        override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String> = emptyArray()
        override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? = null
        override fun getCertificateChain(alias: String?): Array<X509Certificate>? = null
        override fun getPrivateKey(alias: String?): PrivateKey? = null
    }

    @Test fun clientSocketIsRestrictedToTls13() {
        val policy = Tls13PeerContext(stubLocalCredentials, ByteArray(32))
        policy.unconnectedClientSocket().use { socket ->
            assertTrue(socket.useClientMode)
            assertArrayEquals(arrayOf("TLSv1.3"), socket.enabledProtocols)
        }
    }

    @Test fun serverSocketRequiresMutualAuthAndTls13() {
        val policy = Tls13PeerContext(stubLocalCredentials, ByteArray(32))
        policy.unboundServerSocket().use { server ->
            assertTrue(server.needClientAuth)
            assertArrayEquals(arrayOf("TLSv1.3"), server.enabledProtocols)
        }
    }

    @Test fun missingPeerPinCannotMakeContext() {
        assertThrows(IllegalArgumentException::class.java) {
            Tls13PeerContext(stubLocalCredentials, ByteArray(0))
        }
    }
}
