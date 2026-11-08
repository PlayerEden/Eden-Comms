package com.edenpractical.comms.transport

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test-only local OS-loopback TCP handles. Does not test QR, TLS, Android
 * networking, a real peer, message delivery or security properties.
 */
class LocalTwoPeerSocketCancellationTest {
    @Test fun cancellingBothEndsClosesTrackedConnectedSocketsAndListener() {
        val host = SessionSocketRegistry()
        val joiner = SessionSocketRegistry()
        val listener = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        var client: Socket? = null
        var accepted: Socket? = null
        try {
            listener.soTimeout = 3_000
            host.ownServer(listener)
            client = Socket(InetAddress.getLoopbackAddress(), listener.localPort)
            joiner.ownSocket(client)
            accepted = listener.accept()
            host.ownSocket(accepted)
            assertTrue(client.isConnected)
            assertTrue(accepted.isConnected)

            joiner.close()
            joiner.close()
            assertTrue(client.isClosed)
            assertNull(joiner.currentSocket())
            assertThrows(SocketException::class.java) { joiner.requireOpen() }

            host.close()
            assertTrue(accepted.isClosed)
            assertTrue(listener.isClosed)
            assertNull(host.currentSocket())
            assertThrows(SocketException::class.java) { host.requireOpen() }
            val late = Socket()
            assertThrows(SocketException::class.java) { host.ownSocket(late) }
            assertTrue(late.isClosed)
        } finally {
            host.close()
            joiner.close()
            try { client?.close() } catch (_: Exception) {}
            try { accepted?.close() } catch (_: Exception) {}
            try { listener.close() } catch (_: Exception) {}
        }
    }
}
