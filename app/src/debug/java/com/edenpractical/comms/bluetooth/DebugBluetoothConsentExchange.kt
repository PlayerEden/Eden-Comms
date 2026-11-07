package com.edenpractical.comms.bluetooth

import com.edenpractical.comms.transport.DebugApprovalGate
import java.io.IOException

/**
 * Fixed, ordered encrypted bilateral-consent diagnostic for ONE verified QR
 * and TLS-exporter session commitment. Runs on the RFCOMM worker, NOT the UI.
 *
 * Human APPROVE is required independently on BOTH devices. Once both
 * approvals arrive, each endpoint sends a separate explicit protocol CONFIRM
 * (not an additional button) and checks the peer CONFIRM on the same TLS link.
 * No chat/voice API is exposed even on CONFIRMED; the caller closes the socket.
 */
internal object DebugBluetoothConsentExchange {
    const val CONSENT_WAIT_MS = 25_000L

    fun run(
        transport: DebugTlsControlTransport,
        isHost: Boolean,
        commitment: ByteArray,
        decision: DebugBluetoothConsentDecision,
        onApprovalRequired: () -> Unit
    ) {
        val gate = DebugApprovalGate()
        try {
            // M3n-B2 must have already completed QR proof, remote exporter
            // comparison, and two transcript commitment echoes.
            transport.advance(
                DebugTlsControlTransport.Phase.COMMITMENT,
                DebugTlsControlTransport.Phase.APPROVAL
            )
            onApprovalRequired()
            when (decision.await(CONSENT_WAIT_MS)) {
                DebugBluetoothConsentDecision.Decision.APPROVE -> {
                    DebugBluetoothConsentFrame.write(
                        transport.output, isHost,
                        DebugBluetoothConsentFrame.Type.APPROVE, commitment
                    )
                    require(gate.localWritten() == DebugApprovalGate.State.LOCAL_ONLY)
                }
                DebugBluetoothConsentDecision.Decision.DECLINE -> {
                    // Sending only a session-bound encrypted DECLINE, then
                    // immediately terminating; no CONFIRM can follow.
                    DebugBluetoothConsentFrame.write(
                        transport.output, isHost,
                        DebugBluetoothConsentFrame.Type.DECLINE, commitment
                    )
                    gate.decline()
                    throw DebugBluetoothConsentDenied()
                }
                DebugBluetoothConsentDecision.Decision.CANCELED,
                DebugBluetoothConsentDecision.Decision.TIMEOUT ->
                    throw DebugBluetoothConsentDenied()
            }

            val peerApproval = DebugBluetoothConsentFrame.read(
                transport.input, expectPeerHost = !isHost, commitment
            )
            if (peerApproval == DebugBluetoothConsentFrame.Type.DECLINE) {
                gate.decline()
                throw DebugBluetoothConsentDenied()
            }
            if (peerApproval != DebugBluetoothConsentFrame.Type.APPROVE ||
                gate.peerObserved() != DebugApprovalGate.State.BOTH_OBSERVED ||
                !gate.mayWriteConfirmation()
            ) throw DebugBluetoothConsentDenied()

            transport.advance(
                DebugTlsControlTransport.Phase.APPROVAL,
                DebugTlsControlTransport.Phase.CONFIRM
            )
            DebugBluetoothConsentFrame.write(
                transport.output, isHost,
                DebugBluetoothConsentFrame.Type.CONFIRM, commitment
            )
            if (gate.localConfirmationWritten() !=
                DebugApprovalGate.State.LOCAL_CONFIRMED
            ) throw DebugBluetoothConsentDenied()

            // The peer cannot confirm before its APPROVE on this ordered TLS
            // stream. A mismatched/duplicate/early CONFIRM fails closed.
            val peerConfirmation = DebugBluetoothConsentFrame.read(
                transport.input, expectPeerHost = !isHost, commitment
            )
            if (peerConfirmation != DebugBluetoothConsentFrame.Type.CONFIRM ||
                gate.peerConfirmationObserved() != DebugApprovalGate.State.CONFIRMED
            ) throw DebugBluetoothConsentDenied()

            // Proven for this one debug connection only; controller must close
            // the socket after returning. NEVER authorize user messages here.
        } finally {
            gate.close()
            decision.close()
        }
    }
}

internal class DebugBluetoothConsentDenied :
    IOException("Bluetooth session consent denied, missing or interrupted")
