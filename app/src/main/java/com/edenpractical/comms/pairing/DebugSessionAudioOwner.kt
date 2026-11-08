package com.edenpractical.comms.pairing

/**
 * F4-J: permits only the CURRENT nearby LAN session to stop local
 * microphone/speaker hardware when its encrypted connection closes.
 *
 * The closure callback can run on a networking thread, independent of the
 * Android UI event queue. A delayed callback from an older QR/TLS attempt
 * must never stop the audio belonging to a newer approved connection.
 *
 * Never stores a peer identity, voice data, network handle or credentials.
 */
internal class DebugSessionAudioOwner {
    private var current: Any? = null

    @Synchronized
    fun activate(owner: Any) {
        current = owner
    }

    @Synchronized
    fun revoke() {
        current = null
    }

    /**
     * Cancellation is executed under this short ownership lock, so an old
     * close cannot pass the identity check then cancel audio AFTER a new
     * session has been activated on a different thread.
     *
     * The callback must only perform local, best-effort audio cleanup.
     */
    @Synchronized
    fun stopIfCurrent(owner: Any, stopAudio: () -> Unit): Boolean {
        if (current !== owner) return false
        current = null
        stopAudio()
        return true
    }
}
