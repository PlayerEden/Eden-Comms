package com.edenpractical.comms.bluetooth

import android.bluetooth.BluetoothSocket
import android.net.ssl.SSLEngines
import android.os.Build
import com.edenpractical.comms.security.PinnedTlsPeer
import com.edenpractical.comms.security.Tls13PeerContext
import java.io.IOException
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLException

/**
 * DEBUG-ONLY mutual TLS 1.3 handshake probe on the already opened RFCOMM
 * socket, BEFORE any user data. This checks the public pin from the in-person
 * QR against the peer's actual TLS certificate and private-key proof.
 *
 * It does NOT exchange the host/join QR nonces through TLS, compare both
 * sides' TLS exporter value, verify bilateral approvals, or authorize text.
 * No plaintext fallback. The owning link controller closes this socket
 * immediately after the probe (success or failure).
 */
internal object DebugRfcommTlsProbe {
    private const val EXPORTER_LABEL = "EXPORTER-Channel-Binding"
    private const val EXPORTER_LENGTH = 32

    @Suppress("MissingPermission")
    fun run(socket: BluetoothSocket, request: DebugBluetoothTlsProbeInput) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            throw SSLException("TLS engine exporter requires Android 12 or newer")
        }
        val expectedPin = request.pinCopy()
        var engine: SSLEngine? = null
        try {
            val context = Tls13PeerContext(
                request.localIdentity.keyManager(), expectedPin
            )
            engine = context.unconnectedEngine(client = !request.hostRole)
            if (!SSLEngines.isSupportedEngine(engine)) {
                throw SSLException("TLS provider has no supported exporter; refuse")
            }

            // ONLY TLS handshake frames flow over the raw Bluetooth byte stream.
            // This class never exposes encrypted app data read/write operations.
            DebugTlsEngineHandshake.negotiate(
                engine, socket.inputStream, socket.outputStream
            )
            PinnedTlsPeer.requirePinnedTls13(engine.session, expectedPin)

            // The exporter is just a local capability diagnostic. Its
            // agreement with the REMOTE peer and fresh QR nonces is NOT
            // checked in M3n-B1. It must never be logged, shown or persisted.
            val exported = SSLEngines.exportKeyingMaterial(
                engine, EXPORTER_LABEL, ByteArray(0), EXPORTER_LENGTH
            ) ?: throw SSLException("No available TLS channel exporter")
            try {
                if (exported.size != EXPORTER_LENGTH ||
                    exported.all { it == 0.toByte() }
                ) throw SSLException("Invalid TLS channel exporter")
            } finally {
                exported.fill(0)
            }
        } finally {
            expectedPin.fill(0)
            try { engine?.closeOutbound() } catch (_: Exception) { }
            // The controller closes the RFCOMM socket immediately afterwards.
            // No application-data flow is ever enabled here.
        }
    }
}
