package com.edenpractical.comms.bluetooth

/**
 * F2-A: a verified debug TLS consent result is not enough to report success
 * if Android foreground or Bluetooth CONNECT permission changed before
 * the controller's worker completed. This is an exit/status check ONLY;
 * it grants no Bluetooth messages, voice or production authorization.
 */
internal object DebugRfcommCompletionPolicy {
    fun endAfterConsent(
        foreground: Boolean,
        hasConnectPermission: Boolean
    ): DebugRfcommAttemptGate.End = when {
        !foreground -> DebugRfcommAttemptGate.End.BACKGROUND
        !hasConnectPermission -> DebugRfcommAttemptGate.End.PERMISSION_LOST
        else -> DebugRfcommAttemptGate.End.CONSENT_CONFIRMED
    }

    fun mayChooseConsent(
        phase: DebugRfcommAttemptGate.Phase,
        foreground: Boolean,
        hasConnectPermission: Boolean
    ): Boolean =
        phase == DebugRfcommAttemptGate.Phase.LINK_OPEN &&
            foreground && hasConnectPermission
}
