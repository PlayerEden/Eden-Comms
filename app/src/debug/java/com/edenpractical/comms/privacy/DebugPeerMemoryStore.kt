package com.edenpractical.comms.privacy

import android.content.Context
import android.content.pm.ApplicationInfo
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * F5-B, DEBUG ONLY. Explicitly remembered PUBLIC certificate fingerprints.
 *
 * Separate from F5-A message ciphertext: dedicated AES-GCM key, AAD, directory
 * and file; no history bodies, aliases, addresses, names, nonces or private keys.
 * The same local identity's public pin is sealed inside the record, so a reset
 * of this phone's identity invalidates old records even before cleanup.
 *
 * Never used as an input to TLS/QR/approvals. Reading is allowed only from
 * already confirmed app UI, and can report "seen before", never "trusted".
 */
internal class DebugPeerMemoryStore(context: Context) {
    companion object {
        private const val KEY_ALIAS = "eden_comms_debug_peer_memory_aes_1"
        private const val DIRECTORY = "eden_comms_peer_memory_debug_v1"
        private const val CONTENT = "peers.ec1"
        private const val TEMP = "peers.ec1.tmp"
        private const val MAGIC = 0x4543504d // ECPM
        private const val VERSION = 1
    }

    private val app = context.applicationContext
    private val isDebug = app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    private val directory = File(app.noBackupFilesDir, DIRECTORY)
    private val file = File(directory, CONTENT)
    private val temp = File(directory, TEMP)

    private data class State(
        val ownPin: ByteArray,
        var lastSeenMs: Long,
        val peers: MutableList<PeerMemoryPolicy.Saved> = mutableListOf()
    )

    private val androidKeyStore: KeyStore
        get() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    /** No key, file or preference is created on read, QR scan or discovery. */
    @Synchronized
    fun rememberedUntil(
        ownPin: ByteArray, verifiedPeerPin: ByteArray, nowMs: Long
    ): Long? {
        if (!isDebug || !PeerMemoryPolicy.validPin(ownPin) ||
            !PeerMemoryPolicy.validPin(verifiedPeerPin)) return null
        return try {
            readState(ownPin, nowMs)?.peers
                ?.firstOrNull { it.pin.contentEquals(verifiedPeerPin) }
                ?.expiresAtMs
        } catch (_: Exception) { null }
    }

    /** Called only from an explicit user action in an already confirmed chat. */
    @Synchronized
    fun remember(
        ownPin: ByteArray, verifiedPeerPin: ByteArray, days: Int, nowMs: Long
    ): Long? {
        if (!isDebug || !PeerMemoryPolicy.validPin(ownPin) ||
            !PeerMemoryPolicy.validPin(verifiedPeerPin) ||
            ownPin.contentEquals(verifiedPeerPin)) return null
        val requestedExpiry =
            PeerMemoryPolicy.expiryForExplicitChoice(nowMs, days) ?: return null
        return try {
            val state = readState(ownPin, nowMs) ?: State(ownPin.copyOf(), nowMs)
            val previous = state.peers.firstOrNull {
                it.pin.contentEquals(verifiedPeerPin)
            }
            if (previous == null && state.peers.size >= PeerMemoryPolicy.MAX_PEERS)
                return null
            val expiry = PeerMemoryPolicy.retainedExpiry(
                previous?.expiresAtMs, requestedExpiry
            )
            if (previous != null) state.peers.remove(previous)
            state.peers.add(PeerMemoryPolicy.Saved(
                verifiedPeerPin.copyOf(),
                previous?.savedAtMs ?: nowMs,
                expiry
            ))
            state.lastSeenMs = maxOf(state.lastSeenMs, nowMs)
            if (writeState(state)) expiry else null
        } catch (_: Exception) { null }
    }

    @Synchronized
    fun forget(ownPin: ByteArray, verifiedPeerPin: ByteArray, nowMs: Long): Boolean {
        if (!isDebug || !PeerMemoryPolicy.validPin(ownPin) ||
            !PeerMemoryPolicy.validPin(verifiedPeerPin)) return false
        return try {
            val state = readState(ownPin, nowMs) ?: return true
            state.peers.removeAll { it.pin.contentEquals(verifiedPeerPin) }
            state.lastSeenMs = maxOf(state.lastSeenMs, nowMs)
            if (state.peers.isEmpty()) deleteCiphertext() else writeState(state)
        } catch (_: Exception) { false }
    }

    /** Explicit all-peers forget or local identity reset; messages are separate. */
    @Synchronized
    fun forgetAll(): Boolean {
        if (!isDebug) return false
        return try {
            val removed = deleteCiphertext()
            if (removed) {
                val store = androidKeyStore
                if (store.containsAlias(KEY_ALIAS)) store.deleteEntry(KEY_ALIAS)
            }
            removed
        } catch (_: Exception) { false }
    }

    private fun aad(): ByteArray =
        ("eden-comms/debug-peer-memory-v1/" + app.packageName)
            .toByteArray(StandardCharsets.US_ASCII)

    /** Generate only on a fully opted-in write; never silently on read. */
    private fun aesKey(create: Boolean): SecretKey? {
        val store = androidKeyStore
        if (store.containsAlias(KEY_ALIAS)) {
            return (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        }
        if (!create) return null
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    ).setKeySize(256)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .setUserAuthenticationRequired(false)
                        .build()
                )
            }.generateKey()
    }

    private fun readState(ownPin: ByteArray, nowMs: Long): State? {
        if (!file.exists()) return null
        try {
            val bytes = file.readBytes()
            val key = aesKey(false) ?: error("Missing encryption key")
            val decoded = LocalHistoryEnvelope.open(key, bytes, aad())
            try {
                val state = parse(decoded)
                if (!state.ownPin.contentEquals(ownPin) ||
                    !PeerMemoryPolicy.clockSafe(nowMs, state.lastSeenMs)) {
                    deleteCiphertext()
                    return null
                }
                state.peers.removeAll { !PeerMemoryPolicy.valid(it, nowMs) }
                if (state.peers.isEmpty()) {
                    deleteCiphertext()
                    return null
                }
                state.lastSeenMs = maxOf(nowMs, state.lastSeenMs)
                // The validity check above is performed before exposing any
                // record. Persist purges/clock evidence; failed write hides all.
                return if (writeState(state)) state else null
            } finally { decoded.fill(0) }
        } catch (_: Exception) {
            // Corruption, key loss or rollback never confers remembered status.
            deleteCiphertext()
            return null
        }
    }

    private fun parse(encoded: ByteArray): State =
        DataInputStream(ByteArrayInputStream(encoded)).use { input ->
            require(input.readInt() == MAGIC)
            require(input.readUnsignedByte() == VERSION)
            val own = ByteArray(PeerMemoryPolicy.PIN_BYTES)
            input.readFully(own)
            require(PeerMemoryPolicy.validPin(own))
            val clock = input.readLong()
            require(clock > 0L)
            val count = input.readUnsignedByte()
            require(count in 1..PeerMemoryPolicy.MAX_PEERS)
            val peers = mutableListOf<PeerMemoryPolicy.Saved>()
            repeat(count) {
                val peer = ByteArray(PeerMemoryPolicy.PIN_BYTES)
                input.readFully(peer)
                val created = input.readLong()
                val expires = input.readLong()
                require(PeerMemoryPolicy.validPin(peer) &&
                    !peer.contentEquals(own) &&
                    peers.none { it.pin.contentEquals(peer) } &&
                    created > 0L && expires > created &&
                    expires - created <= PeerMemoryPolicy.MAX_AGE_MS)
                peers.add(PeerMemoryPolicy.Saved(peer, created, expires))
            }
            require(input.available() == 0)
            State(own, clock, peers)
        }

    private fun writeState(state: State): Boolean {
        if (state.peers.isEmpty()) return deleteCiphertext()
        val plain = ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(MAGIC)
                output.writeByte(VERSION)
                output.write(state.ownPin)
                output.writeLong(state.lastSeenMs)
                output.writeByte(state.peers.size)
                state.peers.forEach { entry ->
                    output.write(entry.pin)
                    output.writeLong(entry.savedAtMs)
                    output.writeLong(entry.expiresAtMs)
                }
            }
            bytes.toByteArray()
        }
        try {
            val key = aesKey(true) ?: return false
            val ciphertext = LocalHistoryEnvelope.seal(key, plain, aad())
            require(directory.isDirectory || directory.mkdirs())
            FileOutputStream(temp).use { output ->
                output.write(ciphertext)
                output.flush()
                output.fd.sync()
            }
            return temp.renameTo(file)
        } finally { plain.fill(0) }
    }

    private fun deleteCiphertext(): Boolean =
        (!temp.exists() || temp.delete()) && (!file.exists() || file.delete())
}
