package com.edenpractical.comms.pairing

/**
 * Foreground-only UI convenience for Eden Comms' real LAN debug flow.
 *
 * Android View.keepScreenOn requests that the display not dim while THIS
 * particular composition is visible. It does not hold background power, does not run
 * in the background, open a transport, bypass QR or approvals, or grant
 * any messaging capability. Screen power button and OS policy still win.
 */
internal object PairingScreenAwakePolicy {
    fun shouldKeepOn(stage: String): Boolean = when (stage) {
        // A QR must remain visible long enough for the other phone to scan.
        "showHost", "scanHost", "scanReply", "showReply",
        // Avoid screen timeout while an OS socket/peer approval is in progress.
        "connecting", "approval",
        // The foreground-only live test requires the current screen to remain up.
        "demo" -> true
        else -> false // checking, choose, result, error or unknown never hold.
    }
}
