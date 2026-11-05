package com.edenpractical.comms.security

import android.net.ssl.SSLSockets
import android.os.Build
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket

/**
 * Diagnostic ONLY: tests whether the Android TLS provider can produce the
 * standard RFC 9266 tls-exporter channel-binding value on the ALREADY
 * mutually pinned, live TLS 1.3 socket.
 *
 * - Used only from the debug-gated, fixed-phrase LanDemoSession.
 * - Never returns, logs, stores or transmits the exported bytes.
 * - Does not compare both phones, prove consent or authorize any messages.
 * - No fallback to QR-only hashes, TLS 1.2, made-up key material or extra SDKs.
 *
 * This is NOT the future reviewed, authenticated production consent adapter.
 */
internal object TlsExporterReadiness {
    private const val MIN_EXPORTER_API = 31
    private const val REQUIRED_PROTOCOL = "TLSv1.3"

    // RFC 9266 §2: 32-byte output, EXACT label, zero-length context.
    private const val RFC9266_LABEL = "EXPORTER-Channel-Binding"
    private const val OUTPUT_LENGTH = 32
    private val EMPTY_CONTEXT = ByteArray(0) // Do not replace with null!

    enum class Status(val safeDescription: String) {
        API_TOO_OLD("Unavailable on this Android version (requires Android 12+)"),
        TLS_NOT_READY("TLS 1.3 was not active"),
        SOCKET_CLOSED("Connection closed before channel-binding check"),
        PROVIDER_UNSUPPORTED("This TLS provider does not support Android's exporter"),
        EXPORT_UNAVAILABLE("The live connection could not export a valid binding"),
        AVAILABLE("TLS exporter available on this phone (not a secure-chat approval)")
    }

    /** Pure capability preflight; unit-tested without Android framework runtime. */
    fun preflight(
        sdkInt: Int,
        tlsProtocol: String,
        isConnected: Boolean,
        isClosed: Boolean,
        providerSupported: Boolean
    ): Status? = when {
        sdkInt < MIN_EXPORTER_API -> Status.API_TOO_OLD
        tlsProtocol != REQUIRED_PROTOCOL -> Status.TLS_NOT_READY
        !isConnected || isClosed -> Status.SOCKET_CLOSED
        !providerSupported -> Status.PROVIDER_UNSUPPORTED
        else -> null
    }

    /**
     * Caller must already have called startHandshake(), validated exact pins,
     * completed the QR session proof, and checked socket ownership/cancellation.
     * The result is informative only and never gates or enables data traffic.
     */
    fun probeAfterVerifiedDemoProof(socket: SSLSocket): Status {
        if (Build.VERSION.SDK_INT < MIN_EXPORTER_API) return Status.API_TOO_OLD
        // Requires Android API 31; avoid calls on older devices.
        return try {
            val status = preflight(
                sdkInt = Build.VERSION.SDK_INT,
                tlsProtocol = socket.session.protocol,
                isConnected = socket.isConnected,
                isClosed = socket.isClosed,
                providerSupported = SSLSockets.isSupportedSocket(socket)
            )
            if (status != null) return status

            // RFC 9266 uses an *empty* context byte sequence, not null, since
            // Android distinguishes absent and empty exporter contexts.
            val value = SSLSockets.exportKeyingMaterial(
                socket, RFC9266_LABEL, EMPTY_CONTEXT, OUTPUT_LENGTH
            ) ?: return Status.EXPORT_UNAVAILABLE
            try {
                if (value.size == OUTPUT_LENGTH && value.any { it != 0.toByte() })
                    Status.AVAILABLE
                else Status.EXPORT_UNAVAILABLE
            } finally {
                // Best-effort memory cleanup; no value is retained or displayed.
                value.fill(0)
            }
        } catch (_: SSLException) {
            Status.EXPORT_UNAVAILABLE
        } catch (_: RuntimeException) {
            // Unexpected provider/socket implementation: fail the DIAGNOSTIC
            // closed without leaking a raw exception or enabling consent.
            Status.EXPORT_UNAVAILABLE
        }
    }
}
