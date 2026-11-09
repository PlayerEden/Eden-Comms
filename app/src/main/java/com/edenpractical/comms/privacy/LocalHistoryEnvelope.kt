package com.edenpractical.comms.privacy

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Simple authenticated local ciphertext envelope; key provisioning is ALWAYS
 * handled separately by the DEBUG Android Keystore owner. No fallback key,
 * password, plaintext alternative or authorization semantics.
 *
 * Tests exercise the real JCA cipher with ephemeral JVM test-only keys.
 * Those tests are NOT proof of Android Keystore/OEM or secure file deletion.
 */
internal object LocalHistoryEnvelope {
    private const val MAGIC = 0x45434846 // ECHF
    private const val VERSION = 1
    private const val IV_LENGTH = 12
    private const val TAG_BITS = 128
    const val MAX_ENVELOPE_SIZE = 256_000
    const val MAX_PLAINTEXT_SIZE = 240_000

    fun seal(key: SecretKey, plain: ByteArray, aad: ByteArray): ByteArray {
        require(plain.isNotEmpty() && plain.size <= MAX_PLAINTEXT_SIZE)
        require(aad.isNotEmpty() && aad.size <= 128)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(aad)
        val sealed = cipher.doFinal(plain)
        require(cipher.iv.size == IV_LENGTH)
        return ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { out ->
                out.writeInt(MAGIC)
                out.writeByte(VERSION)
                out.writeByte(IV_LENGTH)
                out.write(cipher.iv)
                out.writeInt(sealed.size)
                out.write(sealed)
            }
            bytes.toByteArray().also {
                require(it.size <= MAX_ENVELOPE_SIZE)
            }
        }
    }

    fun open(key: SecretKey, encoded: ByteArray, aad: ByteArray): ByteArray {
        require(encoded.size in 34..MAX_ENVELOPE_SIZE)
        require(aad.isNotEmpty() && aad.size <= 128)
        return DataInputStream(ByteArrayInputStream(encoded)).use { input ->
            require(input.readInt() == MAGIC && input.readUnsignedByte() == VERSION)
            require(input.readUnsignedByte() == IV_LENGTH)
            val iv = ByteArray(IV_LENGTH).also(input::readFully)
            val length = input.readInt()
            require(length in 16..MAX_ENVELOPE_SIZE && length == input.available())
            val data = ByteArray(length).also(input::readFully)
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
                updateAAD(aad)
                doFinal(data).also {
                    require(it.isNotEmpty() && it.size <= MAX_PLAINTEXT_SIZE)
                }
            }
        }
    }
}
