package com.edenpractical.comms.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugTextDraftPolicyTest {
    @Test fun acceptsRealUnicodeCodePointsRatherThanTruncatingEmojiByUtf16Units() {
        val fox = "🦊"
        assertEquals(1, DebugTextDraftPolicy.codePoints(fox))
        assertTrue(DebugTextDraftPolicy.accepts(fox.repeat(280)))
        assertTrue(DebugTextDraftPolicy.maySend(fox.repeat(280)))
        assertFalse(DebugTextDraftPolicy.accepts(fox.repeat(281)))
        assertTrue(DebugTextDraftPolicy.maySend("Hej världen • 你好 • مرحبا"))
        assertTrue(DebugTextDraftPolicy.maySend("First line\nSecond line"))
    }

    @Test fun emptyDraftCanBeEditedButNotSent() {
        assertTrue(DebugTextDraftPolicy.accepts(""))
        assertTrue(DebugTextDraftPolicy.accepts("  "))
        assertFalse(DebugTextDraftPolicy.maySend(""))
        assertFalse(DebugTextDraftPolicy.maySend(" \t\n "))
        assertTrue(DebugTextDraftPolicy.maySend("Test hello"))
    }

    @Test fun rejectsControlCharactersAndMalformedSurrogates() {
        for (bad in listOf("hi\u0000", "hi\u0001", "hi\u001b",
            "hi\u007f", "hi\u0085", "\uD800", "\uDC00", "hi\uD800x")) {
            assertFalse(DebugTextDraftPolicy.accepts(bad))
            assertFalse(DebugTextDraftPolicy.maySend(bad))
        }
        assertTrue(DebugTextDraftPolicy.maySend("Tab\there"))
    }

    @Test fun codePointBoundaryMatchesMessageLimit() {
        val mixed = "x".repeat(279) + "🦊"
        assertEquals(280, DebugTextDraftPolicy.codePoints(mixed))
        assertTrue(DebugTextDraftPolicy.maySend(mixed))
        assertFalse(DebugTextDraftPolicy.accepts(mixed + "z"))
    }

    @Test fun ordinaryDraftValidationDoesNotChangeReleaseGates() {
        assertFalse(com.edenpractical.comms.model.ReleaseCapabilities.CAN_CONNECT)
        assertFalse(com.edenpractical.comms.model.ReleaseCapabilities.CAN_SEND_MESSAGES)
        assertFalse(com.edenpractical.comms.model.ReleaseCapabilities.CAN_TRANSMIT_VOICE)
    }
}
