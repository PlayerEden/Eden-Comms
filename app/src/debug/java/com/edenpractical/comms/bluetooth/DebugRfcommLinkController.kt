package com.edenpractical.comms.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.os.Handler
import java.io.Closeable
import java.io.IOException
import java.util.UUID

/**
 * Actual secure-Bluetooth-RFCOMM socket experiment in src/debug ONLY.
 *
 * M3n-B1: after a fresh two-QR exchange, a real pinned mutual TLS 1.3
 * SSLEngine HANDSHAKE ONLY runs over this RFCOMM socket, checking peer key
 * possession, exact certificate pin and exporter availability. No QR nonce
 * binding, bilateral approvals, application packets, chat, voice or
 * persisted data. No plaintext or insecure RFCOMM fallback.
 *
 * Socket accept/connect always run off the UI thread. Closing the socket/server
 * aborts pending blocking operations. Each callback is fenced by a token so
 * canceled/expired worker completions cannot reopen or update a new attempt.
 */
internal class DebugRfcommLinkController(
    private val handler: Handler,
    private val isForeground: () -> Boolean,
    private val hasConnectPermission: () -> Boolean,
    private val onState: (DebugRfcommAttemptGate.Phase, String) -> Unit
) {
    companion object {
        // Fixed DEMO SDP UUID, not a secret or a peer identity.
        val SERVICE_UUID: UUID =
            UUID.fromString("7de2a6e5-332a-4d3a-891b-6bc4a9ab742d")
        const val HOST_WAIT_MS = 60_000L
        const val JOIN_TIMEOUT_MS = 18_000L
        const val LINK_LIFETIME_MS = 45_000L
    }

    private val lock = Any()
    private val gate = DebugRfcommAttemptGate()
    private var active: DebugRfcommAttemptGate.Token? = null
    private var server: BluetoothServerSocket? = null
    private var socket: BluetoothSocket? = null
    private var deadline: Runnable? = null

    fun phase(): DebugRfcommAttemptGate.Phase = gate.snapshot().phase

    @Suppress("MissingPermission")
    fun host(adapter: BluetoothAdapter, request: DebugBluetoothTlsProbeInput): Boolean {
        val token = begin(DebugRfcommAttemptGate.Role.HOST) ?: return false
        Thread({
            var listening: BluetoothServerSocket? = null
            try {
                val created = adapter.listenUsingRfcommWithServiceRecord(
                    "Eden Comms DEV link only", SERVICE_UUID
                )
                listening = created
                if (!claimServer(token, created)) return@Thread
                val accepted = created.accept(HOST_WAIT_MS.toInt())
                // The server socket is not needed after one link is accepted.
                synchronized(lock) { if (server === created) server = null }
                closeQuietly(created)
                listening = null
                connected(token, accepted, request)
            } catch (_: IOException) {
                fail(token)
            } catch (_: SecurityException) {
                fail(token, DebugRfcommAttemptGate.End.PERMISSION_LOST)
            } catch (_: RuntimeException) {
                fail(token)
            } finally {
                request.close()
                closeQuietly(listening)
                synchronized(lock) { if (server === listening) server = null }
            }
        }, "EC-debug-rfcomm-host").start()
        return true
    }

    @Suppress("MissingPermission")
    fun join(device: BluetoothDevice, adapter: BluetoothAdapter, request: DebugBluetoothTlsProbeInput): Boolean {
        val token = begin(DebugRfcommAttemptGate.Role.JOIN) ?: return false
        Thread({
            try {
                // Inquiring during connect makes Bluetooth connections slower.
                // The UI already stopped our scan before staging this handle.
                // A second best-effort cancellation needs BLUETOOTH_SCAN.
                try { adapter.cancelDiscovery() } catch (_: SecurityException) { }
                val created = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
                if (!claimSocket(token, created)) return@Thread
                created.connect() // blocking; stop/timeout closes it
                connected(token, created, request)
            } catch (_: IOException) {
                fail(token)
            } catch (_: SecurityException) {
                fail(token, DebugRfcommAttemptGate.End.PERMISSION_LOST)
            } catch (_: RuntimeException) {
                fail(token)
            } finally {
                request.close()
            }
        }, "EC-debug-rfcomm-join").start()
        return true
    }

    private fun begin(role: DebugRfcommAttemptGate.Role): DebugRfcommAttemptGate.Token? {
        val token: DebugRfcommAttemptGate.Token
        synchronized(lock) {
            if (active != null) return null
            if (gate.snapshot().phase == DebugRfcommAttemptGate.Phase.STOPPED) {
                gate.reset()
            }
            token = gate.begin(role, isForeground(), hasConnectPermission()) ?: return null
            active = token
            armTimer(token,
                if (role == DebugRfcommAttemptGate.Role.HOST) HOST_WAIT_MS else JOIN_TIMEOUT_MS,
                DebugRfcommAttemptGate.End.TIMEOUT)
        }
        announce(token,
            if (role == DebugRfcommAttemptGate.Role.HOST) "Waiting for one Bluetooth RFCOMM link. No messages."
            else "Opening an unverified RFCOMM link. This can cause an Android pairing prompt."
        )
        return token
    }

    private fun claimServer(token: DebugRfcommAttemptGate.Token, created: BluetoothServerSocket): Boolean =
        synchronized(lock) {
            if (active !== token || !gate.isConnecting(token)) {
                closeQuietly(created)
                false
            } else {
                server = created
                true
            }
        }

    private fun claimSocket(token: DebugRfcommAttemptGate.Token, created: BluetoothSocket): Boolean =
        synchronized(lock) {
            if (active !== token || !gate.isConnecting(token)) {
                closeQuietly(created)
                false
            } else {
                socket = created
                true
            }
        }

    private fun connected(
        token: DebugRfcommAttemptGate.Token,
        created: BluetoothSocket,
        request: DebugBluetoothTlsProbeInput
    ) {
        var accepted = false
        synchronized(lock) {
            if (active === token && gate.opened(
                    token, isForeground(), hasConnectPermission()
                )
            ) {
                socket = created
                cancelTimer()
                armTimer(token, LINK_LIFETIME_MS, DebugRfcommAttemptGate.End.EXPIRED)
                accepted = true
            }
        }
        if (!accepted) {
            closeQuietly(created)
            return
        }
        announce(token, "RFCOMM open: testing strict pinned mutual TLS 1.3. No app messages.")
        try {
            // Worker thread: the TLS handshake only. No channel authorizes
            // chat/voice, and the socket is closed after this probe returns.
            DebugRfcommTlsProbe.run(created, request)
            stop(token, DebugRfcommAttemptGate.End.TLS_PROBE_PASSED)
        } catch (_: Exception) {
            stop(token, DebugRfcommAttemptGate.End.FAILED)
        }
    }

    private fun announce(token: DebugRfcommAttemptGate.Token, message: String) {
        handler.post {
            synchronized(lock) {
                if (active !== token || !gate.isLive(token)) return@post
                onState(gate.snapshot().phase, message)
            }
        }
    }

    private fun armTimer(
        token: DebugRfcommAttemptGate.Token,
        milliseconds: Long,
        why: DebugRfcommAttemptGate.End
    ) {
        cancelTimer()
        val task = Runnable { stop(token, why) }
        deadline = task
        handler.postDelayed(task, milliseconds)
    }

    private fun cancelTimer() {
        deadline?.let { handler.removeCallbacks(it) }
        deadline = null
    }

    private fun fail(token: DebugRfcommAttemptGate.Token, why: DebugRfcommAttemptGate.End =
        DebugRfcommAttemptGate.End.FAILED
    ) { stop(token, why) }

    fun disconnect() {
        val token = synchronized(lock) { active } ?: return
        stop(token, DebugRfcommAttemptGate.End.USER)
    }

    fun background() {
        val token = synchronized(lock) { active } ?: return
        stop(token, DebugRfcommAttemptGate.End.BACKGROUND)
    }

    fun permissionLost() {
        val token = synchronized(lock) { active } ?: return
        stop(token, DebugRfcommAttemptGate.End.PERMISSION_LOST)
    }

    private fun stop(token: DebugRfcommAttemptGate.Token, why: DebugRfcommAttemptGate.End) {
        val oldServer: BluetoothServerSocket?
        val oldSocket: BluetoothSocket?
        synchronized(lock) {
            if (active !== token || !gate.stop(token, why)) return
            active = null
            cancelTimer()
            oldServer = server
            oldSocket = socket
            server = null
            socket = null
        }
        // Socket closure may unblock accept() / connect() on worker threads.
        closeQuietly(oldServer)
        closeQuietly(oldSocket)
        handler.post {
            // An old connection's queued closure message cannot overwrite
            // a newer user-started link after reset or reconnect.
            synchronized(lock) {
                if (active != null || gate.snapshot().phase !=
                    DebugRfcommAttemptGate.Phase.STOPPED
                ) return@post
            }
            val reason = when (why) {
                DebugRfcommAttemptGate.End.USER -> "You disconnected the test link."
                DebugRfcommAttemptGate.End.BACKGROUND -> "Link closed when the app left the screen."
                DebugRfcommAttemptGate.End.PERMISSION_LOST -> "Bluetooth permission lost; link closed."
                DebugRfcommAttemptGate.End.TIMEOUT -> "Bluetooth connection attempt timed out."
                DebugRfcommAttemptGate.End.EXPIRED -> "Debug link reached its 45-second limit and closed."
                DebugRfcommAttemptGate.End.FAILED ->
                    "Bluetooth link/TLS probe failed or peer pin mismatched. No messages sent."
                DebugRfcommAttemptGate.End.TLS_PROBE_PASSED ->
                    "Pinned TLS 1.3 certificate verified and local exporter available. " +
                        "QR nonce/channel agreement and human approvals NOT tested. " +
                        "Diagnostic socket closed; no messages sent."
            }
            onState(DebugRfcommAttemptGate.Phase.STOPPED, reason)
        }
    }

    private fun closeQuietly(item: Closeable?) {
        try {
            item?.close()
        } catch (_: IOException) {
            // Cancellation must continue even when a platform socket close fails.
        } catch (_: RuntimeException) {
            // Vendor Bluetooth implementations can throw during teardown.
        }
    }
}
