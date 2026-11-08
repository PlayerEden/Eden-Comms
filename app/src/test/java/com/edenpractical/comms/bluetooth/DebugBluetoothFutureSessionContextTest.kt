package com.edenpractical.comms.bluetooth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Future-only role/pin/commitment correlation checks, NOT cryptographic
 * proof of a real Bluetooth session or authorization to message.
 */
class DebugBluetoothFutureSessionContextTest {
    private fun context(
        hostRole: Boolean = true,
        peer: Byte = 0x31,
        transcript: Byte = 0x42
    ) = DebugBluetoothFutureSessionContext(
        hostRole, ByteArray(32) { peer }, ByteArray(32) { transcript }
    )

    @Test fun exactRolePeerPinAndTranscriptMustMatch() {
        val original = context()
        val same = context()
        val otherRole = context(hostRole = false)
        val otherPeer = context(peer = 0x32)
        val otherTranscript = context(transcript = 0x43)
        try {
            assertTrue(original.sameAs(same))
            assertTrue(same.sameAs(original))
            assertFalse(original.sameAs(otherRole))
            assertFalse(original.sameAs(otherPeer))
            assertFalse(original.sameAs(otherTranscript))
        } finally {
            for (candidate in listOf(
                original, same, otherRole, otherPeer, otherTranscript
            )) candidate.close()
        }
    }

    @Test fun copiesCannotBeChangedByMutatingCallerArraysOrClosingCaller() {
        val pin = ByteArray(32) { 0x31 }
        val transcript = ByteArray(32) { 0x42 }
        val supplied = DebugBluetoothFutureSessionContext(true, pin, transcript)
        val captured = supplied.copyForReview()
        try {
            pin.fill(0)
            transcript.fill(0)
            supplied.close()
            val matching = context()
            try {
                assertTrue(captured.sameAs(matching))
                assertFalse(supplied.sameAs(matching))
            } finally {
                matching.close()
            }
        } finally {
            captured.close()
            supplied.close()
        }
    }

    @Test fun closedContextsCannotBeComparedOrCopied() {
        val a = context()
        val b = context()
        assertTrue(a.sameAs(b))
        a.close()
        a.close()
        assertFalse(a.sameAs(b))
        assertFalse(b.sameAs(a))
        assertThrows(IllegalStateException::class.java) {
            a.copyForReview()
        }
        b.close()
        assertFalse(b.sameAs(b))
    }

    @Test fun zeroPinsAndBadWidthsAreRefused() {
        val valid = ByteArray(32) { 7 }
        val zero = ByteArray(32)
        for (invalid in listOf(zero, ByteArray(31), ByteArray(33))) {
            assertThrows(IllegalArgumentException::class.java) {
                DebugBluetoothFutureSessionContext(true, invalid, valid)
            }
            assertThrows(IllegalArgumentException::class.java) {
                DebugBluetoothFutureSessionContext(true, valid, invalid)
            }
        }
        valid.fill(0)
    }
}
