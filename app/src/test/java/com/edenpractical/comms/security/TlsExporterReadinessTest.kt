package com.edenpractical.comms.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TlsExporterReadinessTest {
    private fun check(
        sdk: Int = 35,
        protocol: String = "TLSv1.3",
        connected: Boolean = true,
        closed: Boolean = false,
        platformSocket: Boolean = true
    ): TlsExporterReadiness.Status? = TlsExporterReadiness.preflight(
        sdkInt = sdk,
        tlsProtocol = protocol,
        isConnected = connected,
        isClosed = closed,
        providerSupported = platformSocket
    )

    @Test fun androidBelow31CannotUseExporter() {
        listOf(26, 28, 29, 30).forEach {
            assertEquals(TlsExporterReadiness.Status.API_TOO_OLD, check(sdk = it))
        }
    }

    @Test fun api31OrAboveRequiresLiveTls13AndSupportedSocket() {
        assertNull(check(sdk = 31))
        assertNull(check(sdk = 36))
        assertEquals(TlsExporterReadiness.Status.TLS_NOT_READY, check(protocol = "TLSv1.2"))
        assertEquals(TlsExporterReadiness.Status.TLS_NOT_READY, check(protocol = "NONE"))
        assertEquals(TlsExporterReadiness.Status.SOCKET_CLOSED, check(connected = false))
        assertEquals(TlsExporterReadiness.Status.SOCKET_CLOSED, check(closed = true))
        assertEquals(
            TlsExporterReadiness.Status.PROVIDER_UNSUPPORTED,
            check(platformSocket = false)
        )
    }

    @Test fun diagnosticResultDoesNotPretendToProveUserConsent() {
        val message = TlsExporterReadiness.Status.AVAILABLE.safeDescription
        assertTrue(message.contains("not a secure-chat approval"))
        assertFalse(message.contains("approved to send messages"))
    }

    @Test fun unsupportedVersionsMustNotFallBackToQrOnlyHash() {
        for (sdk in 26..30) {
            assertEquals(TlsExporterReadiness.Status.API_TOO_OLD, check(sdk = sdk))
        }
    }
}
