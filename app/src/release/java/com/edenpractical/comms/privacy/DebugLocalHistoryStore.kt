package com.edenpractical.comms.privacy

import android.content.Context

/**
 * F5-A release source intentionally has NO persistence key, file or cipher.
 * Debug-only, unaudited opt-in local history is not a released capability.
 */
internal class DebugLocalHistoryStore(@Suppress("UNUSED_PARAMETER") context: Context) {
    fun save(
        peerPin: ByteArray,
        direction: LocalHistoryPolicy.Direction,
        id: Long, text: String, durationMs: Long, nowWallMs: Long
    ): Boolean = false

    fun read(peerPin: ByteArray, nowWallMs: Long):
        List<LocalHistoryPolicy.SavedMessage> = emptyList()

    fun changeRetention(peerPin: ByteArray, choiceMs: Long, nowWallMs: Long):
        Boolean = false

    fun erasePeer(peerPin: ByteArray, nowWallMs: Long): Boolean = false
    fun eraseAll(): Boolean = false
}
