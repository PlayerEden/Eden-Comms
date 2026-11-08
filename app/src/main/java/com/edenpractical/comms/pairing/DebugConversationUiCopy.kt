package com.edenpractical.comms.pairing

/**
 * UI wording for the ephemeral debug text view.
 * These labels describe local events only. A local TLS write must NEVER
 * become a remote delivery/read claim or production messaging authority.
 */
internal object DebugConversationUiCopy {
    const val peerAppAckLabel = "Other app returned test ACK • not read"
    fun participant(direction: DebugTextTranscript.Direction): String =
        when (direction) {
            DebugTextTranscript.Direction.WRITTEN_TO_TLS -> "You"
            DebugTextTranscript.Direction.RECEIVED_HERE -> "Other phone"
        }

    fun observation(direction: DebugTextTranscript.Direction): String =
        when (direction) {
            DebugTextTranscript.Direction.WRITTEN_TO_TLS -> "Written to test link"
            DebugTextTranscript.Direction.RECEIVED_HERE -> "Received on this phone"
        }

    fun counts(writtenHere: Int, receivedHere: Int): String {
        require(writtenHere >= 0 && receivedHere >= 0)
        return "This phone: $writtenHere written • $receivedHere received"
    }
}
