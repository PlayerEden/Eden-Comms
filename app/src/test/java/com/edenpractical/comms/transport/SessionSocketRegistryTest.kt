package com.edenpractical.comms.transport

import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSocketRegistryTest {
    @Test fun closesTrackedClientAndListenerWhenCancelled() {
        val registry = SessionSocketRegistry()
        val client = Socket()
        val listener = ServerSocket()
        registry.ownSocket(client)
        registry.ownServer(listener)
        registry.close()
        registry.close()
        assertTrue(client.isClosed)
        assertTrue(listener.isClosed)
        assertNull(registry.currentSocket())
        assertThrows(SocketException::class.java) { registry.requireOpen() }
    }

    @Test fun lateSocketsRejectedAndImmediatelyClosed() {
        val registry = SessionSocketRegistry()
        registry.close()
        val client = Socket()
        val server = ServerSocket()
        assertThrows(SocketException::class.java) { registry.ownSocket(client) }
        assertThrows(SocketException::class.java) { registry.ownServer(server) }
        assertTrue(client.isClosed)
        assertTrue(server.isClosed)
    }

    @Test fun tlsWrapperReplacementDoesNotPrematurelyCloseRawSocket() {
        val registry = SessionSocketRegistry()
        val raw = Socket()
        val wrapper = Socket()
        registry.ownSocket(raw)
        registry.ownSocket(wrapper)
        registry.releaseSocket(raw) // stale cleanup cannot disown the wrapper
        assertFalse(raw.isClosed)
        assertTrue(registry.currentSocket() === wrapper)
        registry.close()
        assertTrue(wrapper.isClosed)
        raw.close() // actual SSLSocket wrapper uses autoClose=true
    }

    @Test fun concurrentCancelAndRegistrationCannotLeakSocket() {
        repeat(40) {
            val registry = SessionSocketRegistry()
            val start = CountDownLatch(1)
            val done = CountDownLatch(1)
            val socket = Socket()
            val runner = thread {
                try {
                    start.await(3, TimeUnit.SECONDS)
                    try { registry.ownSocket(socket) } catch (_: SocketException) {}
                } finally { done.countDown() }
            }
            start.countDown()
            registry.close()
            assertTrue(done.await(3, TimeUnit.SECONDS))
            runner.join(1000)
            assertTrue("Socket leaked after cancellation", socket.isClosed)
        }
    }
}
