package com.edenpractical.comms.security

import android.net.ssl.SSLSockets
import android.os.Build
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket

/**
 * An Android-only, DEBUG-TEST channel-binding interoperability experiment.
 *
 * Must be invoked ONLY after mutual TLS 1.3 peer pinning, reciprocal QR proof,
 * and Wi-Fi continuity checks on this exact socket. A matching RFC 9266
 * channel-binding value does not prove independent human consent or unlock
 * arbitrary text. Never log the value or use it as encryption key material.
 */
internal object DebugTlsChannelAgreement {
    private const val RFC9266_LABEL = "EXPORTER-Channel-Binding"
    private val EMPTY_CONTEXT = ByteArray(0)

    fun verify(
        socket: SSLSocket,
        isPresenter: Boolean,
        binding: SessionProofBinding,
        challenges: SessionProofChallenges
    ): ByteArray {
        // The standard Android API first exists at level 31, and unsupported
        // providers MUST fail closed rather than use QR-only substitute proof.
        if (Build.VERSION.SDK_INT < 31) throw DebugExporterUnavailableException()
        try {
            val status = TlsExporterReadiness.preflight(
                sdkInt = Build.VERSION.SDK_INT,
                tlsProtocol = socket.session.protocol,
                isConnected = socket.isConnected,
                isClosed = socket.isClosed,
                providerSupported = SSLSockets.isSupportedSocket(socket)
            )
            if (status != null) throw DebugExporterUnavailableException()

            val value = SSLSockets.exportKeyingMaterial(
                socket,
                RFC9266_LABEL,
                EMPTY_CONTEXT,
                DebugExporterBindingFrame.EXPORTER_BYTES
            ) ?: throw DebugExporterUnavailableException()

            try {
                DebugExporterBindingFrame.exchange(
                    socket.inputStream, socket.outputStream, isPresenter, value
                )
                // Share only the fixed digest with the caller; the 32-byte
                // exporter itself is cleared and must never enter the UI.
                return DebugApprovalCommitment.derive(binding, challenges, value)
            } finally {
                value.fill(0)
            }
        } catch (e: DebugExporterMismatchException) {
            throw e
        } catch (e: DebugExporterUnavailableException) {
            throw e
        } catch (e: SSLException) {
            throw DebugExporterUnavailableException()
        } catch (e: RuntimeException) {
            // Never disclose raw provider exception text, QR data or addresses.
            throw DebugExporterUnavailableException()
        }
    }
}
