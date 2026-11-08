package com.edenpractical.comms.pairing

/**
 * Presentation-only shortcut for repeating the user's last QR role.
 *
 * This remembers ONLY the current screen's non-sensitive presenter/joiner
 * choice. It does not retain a QR, prior peer pin, identity proof, consent,
 * socket or session. The screen MUST call reset() before startRole(...), so
 * both phones perform a fresh reciprocal QR scan and BOTH approve again.
 */
internal object PairingFreshRetry {
    data class Choice(val role: String, val buttonLabel: String)

    fun forLastRole(lastRole: LocalQaReport.Role): Choice? = when (lastRole) {
        LocalQaReport.Role.PRESENTER -> Choice(
            role = "show",
            buttonLabel = "Try again • Show a new code"
        )
        LocalQaReport.Role.JOINER -> Choice(
            role = "scan",
            buttonLabel = "Try again • Scan a new code"
        )
        LocalQaReport.Role.NOT_SELECTED -> null
    }
}
