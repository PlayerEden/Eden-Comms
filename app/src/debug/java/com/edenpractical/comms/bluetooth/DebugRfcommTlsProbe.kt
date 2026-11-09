package com.edenpractical.comms.bluetooth

import android.bluetooth.BluetoothSocket
import android.net.ssl.SSLEngines
import android.os.Build
import android.os.SystemClock
import com.edenpractical.comms.security.DebugApprovalCommitment
import com.edenpractical.comms.security.DebugExporterBindingFrame
import com.edenpractical.comms.security.PinnedTlsPeer
import com.edenpractical.comms.security.SessionProofBinding
import com.edenpractical.comms.security.SessionProofChallenges
import com.edenpractical.comms.security.SessionProofExchange
import com.edenpractical.comms.security.Tls13PeerContext
import javax.net.ssl.SSLEngine
import java.io.InputStream
import java.io.OutputStream
import javax.net.ssl.SSLException

/**
 * M3n-B2 DEBUG-only mutual TLS 1.3 + fixed encrypted control agreement.
 *
 * Order on this ONE RFCOMM socket:
 *  1. TLS 1.3 with both exact in-person X509 pins and Keystore key possession.
 *  2. Four fresh host/join QR nonce/challenge records inside verified TLS.
 *  3. Both endpoints exchange and compare the same TLS exporter over TLS.
 *  4. Both endpoints exchange and compare the resulting domain-separated
 *     QR+identity+challenge+exporter commitment over TLS.
 *
 * M3n-C1 after transcript agreement: both people must independently tap
 * APPROVE (or DECLINE), then two exact-session TLS-encrypted final CONFIRM
 * frames must be exchanged. F2-Q: only AFTER these exact authenticated
 * checks, the same worker may run a bounded DEBUG encrypted TEXT/ACK channel
 * on the original engine and original socket streams. Diagnostic-only runs
 * still close as before; no release chat, saved trust or voice authority.
 */
internal object DebugRfcommTlsProbe {
    private const val EXPORTER_LABEL = "EXPORTER-Channel-Binding"
    private const val EXPORTER_LENGTH = 32

    @Suppress("MissingPermission")
    fun run(
        socket: BluetoothSocket,
        request: DebugBluetoothTlsProbeInput,
        onVerifiedStage: (DebugBluetoothVerifiedStage) -> Unit = {},
        onApprovedChannel: ((SSLEngine, InputStream, OutputStream) -> Unit)? = null,
        onConsentRequired: (DebugBluetoothConsentDecision) -> Unit
    ): DebugBluetoothProbeCompletion {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            throw SSLException("TLS engine exporter requires Android 12 or newer")
        }
        val expectedPin = request.pinCopy()
        var binding: SessionProofBinding? = null
        var challenges: SessionProofChallenges? = null
        var engine: SSLEngine? = null
        var transport: DebugTlsControlTransport? = null
        try {
            val context = Tls13PeerContext(
                request.localIdentity.keyManager(), expectedPin
            )
            engine = context.unconnectedEngine(client = !request.hostRole)
            if (!SSLEngines.isSupportedEngine(engine)) {
                throw SSLException("TLS provider does not support channel exporter")
            }

            // The only RFCOMM byte streams are used by our TLS record driver.
            val wireIn = socket.inputStream
            val wireOut = socket.outputStream
            DebugTlsEngineHandshake.negotiate(engine, wireIn, wireOut)
            PinnedTlsPeer.requirePinnedTls13(engine.session, expectedPin)
            // F2-C: unsupported provider close/post-handshake state stops this
            // diagnostic before any encrypted QR control is exchanged.
            DebugTlsProviderReadiness.requireControlReady(engine)
            onVerifiedStage(DebugBluetoothVerifiedStage.TLS_PIN)

            // The original QR is rechecked after blocking TLS negotiation.
            // The selected Bluetooth radio identifier is never used as trust.
            binding = request.freshBinding()
            transport = DebugTlsControlTransport(engine, wireIn, wireOut)
            challenges = if (request.hostRole) {
                SessionProofExchange.runHost(
                    transport.input, transport.output, binding
                )
            } else {
                SessionProofExchange.runJoin(
                    transport.input, transport.output, binding
                )
            }
            transport.advance(
                DebugTlsControlTransport.Phase.QR_PROOF,
                DebugTlsControlTransport.Phase.EXPORTER
            )
            onVerifiedStage(DebugBluetoothVerifiedStage.QR_CHALLENGES)

            // Exchanging the exporter through an already mutually pinned
            // encrypted TLS channel proves SAME-CHANNEL exporter agreement;
            // local availability alone was insufficient in M3n-B1.
            val exported = SSLEngines.exportKeyingMaterial(
                engine, EXPORTER_LABEL, ByteArray(0), EXPORTER_LENGTH
            ) ?: throw SSLException("No TLS channel exporter")
            try {
                if (exported.size != EXPORTER_LENGTH ||
                    exported.all { it == 0.toByte() }
                ) throw SSLException("Invalid TLS exporter")
                DebugExporterBindingFrame.exchange(
                    transport.input, transport.output, request.hostRole, exported
                )
                transport.advance(
                    DebugTlsControlTransport.Phase.EXPORTER,
                    DebugTlsControlTransport.Phase.COMMITMENT
                )
                onVerifiedStage(DebugBluetoothVerifiedStage.EXPORTER)

                // Domain-separated transcript binds BOTH QR pins/nonces,
                // fresh peer challenges and this specific TLS exporter.
                // It is NOT a human approval or an encryption key.
                val commitment = DebugApprovalCommitment.derive(
                    binding, challenges, exported
                )
                try {
                    DebugBluetoothCommitmentFrame.exchange(
                        transport.input, transport.output,
                        request.hostRole, commitment
                    )
                    onVerifiedStage(DebugBluetoothVerifiedStage.COMMITMENT)

                    // Do not solicit human approval before exact TLS pins,
                    // QR proof, both exporters and commitments match.
                    val consent = DebugBluetoothConsentDecision()
                    try {
                        DebugBluetoothConsentExchange.run(
                            transport, request.hostRole, commitment, consent,
                            onApprovalRequired = { onConsentRequired(consent) }
                        )
                    } finally {
                        consent.close()
                    }
                } finally {
                    commitment.fill(0)
                }
            } finally {
                exported.fill(0)
            }

            // Fail if QR expired during the encrypted control and consent exchange.
            val atEnd = request.freshBinding()
            clearBinding(atEnd)
            onVerifiedStage(DebugBluetoothVerifiedStage.HUMAN_CONFIRMATIONS)
            // F2-Q: the *real* verified TLS engine and the original RFCOMM
            // byte streams stay on this SAME worker call stack. Neither a
            // copied transcript nor this non-authorizing receipt opens chat.
            // Control framing is retired before encrypted application data.
            if (onApprovedChannel != null) {
                transport.clear()
                val approvedEngine = engine
                    ?: throw SSLException("Missing actual approved TLS engine")
                onApprovedChannel(approvedEngine, wireIn, wireOut)
            }
            // Diagnostic receipt remains read-only, and never has a socket,
            // engine, message API or independent send authority.
            val nowSeconds = System.currentTimeMillis() / 1000
            return DebugBluetoothProbeCompletion.afterVerifiedDiagnostic(
                hostRole = request.hostRole,
                issuedAtSeconds = nowSeconds,
                qrExpiresAtSeconds = request.expiresAtSeconds,
                issuedAtElapsedMs = SystemClock.elapsedRealtime()
            )
        } finally {
            transport?.clear()
            challenges?.clear()
            binding?.let(::clearBinding)
            expectedPin.fill(0)
            try { engine?.closeOutbound() } catch (_: Exception) { }
            // Controller closes the RFCOMM socket on any outcome.
        }
    }

    private fun clearBinding(binding: SessionProofBinding) {
        binding.hostPin.fill(0)
        binding.joinPin.fill(0)
        binding.hostNonce.fill(0)
        binding.joinNonce.fill(0)
    }
}
