package com.edenpractical.comms.wifidirect

import android.os.Handler
import android.os.Build
import com.edenpractical.comms.bluetooth.DebugBluetoothTlsProbeInput
import com.edenpractical.comms.bluetooth.DebugBluetoothConsentDecision
import com.edenpractical.comms.bluetooth.DebugBluetoothApprovedTextChannel
import com.edenpractical.comms.bluetooth.DebugRfcommTlsProbe
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * F3-C real Android DEBUG Wi-Fi Direct peer-to-peer authenticated text owner.
 *
 * Android OS group + exact OS group-interface IPv4 -> one TCP socket -> same
 * real pinned TLS engine + three in-person QR nonces -> channel exporter +
 * commitment -> explicit approval on EACH phone -> both encrypted CONFIRMs ->
 * one bounded encrypted TEXT/ACK channel. A group, radio candidate, network
 * address or completed TCP connect is NEVER authentication.
 *
 * The actual Bluetooth-named debug TLS protocol objects are intentionally
 * reused on P2P to avoid a weaker second "security" protocol. The transport
 * remains experimental, without independent review/physical OEM QA, accounts,
 * persistence, plaintext fallback, background session or release authority.
 */
internal class DebugWifiDirectApprovedTcpSession(
    private val handler: Handler,
    private val canContinue: () -> Boolean,
    private val onStatus: (Phase, String) -> Unit,
    private val onApprovalReady: (Boolean) -> Unit,
    private val onText: (DebugBluetoothApprovedTextChannel.Event) -> Unit,
    // Public pin metadata for optional user-owned storage, NOT authentication.
    private val onApprovedIdentity: (ByteArray, ByteArray) -> Unit = { _, _ -> }
) : Closeable {
    companion object {
        const val PORT = 39_762 // distinct from zero-payload P2P reachability
        const val CONNECT_TIMEOUT_MS = 4_000
        const val ACCEPT_TIMEOUT_MS = 90_000
        const val INITIAL_DEADLINE_MS = 120_000L
        const val CHAT_DEADLINE_MS = 10L * 60_000L
        const val PROOF_IO_TIMEOUT_MS = 40_000
    }

    enum class Phase { IDLE, CONNECTING, AWAITING_APPROVAL, CHAT, STOPPED }
    private class Attempt(val host: Boolean)

    private val lock = Any()
    private var active: Attempt? = null
    private var listener: ServerSocket? = null
    private var socket: Socket? = null
    private var decision: DebugBluetoothConsentDecision? = null
    private var text: DebugBluetoothApprovedTextChannel? = null
    private var deadline: Runnable? = null
    private var phase = Phase.IDLE
    private var lastStopped: Attempt? = null

    fun phase(): Phase = synchronized(lock) { phase }

    /**
     * Input address is supplied ONLY by Android's formed-group interface
     * resolver and the authenticated request ONLY from the same screen's
     * three-QR coordinator. This class accepts no typed address or peer name.
     */
    fun host(local: InetAddress, request: DebugBluetoothTlsProbeInput): Boolean =
        start(local, null, request, host = true)

    fun join(
        local: InetAddress,
        groupOwner: InetAddress,
        request: DebugBluetoothTlsProbeInput
    ): Boolean = start(local, groupOwner, request, host = false)

    private fun start(
        local: InetAddress, owner: InetAddress?,
        request: DebugBluetoothTlsProbeInput, host: Boolean
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            !canContinue() || request.hostRole != host ||
            !DebugWifiDirectTcpProbe.testAddressAllowed(local) ||
            (!host && (owner == null || owner == local ||
                !DebugWifiDirectTcpProbe.testAddressAllowed(owner)))
        ) {
            request.close()
            return false
        }
        val token = Attempt(host)
        synchronized(lock) {
            if (active != null || phase == Phase.CONNECTING || phase == Phase.CHAT ||
                phase == Phase.AWAITING_APPROVAL
            ) {
                request.close()
                return false
            }
            active = token
            phase = Phase.CONNECTING
            arm(token, INITIAL_DEADLINE_MS)
        }
        signal(token, Phase.CONNECTING,
            "Opening one TCP socket bound only to Android's Wi-Fi Direct interface.")
        Thread({
            try {
                if (host) {
                    val server = ServerSocket()
                    synchronized(lock) {
                        if (active === token) listener = server else server.close()
                    }
                    server.reuseAddress = false
                    server.bind(InetSocketAddress(local, PORT), 1)
                    server.soTimeout = ACCEPT_TIMEOUT_MS
                    val accepted = server.accept()
                    synchronized(lock) {
                        if (listener === server) listener = null
                    }
                    closeQuietly(server)
                    if (!claimSocket(token, accepted, local)) return@Thread
                    process(token, accepted, request)
                } else {
                    val outbound = Socket()
                    if (!claimSocket(token, outbound, null)) return@Thread
                    outbound.bind(InetSocketAddress(local, 0))
                    outbound.connect(InetSocketAddress(owner, PORT), CONNECT_TIMEOUT_MS)
                    if (outbound.localAddress != local ||
                        outbound.inetAddress != owner
                    ) throw IOException("Wi-Fi Direct route/interface mismatch")
                    process(token, outbound, request)
                }
            } catch (_: Exception) {
                stop(token, "Wi-Fi Direct socket, authentication or session failed.")
            } finally {
                request.close()
                stop(token, "Wi-Fi Direct session ended; start fresh QR to reconnect.")
            }
        }, "EC-debug-p2p-verified-tls-text").apply { isDaemon = true }.start()
        return true
    }

    private fun claimSocket(token: Attempt, created: Socket, local: InetAddress?): Boolean =
        synchronized(lock) {
            if (active !== token || !canContinue() ||
                (local != null && created.localAddress != local)
            ) {
                closeQuietly(created)
                false
            } else {
                socket = created
                true
            }
        }

    private fun process(
        token: Attempt, current: Socket, request: DebugBluetoothTlsProbeInput
    ) {
        current.soTimeout = PROOF_IO_TIMEOUT_MS
        // MUST run on the same raw TCP socket. We never construct a second
        // SSLEngine or accept a caller-provided "approved" Boolean.
        val receipt = DebugRfcommTlsProbe.runStreams(
            current.getInputStream(), current.getOutputStream(), request,
            onApprovedChannel = { engine, wireInput, wireOutput ->
                val approved = DebugBluetoothApprovedTextChannel(
                    engine, wireInput, wireOutput
                ) { event -> textEvent(token, event) }
                val admitted = synchronized(lock) {
                    if (active === token && canContinue() &&
                        phase == Phase.AWAITING_APPROVAL && text == null
                    ) {
                        text = approved
                        phase = Phase.CHAT
                        current.soTimeout = CHAT_DEADLINE_MS.toInt()
                        arm(token, CHAT_DEADLINE_MS)
                        true
                    } else false
                }
                if (!admitted) {
                    approved.close()
                    throw IOException("Wi-Fi Direct verified session no longer current")
                }
                // Transport is now the exact authenticated TLS engine after
                // QR/exporter, both person-APPROVE and both encrypted CONFIRM.
                // A saved fingerprint must never influence this admission.
                val ownForUi = request.localIdentity.publicFingerprint()
                val peerForUi = try { request.pinCopy() } catch (_: Exception) { null }
                if (peerForUi != null) {
                    handler.post {
                        try {
                            synchronized(lock) {
                                if (active === token && text === approved &&
                                    phase == Phase.CHAT && canContinue()
                                ) onApprovedIdentity(ownForUi, peerForUi)
                            }
                        } finally {
                            ownForUi.fill(0)
                            peerForUi.fill(0)
                        }
                    }
                } else ownForUi.fill(0)
                signal(token, Phase.CHAT, "Verified Wi-Fi Direct encrypted test chat.")
                try {
                    approved.runUntilClosed()
                } finally { approved.close() }
            }
        ) { pending ->
            synchronized(lock) {
                if (active === token && canContinue() &&
                    phase == Phase.CONNECTING
                ) {
                    decision = pending
                    phase = Phase.AWAITING_APPROVAL
                    signal(token, Phase.AWAITING_APPROVAL,
                        "Both phones passed TLS, QR and exporter checks. Approve independently.")
                    handler.post {
                        synchronized(lock) {
                            if (active === token && decision === pending &&
                                phase == Phase.AWAITING_APPROVAL
                            ) onApprovalReady(true)
                        }
                    }
                } else pending.close()
            }
        }
        receipt.close()
    }

    fun approve(): Boolean = synchronized(lock) {
        if (active == null || !canContinue() ||
            phase != Phase.AWAITING_APPROVAL
        ) false else decision?.approve() ?: false
    }

    fun decline(): Boolean = synchronized(lock) {
        if (active == null || !canContinue() ||
            phase != Phase.AWAITING_APPROVAL
        ) false else decision?.decline() ?: false
    }

    fun offerText(message: String): Boolean {
        val current = synchronized(lock) {
            if (active == null || !canContinue() || phase != Phase.CHAT)
                null else text
        } ?: return false
        return current.offerText(message)
    }

    private fun textEvent(token: Attempt, event: DebugBluetoothApprovedTextChannel.Event) {
        if (event is DebugBluetoothApprovedTextChannel.Event.Failed) {
            stop(token, "Wi-Fi Direct encrypted writer failed; connection closed.")
            return
        }
        handler.post {
            synchronized(lock) {
                if (active !== token || phase != Phase.CHAT ||
                    !canContinue() || text == null
                ) return@post
            }
            onText(event)
        }
    }

    private fun signal(token: Attempt, state: Phase, message: String) {
        handler.post {
            synchronized(lock) {
                if (active !== token || phase != state ||
                    !canContinue()
                ) return@post
            }
            onStatus(state, message)
        }
    }

    private fun arm(token: Attempt, durationMs: Long) {
        deadline?.let { handler.removeCallbacks(it) }
        deadline = Runnable { stop(token, "Wi-Fi Direct session timed out and closed.") }
            .also { handler.postDelayed(it, durationMs) }
    }

    override fun close() {
        val token = synchronized(lock) { active } ?: return
        stop(token, "Disconnected; fresh QR and approvals required to reconnect.")
    }

    private fun stop(token: Attempt, note: String) {
        val closeListener: ServerSocket?
        val closeSocket: Socket?
        val closeDecision: DebugBluetoothConsentDecision?
        val closeText: DebugBluetoothApprovedTextChannel?
        synchronized(lock) {
            if (active !== token) return
            active = null
            phase = Phase.STOPPED
            lastStopped = token
            deadline?.let { handler.removeCallbacks(it) }
            deadline = null
            closeListener = listener
            closeSocket = socket
            closeDecision = decision
            closeText = text
            listener = null
            socket = null
            decision = null
            text = null
        }
        closeDecision?.close()
        closeText?.close()
        closeQuietly(closeSocket)
        closeQuietly(closeListener)
        handler.post {
            synchronized(lock) {
                if (active != null || lastStopped !== token) return@post
            }
            onApprovalReady(false)
            onStatus(Phase.STOPPED, note)
        }
    }

    private fun closeQuietly(item: Closeable?) {
        try { item?.close() } catch (_: Exception) { }
    }
}
