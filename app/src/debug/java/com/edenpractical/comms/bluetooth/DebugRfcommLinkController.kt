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
 * No InputStream, OutputStream, application frames, chat, peer identity,
 * certificates, trusted contact or persisted data. A BluetoothSocket only
 * proves an OS link was opened; it does NOT prove the Eden Comms peer identity
 * or establish an approved message session. No unencrypted fallback.
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
        const val CONNECT_TIMEOUT_MS = 18_000L
        const val LINK_LIFETIME_MS = 45_000L
    }

    private val lock = Any()
    private val gate = DebugRfcommAttemptGate()
    private var active: DebugRfcommAttemptGate.Token? = null
    private var server: BluetoothServerSocket? = null
    private var socket: BluetoothSocket? = null
    private var deadline: Runnable? = null

    fun phase(): DebugRfcommAttemptGate.Phase = gate.snapshot().phase

    fun host(adapter: BluetoothAdapter): Boolean {
        val token = begin(DebugRfcommAttemptGate.Role.HOST) ?: return false
        Thread({
            var listening: BluetoothServerSocket? = null
            try {
                @Suppress("MissingPermission")
                val created = adapter.listenUsingRfcommWithServiceRecord(
                    "Eden Comms DEV link only", SERVICE_UUID
                )
                listening = created
                if (!claimServer(token, created)) return@Thread
                val accepted = created.accept(CONNECT_TIMEOUT_MS.toInt())
                // The server socket is not needed after one link is accepted.
                synchronized(lock) { if (server === created) server = null }
                closeQuietly(created)
                listening = null
                connected(token, accepted)
            } catch (_: IOException) {
                fail(token)
            } catch (_: SecurityException) {
                fail(token, DebugRfcommAttemptGate.End.PERMISSION_LOST)
            } catch (_: RuntimeException) {
                fail(token)
            } finally {
                closeQuietly(listening)
                synchronized(lock) { if (server === listening) server = null }
            }
        }, "EC-debug-rfcomm-host").start()
        return true
    }

    fun join(device: BluetoothDevice, adapter: BluetoothAdapter): Boolean {
        val token = begin(DebugRfcommAttemptGate.Role.JOIN) ?: return false
        Thread({
            try {
                // Inquiring during connect makes Bluetooth connections slower.
                // The UI already stopped our scan before staging this handle.
                // A second best-effort cancellation needs BLUETOOTH_SCAN.
                @Suppress("MissingPermission")
                try { adapter.cancelDiscovery() } catch (_: SecurityException) { }
                @Suppress("MissingPermission")
                val created = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
                if (!claimSocket(token, created)) return@Thread
                created.connect() // blocking; stop/timeout closes it
                connected(token, created)
            } catch (_: IOException) {
                fail(token)
            } catch (_: SecurityException) {
                fail(token, DebugRfcommAttemptGate.End.PERMISSION_LOST)
            } catch (_: RuntimeException) {
                fail(token)
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
            armTimer(token, CONNECT_TIMEOUT_MS, DebugRfcommAttemptGate.End.TIMEOUT)
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

    private fun connected(token: DebugRfcommAttemptGate.Token, created: BluetoothSocket) {
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
        announce(token, "RFCOMM socket opened (UNVERIFIED). No app messages are sent. Disconnect to close.")
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
            val reason = when (why) {
                DebugRfcommAttemptGate.End.USER -> "You disconnected the test link."
                DebugRfcommAttemptGate.End.BACKGROUND -> "Link closed when the app left the screen."
                DebugRfcommAttemptGate.End.PERMISSION_LOST -> "Bluetooth permission lost; link closed."
                DebugRfcommAttemptGate.End.TIMEOUT -> "Bluetooth connection attempt timed out."
                DebugRfcommAttemptGate.End.EXPIRED -> "Debug link reached its 45-second limit and closed."
                DebugRfcommAttemptGate.End.FAILED -> "Bluetooth RFCOMM connection failed. No messages sent."
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
