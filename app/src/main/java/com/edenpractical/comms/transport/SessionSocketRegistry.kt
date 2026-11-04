package com.edenpractical.comms.transport

import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

/**
 * Owns the active foreground-test sockets. Cancellation and registration
 * share one lock; sockets accepted or wrapped after cancellation are closed
 * rather than escaping the session's lifetime.
 */
internal class SessionSocketRegistry : AutoCloseable {
    private val guard = Any()
    private var closed = false
    private var client: Socket? = null
    private var server: ServerSocket? = null

    fun ownSocket(next: Socket) {
        synchronized(guard) {
            if (closed) {
                try { next.close() } catch (_: Exception) {}
                throw SocketException("Connection test stopped")
            }
            // Wrapping TCP in TLS uses autoClose=true: wrapper owns raw socket.
            client = next
        }
    }

    fun ownServer(next: ServerSocket) {
        synchronized(guard) {
            if (closed) {
                try { next.close() } catch (_: Exception) {}
                throw SocketException("Connection test stopped")
            }
            server = next
        }
    }

    fun releaseSocket(expected: Socket) {
        synchronized(guard) { if (client === expected) client = null }
    }

    fun releaseServer(expected: ServerSocket) {
        synchronized(guard) { if (server === expected) server = null }
    }

    fun requireOpen() {
        synchronized(guard) { if (closed) throw SocketException("Connection test stopped") }
    }

    fun currentSocket(): Socket? = synchronized(guard) {
        if (closed) null else client
    }

    override fun close() {
        val owned = synchronized(guard) {
            if (closed) return
            closed = true
            val result = Pair(client, server)
            client = null
            server = null
            result
        }
        try { owned.first?.close() } catch (_: Exception) {}
        try { owned.second?.close() } catch (_: Exception) {}
    }
}
