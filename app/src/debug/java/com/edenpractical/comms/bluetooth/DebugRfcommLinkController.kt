package com.edenpractical.comms.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.SystemClock
import java.io.Closeable
import java.io.IOException
import java.util.UUID

/**
 * Actual secure-Bluetooth-RFCOMM socket experiment in src/debug ONLY.
 *
 * M3n-C1: this RFCOMM socket runs pinned TLS 1.3, fresh QR proof,
 * same-channel exporter and transcript commitments, then requires explicit
 * APPROVE from EACH person and exchanges two encrypted final CONFIRM records.
 * F2-Q: the SAME verified TLS engine can carry bounded typed test TEXT/ACK
 * on an owned, foreground, time-limited RFCOMM socket after both final CONFIRM
 * frames. No audio, stored peer or production connection capability.
 * No plaintext or insecure RFCOMM fallback.
 *
 * Socket accept/connect always run off the UI thread. Closing the socket/server
 * aborts pending blocking operations. Each callback is fenced by a token so
 * canceled/expired worker completions cannot reopen or update a new attempt.
 */
internal class DebugRfcommLinkController(
    private val handler: Handler,
    private val isForeground: () -> Boolean,
    private val hasConnectPermission: () -> Boolean,
    private val onState: (DebugRfcommAttemptGate.Phase, String) -> Unit,
    private val onApprovalReady: (Boolean) -> Unit,
    private val onVerificationStage: (DebugBluetoothVerifiedStage?) -> Unit = {},
    private val onTextEvent: (DebugBluetoothApprovedTextChannel.Event) -> Unit = {}
) {
    companion object {
        // Fixed DEMO SDP UUID, not a secret or a peer identity.
        val SERVICE_UUID: UUID =
            UUID.fromString("7de2a6e5-332a-4d3a-891b-6bc4a9ab742d")
        const val HOST_WAIT_MS = 60_000L
        const val JOIN_TIMEOUT_MS = 18_000L
        const val LINK_LIFETIME_MS = 45_000L
        const val TEXT_LIFETIME_MS = 10L * 60_000L
    }

    private val lock = Any()
    private val gate = DebugRfcommAttemptGate()
    private var active: DebugRfcommAttemptGate.Token? = null
    private var server: BluetoothServerSocket? = null
    private var socket: BluetoothSocket? = null
    private var consent: DebugBluetoothConsentDecision? = null
    private var liveText: DebugBluetoothApprovedTextChannel? = null
    private var deadline: Runnable? = null
    // UI closure callbacks must belong to the most recently stopped attempt.
    private var lastStoppedToken: DebugRfcommAttemptGate.Token? = null
    // Only the last fixed, non-sensitive proof label; no pins, QR, IDs or body.
    // Set by the current worker AFTER each actual cryptographic check.
    private var lastVerifiedStage: DebugBluetoothVerifiedStage? = null

    fun phase(): DebugRfcommAttemptGate.Phase = gate.snapshot().phase

    @Suppress("MissingPermission")
    fun host(adapter: BluetoothAdapter, request: DebugBluetoothTlsProbeInput): Boolean {
        if (!request.hostRole) { request.close(); return false }
        val token = begin(DebugRfcommAttemptGate.Role.HOST)
            ?: run { request.close(); return false }
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
        if (request.hostRole) { request.close(); return false }
        val token = begin(DebugRfcommAttemptGate.Role.JOIN)
            ?: run { request.close(); return false }
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
            lastVerifiedStage = null
            armTimer(token,
                if (role == DebugRfcommAttemptGate.Role.HOST) HOST_WAIT_MS else JOIN_TIMEOUT_MS,
                DebugRfcommAttemptGate.End.TIMEOUT)
        }
        onVerificationStage(null)
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
            stop(
                token,
                if (!isForeground()) DebugRfcommAttemptGate.End.BACKGROUND
                else DebugRfcommAttemptGate.End.PERMISSION_LOST
            )
            return
        }
        announce(token, "RFCOMM open: verifying the pinned TLS 1.3 and fresh in-person QR proof.")
        try {
            // Worker thread. The ONLY text handoff is inside the same real
            // pinned/QR-bound TLS engine after both encrypted confirmations.
            // The socket and TLS engine are never reconstructed from a receipt.
            val completion = DebugRfcommTlsProbe.run(
                created, request,
                onVerifiedStage = { step -> verified(token, step) },
                onApprovedChannel = { engine, wireInput, wireOutput ->
                    val approved = DebugBluetoothApprovedTextChannel(
                        engine, wireInput, wireOutput
                    ) { event -> textEvent(token, event) }
                    val admitted = synchronized(lock) {
                        if (active === token &&
                            gate.snapshot().phase == DebugRfcommAttemptGate.Phase.LINK_OPEN &&
                            isForeground() && hasConnectPermission() && liveText == null
                        ) {
                            liveText = approved
                            cancelTimer()
                            armTimer(token, TEXT_LIFETIME_MS, DebugRfcommAttemptGate.End.EXPIRED)
                            true
                        } else false
                    }
                    if (!admitted) {
                        approved.close()
                        throw IOException("Verified Bluetooth session no longer current")
                    }
                    try {
                        approved.runUntilClosed()
                    } finally {
                        approved.close()
                        stop(token, DebugRfcommAttemptGate.End.CHAT_ENDED)
                    }
                }
            ) { decision ->
                synchronized(lock) {
                    if (active !== token || !gate.isLive(token) ||
                        !isForeground() || !hasConnectPermission()
                    ) {
                        decision.close()
                    } else {
                        consent = decision
                        handler.post {
                            synchronized(lock) {
                                if (active !== token || consent !== decision ||
                                    !gate.isLive(token)
                                ) return@post
                                onApprovalReady(true)
                                onState(
                                    gate.snapshot().phase,
                                    "QR + TLS session matched. Both people must " +
                                        "independently approve this one test link."
                                )
                            }
                        }
                    }
                }
            }
            try {
                // The worker's verified diagnostic result is accepted exactly
                // once, only for the SAME active socket token and host/join
                // role. It is not a reusable connection or send capability.
                val currentAttemptAcknowledged = synchronized(lock) {
                    active === token &&
                        gate.snapshot().phase == DebugRfcommAttemptGate.Phase.LINK_OPEN &&
                        completion.acknowledgeOnce(
                            expectedHostRole = token.role == DebugRfcommAttemptGate.Role.HOST,
                            observedAtSeconds = System.currentTimeMillis() / 1000,
                            observedAtElapsedMs = SystemClock.elapsedRealtime(),
                            foreground = isForeground(),
                            connectPermission = hasConnectPermission()
                        )
                }
                if (!currentAttemptAcknowledged) {
                    // If Android prerequisites vanished, retain the specific
                    // failure reason; never misreport mutual consent success.
                    stop(token, when {
                        !isForeground() -> DebugRfcommAttemptGate.End.BACKGROUND
                        !hasConnectPermission() -> DebugRfcommAttemptGate.End.PERMISSION_LOST
                        else -> DebugRfcommAttemptGate.End.FAILED
                    })
                } else {
                    // F2-A rechecks foreground/permission immediately before
                    // terminal closure, including when they change after
                    // the one-use receipt was acknowledged.
                    stop(token, DebugRfcommCompletionPolicy.endAfterConsent(
                        isForeground(), hasConnectPermission()
                    ))
                }
            } finally {
                // Diagnostic socket always closes in stop(), not handed off.
                completion.close()
            }
        } catch (_: DebugBluetoothConsentDenied) {
            stop(token, DebugRfcommAttemptGate.End.CONSENT_DECLINED)
        } catch (_: Exception) {
            stop(token, DebugRfcommAttemptGate.End.FAILED)
        }
    }

    /**
     * Queue typed text on the currently verified real TLS session only.
     * The UI never touches SSLEngine, Bluetooth streams, or raw RFCOMM bytes.
     */
    fun offerText(text: String): Boolean {
        val channel = synchronized(lock) {
            if (active == null ||
                gate.snapshot().phase != DebugRfcommAttemptGate.Phase.LINK_OPEN ||
                !isForeground() || !hasConnectPermission()
            ) null else liveText
        } ?: return false
        return channel.offerText(text)
    }

    private fun textEvent(
        token: DebugRfcommAttemptGate.Token,
        event: DebugBluetoothApprovedTextChannel.Event
    ) {
        if (event is DebugBluetoothApprovedTextChannel.Event.Failed) {
            stop(token, DebugRfcommAttemptGate.End.FAILED)
            return
        }
        handler.post {
            synchronized(lock) {
                if (active !== token ||
                    gate.snapshot().phase != DebugRfcommAttemptGate.Phase.LINK_OPEN ||
                    liveText == null || !isForeground() || !hasConnectPermission()
                ) return@post
                onTextEvent(event)
            }
        }
    }

    /** Only a live token can publish real, monotonically advanced proof labels. */
    private fun verified(
        token: DebugRfcommAttemptGate.Token,
        step: DebugBluetoothVerifiedStage
    ) {
        synchronized(lock) {
            if (active !== token || !gate.isLive(token) ||
                !isForeground() || !hasConnectPermission() ||
                (lastVerifiedStage?.step ?: 0) >= step.step
            ) return
            lastVerifiedStage = step
        }
        handler.post {
            synchronized(lock) {
                if (active !== token || !gate.isLive(token)) return@post
                onVerificationStage(step)
            }
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

    /** These actions only choose the current socket's pending decision. */
    fun approve(): Boolean = synchronized(lock) {
        if (active == null || !DebugRfcommCompletionPolicy.mayChooseConsent(
                gate.snapshot().phase, isForeground(), hasConnectPermission()
            )
        ) false else consent?.approve() ?: false
    }

    fun decline(): Boolean = synchronized(lock) {
        if (active == null || !DebugRfcommCompletionPolicy.mayChooseConsent(
                gate.snapshot().phase, isForeground(), hasConnectPermission()
            )
        ) false else consent?.decline() ?: false
    }

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
        val oldConsent: DebugBluetoothConsentDecision?
        val oldText: DebugBluetoothApprovedTextChannel?
        val lastProof: DebugBluetoothVerifiedStage?
        synchronized(lock) {
            if (active !== token) return
            // opened() itself may already have set STOPPED on a detected
            // permission/foreground failure. We still MUST close owned sockets
            // and clear the attempt even when the model transitioned first.
            if (!gate.stop(token, why) &&
                gate.snapshot().phase != DebugRfcommAttemptGate.Phase.STOPPED
            ) return
            active = null
            lastStoppedToken = token
            cancelTimer()
            oldServer = server
            oldSocket = socket
            oldConsent = consent
            oldText = liveText
            lastProof = lastVerifiedStage
            lastVerifiedStage = null
            server = null
            socket = null
            consent = null
            liveText = null
        }
        oldText?.close()
        // Wake any human-consent waiter before closing its TLS socket.
        oldConsent?.close()
        // Socket closure may unblock accept() / connect() on worker threads.
        closeQuietly(oldServer)
        closeQuietly(oldSocket)
        handler.post {
            // An old connection's queued closure message cannot overwrite
            // a newer user-started link after reset or reconnect.
            synchronized(lock) {
                if (active != null || lastStoppedToken !== token ||
                    gate.snapshot().phase != DebugRfcommAttemptGate.Phase.STOPPED
                ) return@post
            }
            onApprovalReady(false)
            onVerificationStage(lastProof)
            val reason = when (why) {
                DebugRfcommAttemptGate.End.USER -> "You disconnected the test link."
                DebugRfcommAttemptGate.End.BACKGROUND -> "Link closed when the app left the screen."
                DebugRfcommAttemptGate.End.PERMISSION_LOST -> "Bluetooth permission lost; link closed."
                DebugRfcommAttemptGate.End.TIMEOUT -> "Bluetooth connection attempt timed out."
                DebugRfcommAttemptGate.End.EXPIRED -> "Bluetooth debug link reached its time limit and closed."
                DebugRfcommAttemptGate.End.FAILED ->
                    "Bluetooth link/TLS probe failed or peer pin mismatched. No messages sent."
                DebugRfcommAttemptGate.End.CONSENT_CONFIRMED ->
                    "Both people approved this exact QR + TLS session, and " +
                        "both final confirmations were exchanged. Test socket " +
                        "closed; NO messages or voice were enabled."
                DebugRfcommAttemptGate.End.CONSENT_DECLINED ->
                    "Bluetooth test was declined or approval timed out. " +
                        "Connection closed; no messages sent."
                DebugRfcommAttemptGate.End.CHAT_ENDED ->
                    "Bluetooth encrypted test chat ended. A fresh QR exchange " +
                        "and both approvals are required to reconnect."
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
