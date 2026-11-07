package com.edenpractical.comms.bluetooth

import com.edenpractical.comms.security.DebugApprovalCommitment
import com.edenpractical.comms.security.SessionProofBinding
import com.edenpractical.comms.security.SessionProofChallenges
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugBluetoothCommitmentFrameTest {
    private val hostPin = ByteArray(32) { 0x11 }
    private val joinPin = ByteArray(32) { 0x22 }
    private val hostNonce = ByteArray(16) { 0x33 }
    private val joinNonce = ByteArray(16) { 0x44 }
    private val hostChallenge = ByteArray(16) { 0x55 }
    private val joinChallenge = ByteArray(16) { 0x66 }
    private val exporter = ByteArray(32) { 0x77 }

    private fun derive(
        binding: SessionProofBinding = SessionProofBinding(
            hostPin, joinPin, hostNonce, joinNonce
        ),
        challenges: SessionProofChallenges = SessionProofChallenges(
            hostChallenge, joinChallenge
        ),
        value: ByteArray = exporter
    ) = DebugApprovalCommitment.derive(binding, challenges, value)

    @Test fun hostAndJoinCommitmentsProduceCanonicalRolesAndVerifyPeer() {
        val commitment = derive()
        val fromHost = DebugBluetoothCommitmentFrame.encode(true, commitment)
        val fromJoin = DebugBluetoothCommitmentFrame.encode(false, commitment)
        assertEquals(DebugBluetoothCommitmentFrame.RECORD_BYTES, fromHost.size)
        assertEquals(DebugBluetoothCommitmentFrame.RECORD_BYTES, fromJoin.size)
        assertEquals(1, fromHost[5].toInt())
        assertEquals(2, fromJoin[5].toInt())
        DebugBluetoothCommitmentFrame.verify(
            ByteArrayInputStream(fromHost), expectedPeerIsHost = true, commitment
        )
        DebugBluetoothCommitmentFrame.verify(
            ByteArrayInputStream(fromJoin), expectedPeerIsHost = false, commitment
        )
        assertArrayEquals(fromHost.copyOfRange(6, fromHost.size), commitment)
    }

    @Test fun wrongRoleVersionMagicTruncatedOrModifiedCommitmentNeverMatches() {
        val commitment = derive()
        val good = DebugBluetoothCommitmentFrame.encode(true, commitment)
        val variants = listOf(
            good.clone().apply { this[0] = 0 },
            good.clone().apply { this[4] = 2 },
            good.clone().apply { this[5] = 2 },
            good.clone().apply { this[6] = (this[6].toInt() xor 1).toByte() },
            good.copyOf(good.size - 1)
        )
        variants.forEach { raw ->
            assertThrows(Exception::class.java) {
                DebugBluetoothCommitmentFrame.verify(
                    ByteArrayInputStream(raw), true, commitment
                )
            }
        }
        assertThrows(Exception::class.java) {
            DebugBluetoothCommitmentFrame.verify(
                ByteArrayInputStream(good), false, commitment
            )
        }
        assertThrows(Exception::class.java) {
            DebugBluetoothCommitmentFrame.encode(true, ByteArray(31))
        }
        assertThrows(Exception::class.java) {
            DebugBluetoothCommitmentFrame.encode(true, ByteArray(32))
        }
    }

    @Test fun everyQrChallengeAndTlsExporterFieldChangesTheCommitment() {
        val baseline = derive()
        val changes = listOf(
            derive(SessionProofBinding(ByteArray(32) { 0x18 }, joinPin, hostNonce, joinNonce)),
            derive(SessionProofBinding(hostPin, ByteArray(32) { 0x29 }, hostNonce, joinNonce)),
            derive(SessionProofBinding(hostPin, joinPin, ByteArray(16) { 0x3A }, joinNonce)),
            derive(SessionProofBinding(hostPin, joinPin, hostNonce, ByteArray(16) { 0x4B })),
            derive(challenges = SessionProofChallenges(ByteArray(16) { 0x5C }, joinChallenge)),
            derive(challenges = SessionProofChallenges(hostChallenge, ByteArray(16) { 0x6D })),
            derive(value = ByteArray(32) { 0x7E })
        )
        changes.forEach { different ->
            assertTrue(!different.contentEquals(baseline))
            val wrongFrame = DebugBluetoothCommitmentFrame.encode(false, different)
            assertThrows(DebugBluetoothCommitmentMismatch::class.java) {
                DebugBluetoothCommitmentFrame.verify(
                    ByteArrayInputStream(wrongFrame), false, baseline
                )
            }
        }
    }

    @Test fun exchangeUsesOnlyTwoFixedSizeRoleFrames() {
        val digest = derive()
        val remoteJoin = DebugBluetoothCommitmentFrame.encode(false, digest)
        val writes = ByteArrayOutputStream()
        DebugBluetoothCommitmentFrame.exchange(
            ByteArrayInputStream(remoteJoin), writes, true, digest
        )
        assertArrayEquals(
            DebugBluetoothCommitmentFrame.encode(true, digest),
            writes.toByteArray()
        )
    }
}
