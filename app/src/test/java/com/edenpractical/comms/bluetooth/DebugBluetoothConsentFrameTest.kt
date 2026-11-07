package com.edenpractical.comms.bluetooth

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugBluetoothConsentFrameTest {
    private val current = ByteArray(32) { (it + 1).toByte() }

    @Test fun allThreeTypesHaveStrictRoleBoundFixedSizeFrames() {
        for (kind in DebugBluetoothConsentFrame.Type.entries) {
            for (isHost in listOf(true, false)) {
                val frame = DebugBluetoothConsentFrame.encode(isHost, kind, current)
                assertEquals(DebugBluetoothConsentFrame.RECORD_BYTES, frame.size)
                assertEquals(39, frame.size)
                assertEquals(if (isHost) 1 else 2, frame[5].toInt())
                assertEquals(
                    kind,
                    DebugBluetoothConsentFrame.read(
                        ByteArrayInputStream(frame), isHost, current
                    )
                )
                val output = ByteArrayOutputStream()
                DebugBluetoothConsentFrame.write(output, isHost, kind, current)
                assertArrayEquals(frame, output.toByteArray())
            }
        }
    }

    @Test fun staleCommitmentIsRejectedForApproveDeclineAndConfirm() {
        val old = ByteArray(32) { (it + 30).toByte() }
        for (kind in DebugBluetoothConsentFrame.Type.entries) {
            val encoded = DebugBluetoothConsentFrame.encode(true, kind, old)
            assertThrows(DebugBluetoothConsentFrameException::class.java) {
                DebugBluetoothConsentFrame.read(
                    ByteArrayInputStream(encoded), true, current
                )
            }
        }
    }

    @Test fun swappedRoleWrongVersionTypeMagicAndShortRecordsFail() {
        val approved = DebugBluetoothConsentFrame.encode(true,
            DebugBluetoothConsentFrame.Type.APPROVE, current)
        val bad = listOf(
            approved.clone().also { it[0] = 0 },
            approved.clone().also { it[4] = 9 },
            approved.clone().also { it[5] = 9 },
            approved.clone().also { it[6] = 9 },
            approved.copyOf(approved.size - 1),
            approved.clone().also { it[7] = 0 }
        )
        for (bytes in bad) {
            assertThrows(Exception::class.java) {
                DebugBluetoothConsentFrame.read(
                    ByteArrayInputStream(bytes), true, current
                )
            }
        }
        assertThrows(DebugBluetoothConsentFrameException::class.java) {
            DebugBluetoothConsentFrame.read(ByteArrayInputStream(approved), false, current)
        }
    }

    @Test fun zeroOrWrongSizeCommitmentsNeverEncodeOrVerify() {
        for (bad in listOf(ByteArray(0), ByteArray(31), ByteArray(32), ByteArray(33))) {
            assertThrows(DebugBluetoothConsentFrameException::class.java) {
                DebugBluetoothConsentFrame.encode(true,
                    DebugBluetoothConsentFrame.Type.CONFIRM, bad)
            }
            assertThrows(DebugBluetoothConsentFrameException::class.java) {
                DebugBluetoothConsentFrame.read(ByteArrayInputStream(byteArrayOf()), false, bad)
            }
        }
    }

    @Test fun approveOnlyIsNotAConfirmation() {
        val approved = DebugBluetoothConsentFrame.encode(true,
            DebugBluetoothConsentFrame.Type.APPROVE, current)
        val confirmed = DebugBluetoothConsentFrame.encode(true,
            DebugBluetoothConsentFrame.Type.CONFIRM, current)
        assertTrue(!approved.contentEquals(confirmed))
        assertEquals(DebugBluetoothConsentFrame.Type.APPROVE,
            DebugBluetoothConsentFrame.read(ByteArrayInputStream(approved), true, current))
        assertEquals(DebugBluetoothConsentFrame.Type.CONFIRM,
            DebugBluetoothConsentFrame.read(ByteArrayInputStream(confirmed), true, current))
    }
}
