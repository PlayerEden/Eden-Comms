package com.edenpractical.comms.security

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** These JVM fixtures do NOT prove actual Android exporter or TLS behavior. */
class DebugExporterBindingFrameTest {
    private val codec = DebugExporterBindingFrame
    private val shared = ByteArray(32) { (it + 1).toByte() }

    @Test fun fixedWireFixturesHaveNoExtraMetadataOrUnboundedLength() {
        val host = codec.encode(true, shared)
        val join = codec.encode(false, shared)
        assertEquals(38, codec.RECORD_BYTES)
        assertEquals(codec.RECORD_BYTES, host.size)
        assertEquals(codec.RECORD_BYTES, join.size)
        assertArrayEquals(byteArrayOf(0x45, 0x43, 0x43, 0x42, 1, 1),
            host.copyOfRange(0, 6))
        assertArrayEquals(byteArrayOf(0x45, 0x43, 0x43, 0x42, 1, 2),
            join.copyOfRange(0, 6))
        assertArrayEquals(shared, host.copyOfRange(6, 38))
        assertArrayEquals(shared, join.copyOfRange(6, 38))
        assertFalse(host.contentEquals(join))
    }

    @Test fun presenterAndJoinerExchangeRecordsInFixedRoleOrder() {
        val presenterWrites = ByteArrayOutputStream()
        val joinerWrites = ByteArrayOutputStream()
        codec.exchange(
            ByteArrayInputStream(codec.encode(false, shared)),
            presenterWrites, true, shared
        )
        codec.exchange(
            ByteArrayInputStream(codec.encode(true, shared)),
            joinerWrites, false, shared
        )
        assertArrayEquals(codec.encode(true, shared), presenterWrites.toByteArray())
        assertArrayEquals(codec.encode(false, shared), joinerWrites.toByteArray())
    }

    @Test fun wrongExporterFailsInsteadOfAcceptingDifferentTlsChannel() {
        val different = ByteArray(32) { (it + 2).toByte() }
        assertThrows(DebugExporterMismatchException::class.java) {
            codec.readAndVerify(ByteArrayInputStream(codec.encode(false, different)),
                false, shared)
        }
        assertThrows(DebugExporterMismatchException::class.java) {
            codec.exchange(ByteArrayInputStream(codec.encode(true, different)),
                ByteArrayOutputStream(), false, shared)
        }
    }

    @Test fun wrongRolesVersionsMagicAndShortRecordsFailClosed() {
        val valid = codec.encode(false, shared)
        for (index in 0..5) {
            val altered = valid.copyOf()
            altered[index] = (altered[index].toInt() xor 0x01).toByte()
            assertThrows(DebugExporterMismatchException::class.java) {
                codec.readAndVerify(ByteArrayInputStream(altered), false, shared)
            }
        }
        assertThrows(DebugExporterMismatchException::class.java) {
            codec.readAndVerify(ByteArrayInputStream(valid), true, shared)
        }
        for (n in listOf(0, 1, 6, 36, 37)) {
            assertThrows(EOFException::class.java) {
                codec.readAndVerify(ByteArrayInputStream(valid.copyOf(n)),
                    false, shared)
            }
        }
    }

    @Test fun zeroOrMalformedInputIsNeverAcceptedAsEvidence() {
        for (bad in listOf(ByteArray(0), ByteArray(1), ByteArray(31),
            ByteArray(32), ByteArray(33))) {
            assertThrows(DebugExporterMismatchException::class.java) {
                codec.encode(true, bad)
            }
            assertThrows(DebugExporterMismatchException::class.java) {
                codec.readAndVerify(ByteArrayInputStream(codec.encode(false, shared)),
                    false, bad)
            }
        }
        assertThrows(DebugExporterMismatchException::class.java) {
            codec.encode(false, ByteArray(32))
        }
    }

    @Test fun validFrameDoesNotGrantApplicationAuthorization() {
        codec.readAndVerify(
            ByteArrayInputStream(codec.encode(false, shared)), false, shared
        )
        assertFalse(com.edenpractical.comms.model.ReleaseCapabilities.CAN_CONNECT)
        assertFalse(com.edenpractical.comms.model.ReleaseCapabilities.CAN_SEND_MESSAGES)
        assertFalse(com.edenpractical.comms.model.ReleaseCapabilities.CAN_TRANSMIT_VOICE)
        assertTrue(codec.RECORD_BYTES < 64)
    }
}
