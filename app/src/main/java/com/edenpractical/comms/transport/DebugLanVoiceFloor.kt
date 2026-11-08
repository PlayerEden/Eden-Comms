package com.edenpractical.comms.transport

import com.edenpractical.comms.protocol.DebugLanVoiceFloorCodec
import com.edenpractical.comms.protocol.TestPacketException

/**
 * One floor state per authenticated, human-approved LAN TLS session.
 * The presenter/host is the sole arbitrator. No global identity, account,
 * discovery, extra socket or retained state. A joiner may only capture after
 * receiving GRANT for its current, strictly increasing request ID.
 *
 * A deadline never silently transfers ownership: the caller must CLOSE
 * the entire connection on expiry to avoid delayed grants / stale PCM.
 */
internal class DebugLanVoiceFloor(private val isHost: Boolean) {
    private enum class Owner { FREE, LOCAL, REMOTE }
    enum class Ask { GRANTED, PENDING, BUSY }
    enum class Signal { NONE, GRANTED, DENIED }
    data class Request(val status: Ask, val packet: DebugLanVoiceFloorCodec.Frame? = null)
    data class Incoming(
        val outbound: DebugLanVoiceFloorCodec.Frame? = null,
        val signal: Signal = Signal.NONE
    )

    private var owner = Owner.FREE
    private var id = 0L
    private var pending = 0L
    private var cancelled = 0L
    private var nextRequest = 0L
    private var lastRemoteRequest = 0L
    private var startedAtMs = 0L
    private var remoteAudioStarted = false
    private var remoteTurnConsumed = false
    private var closed = false

    companion object {
        const val PENDING_TIMEOUT_MS = 3_000L
        // Covers 3s accessible record + up to 3s paced playback fallback,
        // network/scheduler slack and teardown. NOT an indefinite lease.
        const val LEASE_TIMEOUT_MS = 9_000L
    }

    @Synchronized
    fun requestLocal(nowMs: Long): Request {
        if (closed || owner != Owner.FREE || pending != 0L ||
            remoteAudioStarted) return Request(Ask.BUSY)
        if (nextRequest == Long.MAX_VALUE) throw TestPacketException()
        id = ++nextRequest
        startedAtMs = nowMs
        return if (isHost) {
            owner = Owner.LOCAL
            Request(Ask.GRANTED)
        } else {
            pending = id
            Request(Ask.PENDING, DebugLanVoiceFloorCodec.Frame(
                DebugLanVoiceFloorCodec.Kind.REQUEST, id
            ))
        }
    }

    @Synchronized
    fun incoming(frame: DebugLanVoiceFloorCodec.Frame, nowMs: Long): Incoming {
        if (closed) throw TestPacketException()
        val kind = frame.kind
        val requestId = frame.requestId
        if (isHost) {
            return when (kind) {
                DebugLanVoiceFloorCodec.Kind.REQUEST -> {
                    if (requestId <= lastRemoteRequest) throw TestPacketException()
                    lastRemoteRequest = requestId
                    val permitted = owner == Owner.FREE && !remoteAudioStarted
                    if (permitted) {
                        owner = Owner.REMOTE
                        id = requestId
                        startedAtMs = nowMs
                    }
                    Incoming(DebugLanVoiceFloorCodec.Frame(
                        if (permitted) DebugLanVoiceFloorCodec.Kind.GRANT
                        else DebugLanVoiceFloorCodec.Kind.DENY, requestId
                    ))
                }
                DebugLanVoiceFloorCodec.Kind.CANCEL -> {
                    if (requestId > lastRemoteRequest) throw TestPacketException()
                    if (owner == Owner.REMOTE && id == requestId) {
                        if (remoteAudioStarted) throw TestPacketException()
                        free()
                    }
                    Incoming()
                }
                DebugLanVoiceFloorCodec.Kind.RELEASE -> {
                    if (owner != Owner.REMOTE || id != requestId ||
                        remoteAudioStarted) throw TestPacketException()
                    free()
                    Incoming()
                }
                else -> throw TestPacketException()
            }
        }
        return when (kind) {
            DebugLanVoiceFloorCodec.Kind.GRANT,
            DebugLanVoiceFloorCodec.Kind.DENY -> {
                if (pending == requestId) {
                    pending = 0L
                    if (kind == DebugLanVoiceFloorCodec.Kind.GRANT) {
                        owner = Owner.LOCAL
                        startedAtMs = nowMs
                    } else {
                        free()
                    }
                    Incoming(signal = if (kind == DebugLanVoiceFloorCodec.Kind.GRANT)
                        Signal.GRANTED else Signal.DENIED)
                } else if (cancelled == requestId) {
                    // A CANCEL can race with a GRANT already written by host.
                    // The abandoned GRANT must not re-enable microphone.
                    Incoming()
                } else throw TestPacketException()
            }
            else -> throw TestPacketException()
        }
    }

    @Synchronized
    fun maySendAudio(): Boolean = !closed && owner == Owner.LOCAL

    @Synchronized
    fun remoteAudioStart() {
        if (closed || remoteAudioStarted || owner == Owner.LOCAL ||
            (isHost && (owner != Owner.REMOTE || remoteTurnConsumed)))
            throw TestPacketException()
        remoteAudioStarted = true
        remoteTurnConsumed = true
    }

    @Synchronized
    fun remoteAudioEnd() {
        if (closed || !remoteAudioStarted) throw TestPacketException()
        remoteAudioStarted = false
        // For a joiner observing host transmissions there is no explicit
        // host GRANT frame. Each END lets the next host-owned turn begin.
        // A presenter must instead demand an exact RELEASE before allowing
        // another joiner turn.
        if (!isHost) remoteTurnConsumed = false
    }

    @Synchronized
    fun cancelPending(): DebugLanVoiceFloorCodec.Frame? {
        if (closed || isHost || pending == 0L) return null
        cancelled = pending
        pending = 0L
        startedAtMs = 0L
        return DebugLanVoiceFloorCodec.Frame(DebugLanVoiceFloorCodec.Kind.CANCEL, cancelled)
    }

    @Synchronized
    fun releaseLocal(): DebugLanVoiceFloorCodec.Frame? {
        if (closed || owner != Owner.LOCAL) return null
        val releasedId = id
        free()
        return if (isHost) null else DebugLanVoiceFloorCodec.Frame(
            DebugLanVoiceFloorCodec.Kind.RELEASE, releasedId
        )
    }

    @Synchronized
    fun timedOut(nowMs: Long): Boolean =
        !closed && startedAtMs > 0L &&
            nowMs - startedAtMs > if (pending != 0L)
                PENDING_TIMEOUT_MS else LEASE_TIMEOUT_MS

    @Synchronized
    fun close() {
        closed = true
        free()
        cancelled = 0L
    }

    private fun free() {
        owner = Owner.FREE
        remoteTurnConsumed = false
        pending = 0L
        id = 0L
        startedAtMs = 0L
    }
}
