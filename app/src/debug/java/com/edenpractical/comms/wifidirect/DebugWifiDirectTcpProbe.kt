package com.edenpractical.comms.wifidirect

import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * F3-A: real TCP handshake-only reachability test on a Wi-Fi Direct group.
 *
 * This is debug-only and UNAUTHENTICATED. It never writes or reads one byte
 * of application data, never creates a TLS engine, and never authorizes a
 * peer, message, PTT or trusted connection. The host listens ONLY on the
 * temporary group's selected OS P2P interface IPv4 address; join binds
 * its socket to that same OS-selected local interface IPv4 before dialing
 * the OS-reported group-owner address. Neither address is logged or saved,
 * included in a QR, or accepted from text input.
 *
 * There can be at most one listener/connection per instance. Cancel closes
 * sockets to unblock accept/connect. Callback is emitted at most once from
 * the worker thread and MUST be fenced by the Android controller's current
 * attempt token before being shown in the UI. No retry/fallback to WAN.
 */
internal class DebugWifiDirectTcpProbe(
    private val port: Int = TCP_TEST_PORT
) : AutoCloseable {
    companion object {
        const val TCP_TEST_PORT = 39_761
        const val ACCEPT_TIMEOUT_MS = 5_000
        const val CONNECT_TIMEOUT_MS = 2_000

        /**
         * Reject external/public, multicast, wildcard, link-local and IPv6.
         * Loopback is permitted only to run deterministic local JVM tests;
         * the caller's Android requestConnectionInfo/group role checks are
         * responsible for admitting the actual P2P group owner address.
         */
        fun testAddressAllowed(address: InetAddress, forJvmLoopback: Boolean = false): Boolean =
            address.address.size == 4 &&
                !address.isAnyLocalAddress && !address.isMulticastAddress &&
                !address.isLinkLocalAddress &&
                (address.isSiteLocalAddress ||
                    (forJvmLoopback && address.isLoopbackAddress))
    }

    enum class Role { HOST, JOIN }

    private val lock = Any()
    private var started = false
    private var stopped = false
    private var server: ServerSocket? = null
    private var socket: Socket? = null

    init { require(port in 1_024..65_535) }

    /**
     * Start one bounded OS TCP connect/accept; no application data transferred.
     * The callback reports only whether an unauthenticated TCP handshake
     * completed. A positive result is NOT evidence the intended peer joined.
     */
    fun start(
        role: Role,
        groupOwnerAddress: InetAddress?,
        onCompleted: (Boolean) -> Unit,
        forJvmLoopback: Boolean = false,
        onListening: () -> Unit = {},
        localP2pAddress: InetAddress? = null
    ): Boolean {
        val local = if (forJvmLoopback) groupOwnerAddress else localP2pAddress
        if (local == null || !testAddressAllowed(local, forJvmLoopback)) return false
        if (role == Role.JOIN) {
            if (groupOwnerAddress == null ||
                !testAddressAllowed(groupOwnerAddress, forJvmLoopback) ||
                local == groupOwnerAddress
            ) {
                // JVM fixture uses loopback for BOTH ends of the socket.
                if (!forJvmLoopback || groupOwnerAddress != local) return false
            }
        } else if (groupOwnerAddress != null &&
            !groupOwnerAddress.isAnyLocalAddress &&
            groupOwnerAddress != local
        ) {
            // Android may anonymize the host address; a concrete,
            // different value is never silently accepted.
            return false
        }
        synchronized(lock) {
            if (started || stopped) return false
            started = true
        }
        Thread({
            var success = false
            try {
                when (role) {
                    Role.HOST -> {
                        val listener = ServerSocket()
                        synchronized(lock) {
                            if (!stopped) server = listener
                            else {
                                closeQuietly(listener)
                                return@Thread
                            }
                        }
                        // Must not listen on Wi-Fi, cellular or every interface.
                        listener.reuseAddress = false
                        listener.bind(InetSocketAddress(local, port), 1)
                        listener.soTimeout = ACCEPT_TIMEOUT_MS
                        if (synchronized(lock) { !stopped }) onListening()
                        val connected = listener.accept()
                        synchronized(lock) {
                            if (!stopped) socket = connected
                        }
                        try {
                            success = synchronized(lock) { !stopped }
                        } finally {
                            closeQuietly(connected)
                        }
                    }
                    Role.JOIN -> {
                        val outbound = Socket()
                        synchronized(lock) {
                            if (!stopped) socket = outbound
                            else {
                                closeQuietly(outbound)
                                return@Thread
                            }
                        }
                        // An explicit local bind prevents OS default routing
                        // onto a different Wi-Fi/cellular interface.
                        outbound.bind(InetSocketAddress(local, 0))
                        outbound.connect(
                            InetSocketAddress(groupOwnerAddress, port),
                            CONNECT_TIMEOUT_MS
                        )
                        success = synchronized(lock) {
                            !stopped && outbound.localAddress == local
                        }
                    }
                }
            } catch (_: IOException) {
                // An Android P2P group callback and a TCP connection are
                // separate events. Failure is expected on unsupported OEMs.
            } catch (_: RuntimeException) {
                // No crash, no fallback and no disclosure of IP/socket state.
            } finally {
                val resultAllowed: Boolean
                val serverToClose: ServerSocket?
                val socketToClose: Socket?
                synchronized(lock) {
                    resultAllowed = !stopped
                    stopped = true
                    serverToClose = server
                    socketToClose = socket
                    server = null
                    socket = null
                }
                closeQuietly(socketToClose)
                closeQuietly(serverToClose)
                if (resultAllowed) onCompleted(success)
            }
        }, "EC-debug-p2p-tcp-handshake-only").apply { isDaemon = true }.start()
        return true
    }

    override fun close() {
        val serverToClose: ServerSocket?
        val socketToClose: Socket?
        synchronized(lock) {
            stopped = true
            serverToClose = server
            socketToClose = socket
            server = null
            socket = null
        }
        closeQuietly(socketToClose)
        closeQuietly(serverToClose)
    }

    private fun closeQuietly(resource: Closeable?) {
        try { resource?.close() } catch (_: IOException) { }
    }
}
