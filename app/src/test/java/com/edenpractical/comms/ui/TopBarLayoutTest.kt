package com.edenpractical.comms.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TopBarLayoutTest {
    @Test fun compactWidthMayWrap() {
        assertEquals(2, TopBarLayout.maxTitleLines(280f, 1.0f))
        assertEquals(2, TopBarLayout.maxTitleLines(359.9f, 1.0f))
    }

    @Test fun largeFontsMayWrapAtAnyWidth() {
        assertEquals(2, TopBarLayout.maxTitleLines(440f, 1.3f))
        assertEquals(2, TopBarLayout.maxTitleLines(980f, 2.0f))
    }

    @Test fun wideNormalFontRemainsSingleLine() {
        assertEquals(1, TopBarLayout.maxTitleLines(360f, 1.0f))
        assertEquals(1, TopBarLayout.maxTitleLines(440f, 1.2f))
    }
}
