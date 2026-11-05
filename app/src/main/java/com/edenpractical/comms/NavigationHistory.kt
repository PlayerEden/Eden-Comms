package com.edenpractical.comms

/**
 * Small screen history used by both Android system Back and the in-app arrow.
 *
 * Stores only screen route names; never peer identities or conversation data.
 * The root is Home. Android's default Back behavior exits only from Home.
 */
internal object NavigationHistory {
    fun push(history: List<String>, destination: String): List<String> {
        require(destination.isNotBlank()) { "Destination required" }
        val current = history.lastOrNull() ?: "home"
        return if (current == destination) {
            if (history.isEmpty()) listOf("home") else history
        } else {
            (if (history.isEmpty()) listOf("home") else history) + destination
        }
    }

    fun pop(history: List<String>): List<String> =
        if (history.size > 1) history.dropLast(1) else listOf("home")
}
