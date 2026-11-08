package com.edenpractical.comms.bluetooth

import javax.net.ssl.SSLException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DebugTlsRecordBoundaryTest {
    private fun record(type: Int = 23, major: Int = 3, minor: Int = 3,
                       size: Int = 39): ByteArray {
        val header = byteArrayOf(
            type.toByte(), major.toByte(), minor.toByte(),
            (size ushr 8).toByte(), size.toByte()
        )
        return header + ByteArray(size) { 0x43 }
    }

    @Test fun singleStrictTls13CiphertextRecordPasses() {
        assertEquals(16_640, DebugTlsRecordBoundary.MAX_CIPHERTEXT_BODY)
        for (size in listOf(1, 17, 38, 39, 134, 16_640)) {
            DebugTlsRecordBoundary.requireSingleCiphertext(record(size = size))
        }
    }

    @Test fun cleartextHandshakeAlertsWrongVersionsAndTypesAreRejected() {
        for (candidate in listOf(
            record(type = 20),
            record(type = 21),
            record(type = 22),
            record(type = 24),
            record(type = 0),
            record(major = 2),
            record(major = 4),
            record(minor = 1),
            record(minor = 2),
            record(minor = 4),
            byteArrayOf()
        )) {
            assertThrows(SSLException::class.java) {
                DebugTlsRecordBoundary.requireSingleCiphertext(candidate)
            }
        }
    }

    @Test fun zeroOversizeTruncationAndMultipleRecordsFailClosed() {
        for (candidate in listOf(
            byteArrayOf(23, 3, 3, 0, 0),
            record(size = 16_641),
            record(size = 38).copyOf(5 + 37),
            record(size = 38) + byteArrayOf(0),
            record(size = 17) + record(size = 17),
            byteArrayOf(23, 3, 3),
            byteArrayOf(23, 3, 3, 0),
            byteArrayOf(23, 3, 3, 0, 1)
        )) {
            assertThrows(SSLException::class.java) {
                DebugTlsRecordBoundary.requireSingleCiphertext(candidate)
            }
        }
    }

    @Test fun inputBytesRemainOwnedByCaller() {
        val ciphertext = record(size = 39)
        val before = ciphertext.copyOf()
        DebugTlsRecordBoundary.requireSingleCiphertext(ciphertext)
        org.junit.Assert.assertArrayEquals(before, ciphertext)
    }
}
