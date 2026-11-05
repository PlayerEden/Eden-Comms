package com.edenpractical.comms.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalChatPreviewTest {
    @Test fun freshSessionHasNoPeerMessagesOrTransmissionState() {
        val fresh = LocalChatPreview.newSession()
        assertTrue(fresh.entries.isEmpty())
        assertTrue(fresh.canEnterText)
        assertFalse(fresh.ended)
        assertEquals(1L, fresh.nextId)
        assertFalse(com.edenpractical.comms.model.ReleaseCapabilities.CAN_SEND_MESSAGES)
        assertFalse(com.edenpractical.comms.model.ReleaseCapabilities.CAN_CONNECT)
        assertFalse(com.edenpractical.comms.model.ReleaseCapabilities.CAN_TRANSMIT_VOICE)
    }

    @Test fun typedBubblesAreOnlyLocalAndNeverSentOrDelivered() {
        val first = LocalChatPreview.newSession().addLocallyTyped("  Hello, world!  ", "10:35 AM")
        val entry = first.entries.single()
        assertEquals("Hello, world!", entry.text)
        assertEquals("10:35 AM", entry.timeLabel)
        assertEquals(LocalChatPreview.Origin.TYPED_HERE, entry.origin)
        assertEquals(LocalChatPreview.DisplayState.LOCAL_ONLY, entry.displayState)
        assertEquals("Local preview • not sent", entry.displayState.label)
    }

    @Test fun emptyAndOversizedDraftNeverEntersUiHistory() {
        val fresh = LocalChatPreview.newSession()
        assertSame(fresh, fresh.addLocallyTyped("  \n  ", "NOW"))
        assertSame(fresh, fresh.addLocallyTyped("a".repeat(281), "NOW"))
        assertEquals(LocalChatPreview.MAX_CHARACTERS, "a".repeat(280).length)
        assertEquals(1, fresh.addLocallyTyped("a".repeat(280), "NOW").entries.size)
        assertTrue(fresh.entries.isEmpty())
    }

    @Test fun onlyExplicitOptInAddsClearlyFakePeerExamples() {
        val fresh = LocalChatPreview.newSession()
        assertTrue(fresh.entries.isEmpty())
        val shown = fresh.addExampleLayout()
        assertEquals(2, shown.entries.size)
        assertTrue(shown.entries.all {
            it.origin == LocalChatPreview.Origin.EXAMPLE_NOT_RECEIVED &&
                it.displayState == LocalChatPreview.DisplayState.FAKE_SAMPLE &&
                it.displayState.label.contains("not received")
        })
        assertSame(shown, shown.addExampleLayout())
        assertEquals(2, shown.entries.size)
        assertTrue(fresh.entries.isEmpty())
    }

    @Test fun cappedVisibleHistoryIsOrderedAndImmutable() {
        var state = LocalChatPreview.newSession()
        repeat(25) { index ->
            state = state.addLocallyTyped("local $index", "11:00")
        }
        assertEquals(12, state.entries.size)
        assertEquals((13..24).map { "local $it" }, state.entries.map { it.text })
        assertEquals((14L..25L).toList(), state.entries.map { it.id })
        assertEquals(26L, state.nextId)
    }

    @Test fun clearRemovesVisiblePayloadWithoutEndingSession() {
        val prior = LocalChatPreview.newSession().addLocallyTyped("sensitive draft", "NOW")
        val cleared = prior.clear()
        assertTrue(cleared.entries.isEmpty())
        assertTrue(cleared.canEnterText)
        assertEquals(2L, cleared.nextId)
        assertEquals("sensitive draft", prior.entries.single().text)
        assertSame(cleared, cleared.clear())
        val newer = cleared.addLocallyTyped("next", "NOW")
        assertEquals(2L, newer.entries.single().id)
    }

    @Test fun endedSessionCannotResurrectOnLateUiActions() {
        val before = LocalChatPreview.newSession()
            .addLocallyTyped("erase on end", "NOW").addExampleLayout()
        val finished = before.end()
        assertTrue(finished.ended)
        assertFalse(finished.canEnterText)
        assertTrue(finished.entries.isEmpty())
        assertSame(finished, finished.end())
        assertSame(finished, finished.addLocallyTyped("late", "NOW"))
        assertSame(finished, finished.addExampleLayout())
        assertSame(finished, finished.clear())
        val next = LocalChatPreview.newSession()
        assertEquals(1L, next.nextId)
        assertTrue(next.entries.isEmpty())
        assertFalse(next.ended)
    }

    @Test fun bogusTimestampNeverPersistsUntrustedMetadata() {
        val state = LocalChatPreview.newSession()
            .addLocallyTyped("hi", "192.168.0.1:SECRET-QR")
        assertEquals("NOW", state.entries.single().timeLabel)
    }

    @Test fun typedContentIsNotPreloadedInFreshState() {
        val original = LocalChatPreview.newSession()
            .addLocallyTyped("hidden local preview", "09:00").end()
        val later = LocalChatPreview.newSession()
        assertEquals(1L, later.nextId)
        assertTrue(later.entries.isEmpty())
        assertTrue(original.entries.isEmpty())
    }
}
