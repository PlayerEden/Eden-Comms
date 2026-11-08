package com.edenpractical.comms.bluetooth

import android.bluetooth.BluetoothSocket
import android.net.ssl.SSLEngines
import android.os.Build
import com.edenpractical.comms.security.DebugApprovalCommitment
import com.edenpractical.comms.security.DebugExporterBindingFrame
import com.edenpractical.comms.security.PinnedTlsPeer
import com.edenpractical.comms.security.SessionProofBinding
import com.edenpractical.comms.security.SessionProofChallenges
import com.edenpractical.comms.security.SessionProofExchange
import com.edenpractical.comms.security.Tls13PeerContext
import javax.net.ssl.SSLEngine
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
 * frames must be exchanged. Even after success the controller closes the
 * socket: NO chat, voice, contact trust or production authorization.
 */
internal object DebugRfcommTlsProbe {
    private const val EXPORTER_LABEL = "EXPORTER-Channel-Binding"
    private const val EXPORTER_LENGTH = 32

    @Suppress("MissingPermission")
    fun run(
        socket: BluetoothSocket,
        request: DebugBluetoothTlsProbeInput,
        onConsentRequired: (DebugBluetoothConsentDecision) -> Unit
    ) {
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
