package com.edenpractical.comms.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeModeLayoutTest {
    @Test fun compactPhonesStack() {
        assertTrue(HomeModeLayout.shouldStack(280f, 1.0f))
        assertTrue(HomeModeLayout.shouldStack(359.9f, 1.0f))
    }

    @Test fun androidLargeFontStacksAtAnyWidth() {
        assertTrue(HomeModeLayout.shouldStack(430f, 1.3f))
        assertTrue(HomeModeLayout.shouldStack(900f, 2.0f))
    }

    @Test fun wideNormalFontKeepsTwoColumns() {
        assertFalse(HomeModeLayout.shouldStack(360f, 1.0f))
        assertFalse(HomeModeLayout.shouldStack(440f, 1.2f))
    }
}
