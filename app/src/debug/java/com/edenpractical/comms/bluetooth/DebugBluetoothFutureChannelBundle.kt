package com.edenpractical.comms.bluetooth

import java.io.Closeable
import javax.net.ssl.SSLEngine

/**
 * F2-M FUTURE-ONLY debug teardown rehearsal for one Bluetooth channel's
 * radio socket, TLS record adapter and the SAME TLS engine.
 *
 * This is NOT connected to the actual RFCOMM probe/controller and provides
 * NO send/read, authentication, session acceptance or trust capability.
 * These resources are caller-supplied; matching references is NOT proof
 * that TLS, QR, exporter or two-person consent ever succeeded.
 *
 * The outer DebugBluetoothFutureOwnedSession can own this bundle as its
 * single Closeable. Its expiry/cancellation/permission checks then tear down
 * all three resources even if one native/vendor close fails.
 *
 * Any future LIVE use requires independent security review of the exact
 * socket-to-engine transfer and physical Android 12+ provider testing.
 */
internal class DebugBluetoothFutureChannelBundle(
    radioSocket: Closeable,
    tlsEngine: SSLEngine,
    recordAdapter: AutoCloseable
) : Closeable {
    private val lock = Any()
    private var radio: Closeable? = radioSocket
    private var engine: SSLEngine? = tlsEngine
    private var adapter: AutoCloseable? = recordAdapter
    private var closed = false

    /**
     * Reference correlation for reviewers only, never a permission check.
     * A malicious caller can supply arbitrary matching objects.
     */
    fun matchesCandidates(socket: Closeable, tls: SSLEngine): Boolean =
        synchronized(lock) {
            !closed && radio === socket && engine === tls
        }

    fun isClosed(): Boolean = synchronized(lock) { closed }

    override fun close() {
        val toClose = synchronized(lock) {
            if (closed) return
            closed = true
            val owned = Triple(adapter, radio, engine)
            adapter = null
            radio = null
            engine = null
            owned
        }
        // Prevent a future record adapter from accepting new work first.
        // Then close the radio to unblock any pending native reads.
        // Never skip socket or engine cleanup because a prior close threw.
        try { toClose.first?.close() } catch (_: Exception) { }
        try { toClose.second?.close() } catch (_: Exception) { }
        try { toClose.third?.closeOutbound() } catch (_: Exception) { }
        // Inbound TLS closeNotify may be missing after a hard radio loss;
        // do not claim a graceful closeInbound() handshake occurred.
    }
}
