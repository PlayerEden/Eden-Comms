package com.edenpractical.comms.wifidirect

import com.edenpractical.comms.security.SessionProofBinding
import com.edenpractical.comms.security.SessionProofChallenges
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.io.PipedInputStream
import java.io.PipedOutputStream
import org.junit.Assert.*
import org.junit.Test

/** Cryptographic correlation and strict encrypted-frame parser contracts.
 * Plain test streams here do NOT establish TLS or authorize messages.
 */
class DebugWifiDirectFutureQrCommitmentTest {
    private fun binding(
        hostNonce: Byte = 3,
        joinNonce: Byte = 4
    ) = SessionProofBinding(
        ByteArray(32) { 1 },
        ByteArray(32) { 2 },
        ByteArray(16) { hostNonce },
        ByteArray(16) { joinNonce }
    )
    private fun challenges(
        host: Byte = 5,
        join: Byte = 6
    ) = SessionProofChallenges(
        ByteArray(16) { host }, ByteArray(16) { join }
    )
    private fun fullQr(value: Byte = 7) = ByteArray(32) { value }

    @Test fun completeQrAndBothFreshChallengesChangeTheDigest() {
        val original = DebugWifiDirectFutureQrCommitment.derive(
            binding(), challenges(), fullQr()
        )
        assertEquals(32, original.size)
        assertArrayEquals(original,
            DebugWifiDirectFutureQrCommitment.derive(binding(), challenges(), fullQr()))
        assertFalse(original.contentEquals(DebugWifiDirectFutureQrCommitment.derive(
            binding(), challenges(host = 8), fullQr())))
        assertFalse(original.contentEquals(DebugWifiDirectFutureQrCommitment.derive(
            binding(), challenges(join = 9), fullQr())))
        assertFalse(original.contentEquals(DebugWifiDirectFutureQrCommitment.derive(
            binding(hostNonce = 10), challenges(), fullQr())))
        assertFalse(original.contentEquals(DebugWifiDirectFutureQrCommitment.derive(
            binding(), challenges(), fullQr(value = 11))))
        original.fill(0)
    }

    @Test fun sameDigestTwoRoleOrderedFramesSucceedAcrossBothPipes() {
        val digest = DebugWifiDirectFutureQrCommitment.derive(
            binding(), challenges(), fullQr()
        )
        val towardJoin = PipedInputStream(1024)
        val fromHost = PipedOutputStream(towardJoin)
        val towardHost = PipedInputStream(1024)
        val fromJoin = PipedOutputStream(towardHost)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val host = pool.submit {
                DebugWifiDirectFutureQrCommitment.exchange(
                    towardHost, fromHost, true, digest
                )
            }
            val join = pool.submit {
                DebugWifiDirectFutureQrCommitment.exchange(
                    towardJoin, fromJoin, false, digest
                )
            }
            host.get(3, TimeUnit.SECONDS)
            join.get(3, TimeUnit.SECONDS)
        } finally {
            fromHost.close()
            fromJoin.close()
            towardHost.close()
            towardJoin.close()
            pool.shutdownNow()
            digest.fill(0)
        }
    }

    @Test fun missingFrameWrongRoleAndTamperedDigestFailClosed() {
        val digest = DebugWifiDirectFutureQrCommitment.derive(
            binding(), challenges(), fullQr()
        )
        try {
            // The host is expecting a JOIN record; a HOST echo is invalid.
            val wrongRole = byteArrayOf(0x45, 0x43, 0x57, 0x43, 1, 1) + digest
            assertEquals(DebugWifiDirectFutureQrCommitment.BYTES, wrongRole.size)
            assertThrows(IOException::class.java) {
                DebugWifiDirectFutureQrCommitment.exchange(
                    ByteArrayInputStream(wrongRole), ByteArrayOutputStream(), true, digest
                )
            }
            val altered = digest.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
            val wrongDigest = byteArrayOf(0x45, 0x43, 0x57, 0x43, 1, 2) + altered
            assertThrows(IOException::class.java) {
                DebugWifiDirectFutureQrCommitment.exchange(
                    ByteArrayInputStream(wrongDigest), ByteArrayOutputStream(), true, digest
                )
            }
            assertThrows(IOException::class.java) {
                DebugWifiDirectFutureQrCommitment.exchange(
                    ByteArrayInputStream(byteArrayOf(0x45, 0x43)), ByteArrayOutputStream(),
                    true, digest
                )
            }
            altered.fill(0)
            wrongRole.fill(0)
            wrongDigest.fill(0)
        } finally {
            digest.fill(0)
        }
    }

    @Test fun invalidDigestAndSameChallengesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            DebugWifiDirectFutureQrCommitment.derive(binding(), challenges(), ByteArray(32))
        }
        assertThrows(IllegalArgumentException::class.java) {
            DebugWifiDirectFutureQrCommitment.derive(
                binding(), challenges(host = 5, join = 5), fullQr()
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            DebugWifiDirectFutureQrCommitment.exchange(
                ByteArrayInputStream(byteArrayOf()), ByteArrayOutputStream(),
                true, ByteArray(32)
            )
        }
    }
}
