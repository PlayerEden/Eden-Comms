package com.edenpractical.comms.bluetooth

/**
 * Read-only novice guidance from ACTUAL debug UI states. This cannot trust a
 * Bluetooth discovery result, identify a peer, approve a TLS session or send.
 * The real QR, TLS and bilateral approval controllers remain authoritative.
 */
internal object DebugBluetoothJourneyCue {
    data class Step(val phase: String, val instruction: String, val next: String)

    fun current(
        qr: DebugBluetoothQrCoordinator.Stage,
        link: DebugRfcommAttemptGate.Phase,
        selectedSignal: Boolean,
        scanning: Boolean,
        approvalRequested: Boolean
    ): Step {
        if (link == DebugRfcommAttemptGate.Phase.LINK_OPEN) {
            return if (approvalRequested) Step(
                "STEP 3 • BOTH PEOPLE APPROVE",
                "Confirm who is holding the other phone",
                "Both people must choose Approve on their own phones. This remains a test, not chat."
            ) else Step(
                "STEP 3 • VERIFY THE LINK",
                "Checking encrypted Bluetooth test controls",
                "Keep this screen open. Do not send messages; this diagnostic closes after the checks."
            )
        }
        if (link == DebugRfcommAttemptGate.Phase.CONNECTING) return Step(
            "STEP 3 • VERIFY THE LINK",
            "Connecting to the selected Bluetooth radio",
            "Wait for QR-pinned TLS checks and a separate approval on BOTH phones."
        )
        // A previous stopped link is NOT the state of a new fresh QR attempt.
        // Once the person explicitly starts new codes, show Step 1/2 again.
        if (link == DebugRfcommAttemptGate.Phase.STOPPED &&
            qr == DebugBluetoothQrCoordinator.Stage.CHOOSE
        ) return Step(
            "TEST ENDED",
            "Start a new in-person QR exchange",
            "The test socket is closed. A new attempt needs fresh codes and both approvals."
        )
        return when (qr) {
            DebugBluetoothQrCoordinator.Stage.CHOOSE -> Step(
                "STEP 1 • EXCHANGE CODES",
                "One person shows a code; the other scans it",
                "On the first phone choose Show. On the other choose Scan. No account or internet needed."
            )
            DebugBluetoothQrCoordinator.Stage.HOST_SHOW -> Step(
                "STEP 1 • EXCHANGE CODES",
                "Show your code to the other person",
                "When they scan, tap Scan their reply code. Keep both phones nearby."
            )
            DebugBluetoothQrCoordinator.Stage.JOIN_SCAN_HOST -> Step(
                "STEP 1 • EXCHANGE CODES",
                "Scan the code shown on the first phone",
                "You'll show a reply code for the first phone to scan."
            )
            DebugBluetoothQrCoordinator.Stage.HOST_SCAN_REPLY -> Step(
                "STEP 1 • EXCHANGE CODES",
                "Scan the other phone's reply code",
                "This prepares an expected certificate, not a trusted Bluetooth radio."
            )
            DebugBluetoothQrCoordinator.Stage.JOIN_SHOW_REPLY -> Step(
                "STEP 1 • EXCHANGE CODES",
                "Show the reply code to the first phone",
                "After they scan, tap I showed the reply to continue. This is not peer verification."
            )
            DebugBluetoothQrCoordinator.Stage.HOST_PREPARED -> Step(
                "STEP 2 • START BLUETOOTH",
                "Host the one-time test connection",
                "Start the host link below. The other person must find and select your phone's radio signal."
            )
            DebugBluetoothQrCoordinator.Stage.JOIN_PREPARED -> when {
                scanning -> Step(
                    "STEP 2 • FIND THE FIRST PHONE",
                    "Looking for nearby Bluetooth signals",
                    "Choose only the signal you intend to try. Discovery does not establish identity."
                )
                selectedSignal -> Step(
                    "STEP 2 • FIND THE FIRST PHONE",
                    "Join the signal you selected",
                    "The QR-pinned TLS check must still prove that radio belongs to the other phone."
                )
                else -> Step(
                    "STEP 2 • FIND THE FIRST PHONE",
                    "Search for the other phone's Bluetooth signal",
                    "Ask them to start Host. Then search and stage its signal here before Join."
                )
            }
        }
    }
}
