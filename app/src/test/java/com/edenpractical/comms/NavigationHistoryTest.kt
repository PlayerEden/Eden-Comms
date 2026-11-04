package com.edenpractical.comms

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationHistoryTest {
    @Test fun systemBackUnwindsSettingsDeveloperLanInOrder() {
        var stack = listOf("home")
        stack = NavigationHistory.push(stack, "settings")
        stack = NavigationHistory.push(stack, "developer")
        stack = NavigationHistory.push(stack, "lan-test")
        assertEquals("developer", NavigationHistory.pop(stack).last())
        stack = NavigationHistory.pop(stack)
        assertEquals("settings", NavigationHistory.pop(stack).last())
        stack = NavigationHistory.pop(stack)
        assertEquals(listOf("home"), NavigationHistory.pop(stack))
    }

    @Test fun identityBackReturnsToActualPreviousScreen() {
        val fromSettings = NavigationHistory.push(listOf("home", "settings"), "identity")
        assertEquals("settings", NavigationHistory.pop(fromSettings).last())

        val fromDeveloper = NavigationHistory.push(
            listOf("home", "settings", "developer"), "identity"
        )
        assertEquals("developer", NavigationHistory.pop(fromDeveloper).last())
    }

    @Test fun duplicateRouteDoesNotPushAnotherScreen() {
        val stack = listOf("home", "settings")
        assertEquals(stack, NavigationHistory.push(stack, "settings"))
    }

    @Test fun backAtHomeNeverPopsPastRoot() {
        assertEquals(listOf("home"), NavigationHistory.pop(listOf("home")))
        assertEquals(listOf("home"), NavigationHistory.pop(emptyList()))
    }

    @Test fun screensPreserveOrderForSaveableHistory() {
        val stack = NavigationHistory.push(
            NavigationHistory.push(listOf("home"), "connect"), "settings"
        )
        assertEquals(listOf("home", "connect", "settings"), stack)
        assertEquals(listOf("home", "connect"), NavigationHistory.pop(stack))
    }
}
