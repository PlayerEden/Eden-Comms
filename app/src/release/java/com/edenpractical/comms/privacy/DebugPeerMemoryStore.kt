package com.edenpractical.comms.privacy

import android.content.Context

/** Release cannot read, save, or reauthorize a peer via experimental memory. */
internal class DebugPeerMemoryStore(@Suppress("UNUSED_PARAMETER") context: Context) {
    fun rememberedUntil(ownPin: ByteArray, verifiedPeerPin: ByteArray, nowMs: Long):
        Long? = null
    fun remember(ownPin: ByteArray, verifiedPeerPin: ByteArray, days: Int, nowMs: Long):
        Long? = null
    fun forget(ownPin: ByteArray, verifiedPeerPin: ByteArray, nowMs: Long):
        Boolean = false
    fun forgetAll(): Boolean = false
}
