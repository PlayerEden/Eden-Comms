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
 * F5-A experimental opt-in encrypted local history. DEBUG-ONLY BUILD SOURCE.
 *
 * No write occurs unless the verified conversation explicitly opts in; not
 * opened by QR, discovery, a message draft or the production app. The file
 * contains *only* an AES-256-GCM ciphertext with an Android Keystore key.
 * All record metadata and verified public peer pins are INSIDE the ciphertext.
 * No peer IPs, accounts, private keys, remote copies, plaintext cache or WAL.
 *
 * Backups and migration are blocked by manifest rules and noBackupFilesDir.
 * Not independently security reviewed; "erase" means best-effort app deletion.
 */
internal class DebugLocalHistoryStore(context: Context) {
    companion object {
        private const val KEY_ALIAS = "eden_comms_debug_history_aes_1"
        private const val DIR = "eden_comms_local_history_debug_v1"
        private const val FILE = "encrypted.ec1"
        private const val TEMP = "encrypted.ec1.tmp"
        private const val PAYLOAD_MAGIC = 0x45434850 // ECHP
        private const val VERSION = 1
        private const val PIN_SIZE = 32
    }

    private val app = context.applicationContext
    private val enabled = app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    private val dir = File(app.noBackupFilesDir, DIR)
    private val file = File(dir, FILE)
    private val temp = File(dir, TEMP)
    private val keyStore: KeyStore
        get() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private class Peer(val pin: ByteArray, val rows: MutableList<LocalHistoryPolicy.SavedMessage>)
    private class State(
        var lastSeenMs: Long,
        val peers: MutableList<Peer> = mutableListOf()
    )

    /** Never log exceptions or plaintext. Any invalid/corrupt file fails closed. */
    @Synchronized
    fun save(
        peerPin: ByteArray,
        direction: LocalHistoryPolicy.Direction,
        id: Long,
        text: String,
        durationMs: Long,
        nowWallMs: Long
    ): Boolean {
        if (!enabled || !validPin(peerPin) || id <= 0L ||
            durationMs == LocalHistoryPolicy.SESSION_ONLY
        ) return false
        val expiry = LocalHistoryPolicy.expiry(nowWallMs, durationMs) ?: return false
        val entry = LocalHistoryPolicy.SavedMessage(
            direction, id, text, nowWallMs, expiry
        )
        if (!LocalHistoryPolicy.valid(entry)) return false
        return try {
            val state = readState(nowWallMs) ?: State(nowWallMs)
            var peer = state.peers.firstOrNull { it.pin.contentEquals(peerPin) }
            if (peer == null) {
                if (state.peers.size >= LocalHistoryPolicy.MAX_PEERS) return false
                peer = Peer(peerPin.copyOf(), mutableListOf())
                state.peers.add(peer)
            }
            peer.rows.add(entry)
            peer.rows.retainAll(LocalHistoryPolicy.visible(peer.rows, nowWallMs))
            state.lastSeenMs = maxOf(state.lastSeenMs, nowWallMs)
            writeState(state)
        } catch (_: Exception) {
            // Key invalidation, storage failure, corrupt bytes are not success.
            false
        }
    }

    /**
     * Read is only called after this phone has reverified the SAME peer pin.
     * Expired or invalid records never reach UI even if delayed deletion fails.
     */
    @Synchronized
    fun read(peerPin: ByteArray, nowWallMs: Long): List<LocalHistoryPolicy.SavedMessage> {
        if (!enabled || !validPin(peerPin)) return emptyList()
        return try {
            val state = readState(nowWallMs) ?: return emptyList()
            val peer = state.peers.firstOrNull { it.pin.contentEquals(peerPin) }
                ?: return emptyList()
            LocalHistoryPolicy.visible(peer.rows, nowWallMs)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Shorten immediately; a longer choice applies only to future saves.
     * Turning OFF deletes this peer's local encrypted records, not the other
     * party's text or any Android/keyboard/screenshot copies.
     */
    @Synchronized
    fun changeRetention(peerPin: ByteArray, choiceMs: Long, nowWallMs: Long): Boolean {
        if (!enabled || !validPin(peerPin) || !LocalHistoryPolicy.supported(choiceMs))
            return false
        return try {
            val state = readState(nowWallMs) ?: return true
            val peer = state.peers.firstOrNull { it.pin.contentEquals(peerPin) }
                ?: return true
            val shorter = LocalHistoryPolicy.shorten(peer.rows, nowWallMs, choiceMs)
            peer.rows.clear()
            peer.rows.addAll(shorter)
            state.peers.removeAll { it.rows.isEmpty() }
            if (state.peers.isEmpty()) {
                deleteCiphertext()
                true
            } else {
                state.lastSeenMs = maxOf(state.lastSeenMs, nowWallMs)
                writeState(state)
            }
        } catch (_: Exception) { false }
    }

    @Synchronized
    fun erasePeer(peerPin: ByteArray, nowWallMs: Long): Boolean =
        changeRetention(peerPin, LocalHistoryPolicy.SESSION_ONLY, nowWallMs)

    @Synchronized
    fun eraseAll(): Boolean {
        if (!enabled) return false
        return deleteCiphertext()
    }

    private fun validPin(pin: ByteArray): Boolean =
        pin.size == PIN_SIZE && pin.any { it != 0.toByte() }

    /** Fresh key ONLY on the first explicit opt-in write, never on read. */
    private fun key(create: Boolean): SecretKey? {
        val store = keyStore
        if (store.containsAlias(KEY_ALIAS)) {
            return (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        }
        if (!create) return null
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                ).setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .setUserAuthenticationRequired(false)
                    .build())
            }.generateKey()
    }

    private fun aad(): ByteArray =
        ("eden-comms/debug-history-v1/" + app.packageName)
            .toByteArray(StandardCharsets.US_ASCII)

    private fun readState(nowMs: Long): State? {
        if (!file.exists()) return null
        try {
            val bytes = file.readBytes()
            val aes = key(false) ?: error("Missing history key")
            val encrypted = LocalHistoryEnvelope.open(aes, bytes, aad())
            try {
                val state = parse(encrypted)
                // Offline wall clock has no trusted source. Clear on rollback.
                if (!LocalHistoryPolicy.clockSafe(nowMs, state.lastSeenMs)) {
                    deleteCiphertext()
                    return null
                }
                state.peers.forEach { peer ->
                    peer.rows.retainAll(LocalHistoryPolicy.visible(peer.rows, nowMs))
                }
                state.peers.removeAll { it.rows.isEmpty() }
                if (state.peers.isEmpty()) {
                    deleteCiphertext()
                    return null
                }
                state.lastSeenMs = maxOf(state.lastSeenMs, nowMs)
                // Persist updated clock and expiration before exposing rows.
                if (!writeState(state)) return null
                return state
            } finally { encrypted.fill(0) }
        } catch (_: Exception) {
            deleteCiphertext()
            return null
        }
    }

    private fun parse(bytes: ByteArray): State =
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == PAYLOAD_MAGIC)
            require(input.readUnsignedByte() == VERSION)
            val clock = input.readLong()
            require(clock > 0)
            val count = input.readUnsignedByte()
            require(count in 1..LocalHistoryPolicy.MAX_PEERS)
            val peers = mutableListOf<Peer>()
            repeat(count) {
                val pin = ByteArray(PIN_SIZE)
                input.readFully(pin)
                require(validPin(pin) && peers.none { it.pin.contentEquals(pin) })
                val rowsCount = input.readUnsignedByte()
                require(rowsCount in 1..LocalHistoryPolicy.MAX_RECORDS_PER_PEER)
                val rows = mutableListOf<LocalHistoryPolicy.SavedMessage>()
                repeat(rowsCount) {
                    val direction = when (input.readUnsignedByte()) {
                        1 -> LocalHistoryPolicy.Direction.WRITTEN_HERE
                        2 -> LocalHistoryPolicy.Direction.RECEIVED_HERE
                        else -> error("Bad direction")
                    }
                    val entry = LocalHistoryPolicy.SavedMessage(
                        direction, input.readLong(), input.readUTF(),
                        input.readLong(), input.readLong()
                    )
                    require(LocalHistoryPolicy.valid(entry))
                    rows.add(entry)
                }
                peers.add(Peer(pin, rows))
            }
            require(input.available() == 0)
            State(clock, peers)
        }

    private fun writeState(state: State): Boolean {
        if (state.peers.isEmpty()) return deleteCiphertext()
        val plaintext = ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(PAYLOAD_MAGIC)
                output.writeByte(VERSION)
                output.writeLong(state.lastSeenMs)
                output.writeByte(state.peers.size)
                state.peers.forEach { peer ->
                    output.write(peer.pin)
                    output.writeByte(peer.rows.size)
                    peer.rows.forEach { row ->
                        output.writeByte(
                            if (row.direction == LocalHistoryPolicy.Direction.WRITTEN_HERE)
                                1 else 2
                        )
                        output.writeLong(row.sourceId)
                        output.writeUTF(row.text)
                        output.writeLong(row.createdAtMs)
                        output.writeLong(row.expiresAtMs)
                    }
                }
            }
            bytes.toByteArray()
        }
        try {
            val aes = key(true) ?: return false
            val sealed = LocalHistoryEnvelope.seal(aes, plaintext, aad())
            require(dir.isDirectory || dir.mkdirs())
            FileOutputStream(temp).use { output ->
                output.write(sealed)
                output.flush()
                output.fd.sync()
            }
            return temp.renameTo(file)
        } finally { plaintext.fill(0) }
    }

    private fun deleteCiphertext(): Boolean {
        if (temp.exists() && !temp.delete()) return false
        return !file.exists() || file.delete()
    }
}
