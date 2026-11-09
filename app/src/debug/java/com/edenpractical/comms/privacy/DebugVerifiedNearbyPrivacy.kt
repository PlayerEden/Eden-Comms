package com.edenpractical.comms.privacy

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.edenpractical.comms.bluetooth.DebugBluetoothApprovedTextChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * F5-C DEBUG-only privacy controller shared by live Bluetooth Classic and
 * Wi-Fi Direct. Never opens a radio, trusts stored pins or admits TLS sessions.
 *
 * The transport calls openVerified ONLY from its already adopted same-channel
 * pinned TLS + fresh QR + exporter + bilateral CONFIRM callback. Text can
 * only be persisted on actual encrypted writer/reader events from that
 * current session, and only AFTER explicit per-session opt-in.
 *
 * Serializes all disk operations to honor Save -> Off/Delete order and fences
 * any late asynchronous read result after disconnect, new session or Forget.
 */
internal class DebugVerifiedNearbyPrivacy(context: Context) {
    private val history = DebugLocalHistoryStore(context.applicationContext)
    private val peers = DebugPeerMemoryStore(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val storageMutex = Mutex()
    private var own: ByteArray? = null
    private var other: ByteArray? = null
    private var revision = 0

    var verified by mutableStateOf(false)
        private set
    var retentionMs by mutableLongStateOf(LocalHistoryPolicy.SESSION_ONLY)
        private set
    var rows by mutableStateOf(emptyList<LocalHistoryPolicy.SavedMessage>())
        private set
    var historyNote by mutableStateOf(
        "Session only. Nothing saved unless you explicitly choose a duration."
    )
        private set
    var rememberedUntilMs by mutableStateOf<Long?>(null)
        private set
    var peerNote by mutableStateOf("No previously remembered peer checked.")
        private set
    var busy by mutableStateOf(false)
        private set

    /** Not callable from QR preparation, discovery, or an approval prompt. */
    fun openVerified(ownPin: ByteArray, verifiedPeerPin: ByteArray) {
        close()
        if (!PeerMemoryPolicy.validPin(ownPin) ||
            !PeerMemoryPolicy.validPin(verifiedPeerPin) ||
            ownPin.contentEquals(verifiedPeerPin)
        ) return
        own = ownPin.copyOf()
        other = verifiedPeerPin.copyOf()
        verified = true
        val ticket = revision
        val self = ownPin.copyOf()
        val peer = verifiedPeerPin.copyOf()
        peerNote = "Checking this phone's saved public fingerprint after fresh verification."
        scope.launch {
            try {
                val expiry = storageMutex.withLock {
                    withContext(Dispatchers.IO) {
                        peers.rememberedUntil(self, peer, System.currentTimeMillis())
                    }
                }
                if (current(ticket)) {
                    rememberedUntilMs = expiry
                    peerNote = if (expiry == null)
                        "No saved peer identity for this verified phone."
                    else "Previously remembered public fingerprint matched. " +
                        "Fresh QR/TLS and both approvals were still required."
                }
            } finally {
                self.fill(0)
                peer.fill(0)
            }
        }
    }

    fun receiveEncryptedEvent(event: DebugBluetoothApprovedTextChannel.Event) {
        if (!verified) return
        val direction: LocalHistoryPolicy.Direction
        val id: Long
        val text: String
        when (event) {
            is DebugBluetoothApprovedTextChannel.Event.Written -> {
                direction = LocalHistoryPolicy.Direction.WRITTEN_HERE
                id = event.id
                text = event.text
            }
            is DebugBluetoothApprovedTextChannel.Event.Received -> {
                direction = LocalHistoryPolicy.Direction.RECEIVED_HERE
                id = event.id
                text = event.text
            }
            else -> return // Ready/ACK/Failed are NEVER message-history entries.
        }
        val duration = retentionMs
        if (duration == LocalHistoryPolicy.SESSION_ONLY) return
        val peer = other?.copyOf() ?: return
        val ticket = revision
        scope.launch {
            try {
                storageMutex.withLock {
                    if (!current(ticket) || retentionMs == LocalHistoryPolicy.SESSION_ONLY)
                        return@withLock
                    val saved = withContext(Dispatchers.IO) {
                        history.save(
                            peer, direction, id, text,
                            duration, System.currentTimeMillis()
                        )
                    }
                    if (!saved && current(ticket))
                        historyNote = "This encrypted test message could not be saved."
                }
            } finally { peer.fill(0) }
        }
    }

    fun chooseRetention(duration: Long) {
        if (!verified || busy || !LocalHistoryPolicy.supported(duration)) return
        val peer = other?.copyOf() ?: return
        val ticket = revision
        busy = true
        scope.launch {
            try {
                storageMutex.withLock {
                    if (!current(ticket)) return@withLock
                    val now = System.currentTimeMillis()
                    val changed = withContext(Dispatchers.IO) {
                        history.changeRetention(peer, duration, now)
                    }
                    if (!current(ticket)) return@withLock
                    if (!changed) {
                        historyNote = "Local retention couldn't be changed. No success assumed."
                    } else {
                        retentionMs = duration
                        rows = if (duration == LocalHistoryPolicy.SESSION_ONLY)
                            emptyList() else withContext(Dispatchers.IO) {
                                history.read(peer, System.currentTimeMillis())
                            }
                        historyNote = if (duration == LocalHistoryPolicy.SESSION_ONLY)
                            "Session only. Saved messages for this verified peer erased here."
                        else "Only NEW verified text will be stored for " +
                            LocalHistoryPolicy.label(duration) + "; max 30 days."
                    }
                }
            } finally {
                peer.fill(0)
                if (current(ticket)) busy = false
            }
        }
    }

    fun viewSaved() {
        if (!verified || busy) return
        val peer = other?.copyOf() ?: return
        val ticket = revision
        busy = true
        scope.launch {
            try {
                val result = storageMutex.withLock {
                    if (!current(ticket)) emptyList()
                    else withContext(Dispatchers.IO) {
                        history.read(peer, System.currentTimeMillis())
                    }
                }
                if (current(ticket)) {
                    rows = result
                    historyNote = "Only unexpired messages on this phone; no remote read receipt."
                }
            } finally {
                peer.fill(0)
                if (current(ticket)) busy = false
            }
        }
    }

    fun eraseSaved() = chooseRetention(LocalHistoryPolicy.SESSION_ONLY)

    fun remember(days: Int) {
        if (!verified || busy || days !in 1..PeerTrustExpiry.MAX_DAYS) return
        val self = own?.copyOf() ?: return
        val peer = other?.copyOf() ?: run { self.fill(0); return }
        val ticket = revision
        busy = true
        scope.launch {
            try {
                val expiry = storageMutex.withLock {
                    if (!current(ticket)) null
                    else withContext(Dispatchers.IO) {
                        peers.remember(self, peer, days, System.currentTimeMillis())
                    }
                }
                if (current(ticket)) {
                    if (expiry != null) rememberedUntilMs = expiry
                    peerNote = if (expiry == null)
                        "Could not save this public peer identity."
                    else "Public fingerprint saved on THIS phone. " +
                        "No automatic authentication or renewal."
                }
            } finally {
                peer.fill(0); self.fill(0)
                if (current(ticket)) busy = false
            }
        }
    }

    fun forgetPeer() {
        if (!verified || busy) return
        val self = own?.copyOf() ?: return
        val peer = other?.copyOf() ?: run { self.fill(0); return }
        val ticket = revision
        busy = true
        scope.launch {
            try {
                val success = storageMutex.withLock {
                    if (!current(ticket)) false
                    else withContext(Dispatchers.IO) {
                        peers.forget(self, peer, System.currentTimeMillis())
                    }
                }
                if (current(ticket)) {
                    if (success) rememberedUntilMs = null
                    peerNote = if (success) "Public peer record forgotten on this phone."
                    else "Could not confirm peer record deletion."
                }
            } finally {
                peer.fill(0); self.fill(0)
                if (current(ticket)) busy = false
            }
        }
    }

    fun forgetAll() {
        if (!verified || busy) return
        val ticket = revision
        busy = true
        scope.launch {
            val success = storageMutex.withLock {
                if (!current(ticket)) false
                else withContext(Dispatchers.IO) { peers.forgetAll() }
            }
            if (current(ticket)) {
                if (success) rememberedUntilMs = null
                peerNote = if (success) "All remembered public peers erased on this phone."
                else "Could not confirm all remembered peer records were removed."
                busy = false
            }
        }
    }

    /** Called on STOP, background, permission loss and activity destruction. */
    fun close() {
        revision++
        verified = false
        own?.fill(0)
        other?.fill(0)
        own = null
        other = null
        retentionMs = LocalHistoryPolicy.SESSION_ONLY
        rows = emptyList()
        rememberedUntilMs = null
        busy = false
        historyNote = "Session only. No active verified phone."
        peerNote = "No active verified phone."
    }

    fun dispose() {
        close()
        scope.cancel()
    }

    private fun current(ticket: Int): Boolean = verified && ticket == revision
}
